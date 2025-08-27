package com.euroclear;

import com.euroclear.util.ApiConfig;
import com.euroclear.util.Parsing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.euroclear.util.ApiConfig.*;
import static com.euroclear.util.Authentication.*;
import static com.euroclear.util.Calculation.eachBusinessDay;
import static com.euroclear.util.Calculation.processingDuration;
import static com.euroclear.util.CsvWriters.getMonthlyWriter;
import static com.euroclear.util.ISIN.ISINS;
import static com.euroclear.util.LiquidityRecord.*;
import static com.euroclear.util.Parsing.formatJsonValue;
import static com.euroclear.util.Parsing.selectFirstNonEmpty;

/**
 * Euroclear Liquidity Drive API Client
 */
public class LiquidityDriveClient {

    private static final Logger logger = Logger.getLogger(LiquidityDriveClient.class);

    private static IConfidentialClientApplication app;
    private static Set<String> scopes;
    private static final AtomicReference<IAuthenticationResult> cachedAuth = new AtomicReference<>();

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        logger.infof("Starting Euroclear Liquidity Drive Client");

        // Load external env variables to set the sensitive information
        loadEnvironmentVariables();

        // Create the output folder
        Path outDir = Paths.get(System.getProperty("user.dir"), "out");
        Files.createDirectories(outDir);

        // Record the start time to calculate the duration
        Instant startTime = Instant.now();

        // Use constants from utility class
        String[] expandBaseCandidates = EXPAND_BASE_CANDIDATES;
        String[] expandFields = EXPAND_FIELDS;
        String[] isins = ISINS;
        LocalDate start = ApiConfig.START_DATE;
        LocalDate end = ApiConfig.END_DATE;

        List<LocalDate> dates = eachBusinessDay(start, end, null).collect(Collectors.toList());

        // Group work by date for better parallelization
        Map<LocalDate, List<String>> workByDate = dates.stream()
            .collect(Collectors.toMap(
                date -> date,
                date -> Arrays.asList(isins)
            ));

        // Initialize headers before parallel processing
        populateHeaders();

        // Authenticate with the Microsoft server and acquire a token
        createConfidentialClientApplication();

        // Set up the HTTP client using the client and root ca certificates
        try (CloseableHttpClient httpClient = createHttpClient()) {

            Map<String, AsyncCSVWriter> writers = new ConcurrentHashMap<>();

            // Process work items in parallel
            int cores = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(cores * 4);

            try {
                // Flatten work items for direct parallel processing
                List<WorkItem> allWork = workByDate.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                        .map(isin -> new WorkItem(isin, entry.getKey())))
                    .collect(Collectors.toList());

                List<CompletableFuture<Void>> futures = allWork.stream()
                    .map(workItem -> CompletableFuture.runAsync(() -> {
                        try {
                            processAsync(workItem, httpClient, () -> {
                                    try {
                                        return getAccessTokenAsync(false).get();
                                    } catch (InterruptedException | ExecutionException e) {
                                        // Best practice: re-wrap checked exceptions as an unchecked exception
                                        // InterruptedException is a signal to stop processing.
                                        if (e instanceof InterruptedException) {
                                            Thread.currentThread().interrupt();
                                        }
                                        throw new RuntimeException("Failed to acquire token", e);
                                    }
                                }, FIXED_PATHS, expandBaseCandidates,
                                expandFields, writers, headerLine(), outDir);
                        } catch (Exception e) {
                            logger.errorf("Error processing %s %s: %s",
                                workItem.isin(), workItem.date(), e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }, executor))
                    .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            } finally {
                executor.shutdown();

                // Close all writers
                writers.values().forEach(writer -> {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        logger.error("Error closing writer", e);
                    }
                });

                logger.infof("Monthly CSVs written to: %s", outDir);
                processingDuration(startTime);
            }
        }
    }

    private static void processAsyncWithQueue(WorkItem workItem, CloseableHttpClient httpClient, String apiToken,
                                             String[] fixedPaths, String[] expandBaseCandidates,
                                             String[] expandFields, Map<String, AsyncCSVWriter> writers,
                                             String headerLine, Path outDir, BlockingQueue<Runnable> writeQueue) throws Exception {

        String nextUrl = String.format(SINGLE_ENDPOINT_FMT,
            URLEncoder.encode(workItem.isin(), StandardCharsets.UTF_8),
            workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        StringBuilder localBuffer = new StringBuilder(64 * 1024);

        while (nextUrl != null && !nextUrl.isEmpty()) {
            String requestUrl = nextUrl.startsWith("http") ? nextUrl : LIQUIDITY_DRIVE_ADDRESS + nextUrl;

            HttpGet request = new HttpGet(requestUrl);
            request.setHeader("Authorization", "Bearer " + apiToken);
            request.setHeader("Ocp-Apim-Subscription-Key", API_KEY);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String bodyText = "";

                if (response.getEntity() != null) {
                    bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (bodyText == null || bodyText.trim().isEmpty()) {
                        logger.infof("SKIP (no JSON): %s %s HTTP %s", workItem.isin(), workItem.date(), response.getCode());
                        break;
                    }
                } else {
                    logger.infof("SKIP (ISIN) as no content found: %s %s HTTP %s", workItem.isin(), workItem.date(), response.getCode());
                    break;
                }

                logger.debugf("HTTP %s Body for %s %s:\n%s", response.getCode(), workItem.isin(), workItem.date(), bodyText);

                if (response.getCode() != HttpStatus.SC_OK) {
                    if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
                        logger.infof("SKIP (Content not found for ISIN): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_UNAUTHORIZED &&
                        bodyText.toLowerCase().contains("invalid Euroclear Liquidity subscription")) {
                        logger.infof("SKIP (not entitled): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_NOT_FOUND ||
                        (response.getCode() == HttpStatus.SC_BAD_REQUEST &&
                            bodyText.toLowerCase().contains("not found"))) {
                        logger.infof("SKIP (ISIN not found): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    throw new RuntimeException("GET failed: " + response.getCode() + " " + bodyText);
                }

                JsonNode json = objectMapper.readTree(bodyText);

                List<JsonNode> pageObjects = new ArrayList<>();
                if (json.isObject()) {
                    pageObjects.add(json);
                } else if (json.isArray()) {
                    json.forEach(pageObjects::add);
                }

                for (JsonNode obj : pageObjects) {
                    List<String> fixedCells = new ArrayList<>();
                    fixedCells.add(workItem.isin());
                    fixedCells.add(workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

                    for (String path : fixedPaths) {
                        fixedCells.add(formatJsonValue(obj.at("/" + path.replace(".", "/").replace("['", "/").replace("']", ""))));
                    }

                    // Handle transaction details
                    JsonNode txItems = selectFirstNonEmpty(obj, expandBaseCandidates);
                    if (txItems == null || !txItems.isArray() || txItems.size() == 0) {
                        List<String> row = new ArrayList<>(fixedCells);
                        for (int i = 0; i < expandFields.length; i++) {
                            row.add("");
                        }
                        localBuffer.append(row.stream().map(Parsing::escapeCSV)
                            .collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                    } else {
                        for (JsonNode item : txItems) {
                            List<String> row = new ArrayList<>(fixedCells);
                            for (String field : expandFields) {
                                row.add(formatJsonValue(item.get(field)));
                            }
                            localBuffer.append(row.stream().map(Parsing::escapeCSV)
                                .collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                        }
                    }
                }

                // Look for next page link
                JsonNode nextLink = json.at("/links").findValue("href");
                nextUrl = (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
            }
        }

        // Instead of synchronized writer access, use the queue
        if (localBuffer.length() > 0) {
            String bufferContent = localBuffer.toString();
            writeQueue.add(() -> {
                try {
                    AsyncCSVWriter writer = getMonthlyWriter(workItem.date(), writers, headerLine, outDir);
                    writer.write(bufferContent);
                    logger.infof("Processed ISIN %s on %s", workItem.isin(), workItem.date());
                } catch (IOException e) {
                    logger.errorf("Error writing to file for %s %s", workItem.isin(), workItem.date(), e);
                }
            });
        }
    }

    private static void processAsync(WorkItem workItem, CloseableHttpClient httpClient, Supplier<String> apiToken,
                                     String[] fixedPaths, String[] expandBaseCandidates,
                                     String[] expandFields, Map<String, AsyncCSVWriter> writers,
                                     String headerLine, Path outDir) throws Exception {

        String nextUrl = String.format(SINGLE_ENDPOINT_FMT,
            URLEncoder.encode(workItem.isin(), StandardCharsets.UTF_8),
            workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        StringBuilder localBuffer = new StringBuilder(64 * 1024);

        while (nextUrl != null && !nextUrl.isEmpty()) {
            String requestUrl = nextUrl.startsWith("http") ? nextUrl : LIQUIDITY_DRIVE_ADDRESS + nextUrl;

            HttpGet request = new HttpGet(requestUrl);
            // getAccessTokenAsync(false).get()
            request.setHeader("Authorization", "Bearer " + apiToken.get());
            request.setHeader("Ocp-Apim-Subscription-Key", API_KEY);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String bodyText = "";

                if (response.getEntity() != null) {
                    bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (bodyText == null || bodyText.trim().isEmpty()) {
                        logger.infof("SKIP (no JSON): %s %s HTTP %s", workItem.isin(), workItem.date(), response.getCode());
                        break;
                    }
                } else {
                    logger.infof("SKIP (ISIN) as no content found: %s %s HTTP %s", workItem.isin(), workItem.date(), response.getCode());
                    break;
                }

                logger.debugf("HTTP %s Body for %s %s:\n%s", response.getCode(), workItem.isin(), workItem.date(), bodyText);

                if (response.getCode() != HttpStatus.SC_OK) {
                    if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
                        logger.infof("SKIP (Content not found for ISIN): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_UNAUTHORIZED &&
                        bodyText.toLowerCase().contains("invalid Euroclear Liquidity subscription")) {
                        logger.infof("SKIP (not entitled): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_NOT_FOUND ||
                        (response.getCode() == HttpStatus.SC_BAD_REQUEST &&
                            bodyText.toLowerCase().contains("not found"))) {
                        logger.infof("SKIP (ISIN not found): %s %s", workItem.isin(), workItem.date());
                        break;
                    }

                    throw new RuntimeException("GET failed: " + response.getCode() + " " + bodyText);
                }

                JsonNode json = objectMapper.readTree(bodyText);

                List<JsonNode> pageObjects = new ArrayList<>();
                if (json.isObject()) {
                    pageObjects.add(json);
                } else if (json.isArray()) {
                    json.forEach(pageObjects::add);
                }

                for (JsonNode obj : pageObjects) {
                    List<String> fixedCells = new ArrayList<>();
                    fixedCells.add(workItem.isin());
                    fixedCells.add(workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

                    for (String path : fixedPaths) {
                        fixedCells.add(formatJsonValue(obj.at("/" + path.replace(".", "/").replace("['", "/").replace("']", ""))));
                    }

                    // Handle transaction details
                    JsonNode txItems = selectFirstNonEmpty(obj, expandBaseCandidates);
                    if (txItems == null || !txItems.isArray() || txItems.size() == 0) {
                        List<String> row = new ArrayList<>(fixedCells);
                        for (int i = 0; i < expandFields.length; i++) {
                            row.add("");
                        }
                        localBuffer.append(row.stream().map(Parsing::escapeCSV)
                            .collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                    } else {
                        for (JsonNode item : txItems) {
                            List<String> row = new ArrayList<>(fixedCells);
                            for (String field : expandFields) {
                                row.add(formatJsonValue(item.get(field)));
                            }
                            localBuffer.append(row.stream().map(Parsing::escapeCSV)
                                .collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                        }
                    }
                }

                // Look for next page link
                JsonNode nextLink = json.at("/links").findValue("href");
                nextUrl = (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
            }
        }

        if (localBuffer.length() > 0) {
            AsyncCSVWriter writer = getMonthlyWriter(workItem.date(), writers, headerLine, outDir);
            writer.write(localBuffer.toString());
            logger.infof("Processed ISIN %s on %s", workItem.isin(), workItem.date());
        }
    }
}