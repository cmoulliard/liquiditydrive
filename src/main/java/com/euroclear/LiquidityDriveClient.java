package com.euroclear;

import com.euroclear.util.ApiConfig;
import com.euroclear.util.Parsing;
import com.euroclear.util.WorkItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.aad.msal4j.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.euroclear.util.ApiConfig.*;
import static com.euroclear.util.ApiConfig.DELIM;
import static com.euroclear.util.ISIN.ISINS;
import static com.euroclear.util.LiquidityRecord.*;
import static com.euroclear.util.Parsing.*;

/**
 * Euroclear Liquidity Drive API Client
 */
public class LiquidityDriveClient {

    private static final Logger logger = LoggerFactory.getLogger(LiquidityDriveClient.class);

    private static IConfidentialClientApplication app;
    private static Set<String> scopes;
    private static final AtomicReference<IAuthenticationResult> cachedAuth = new AtomicReference<>();
    private static final Semaphore tokenGate = new Semaphore(1);

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        logger.info("Starting Euroclear Liquidity Drive Client");

        // Load external env variables to set the sensitive information
        loadEnvironmentVariables();

        // Create the output folder
        Path outDir = Paths.get(System.getProperty("user.dir"), "out");
        Files.createDirectories(outDir);

        // Record the start time to calculate the duration
        Instant startTime = Instant.now();

        // Use constants from utility class
        String[] expandBaseCandidates = ApiConfig.EXPAND_BASE_CANDIDATES;
        String[] expandFields = ApiConfig.EXPAND_FIELDS;
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
        setupAuthentication();

        // Call the API to get the bearer token
        //String apiToken = getAccessTokenAsync(false).get();
        //logger.debug("API Token: " + apiToken);

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
                                }, fixedPaths, expandBaseCandidates,
                                expandFields, writers, headerLine(), outDir);
                        } catch (Exception e) {
                            logger.error("Error processing {} {}: {}",
                                workItem.isin, workItem.date, e.getMessage());
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

                logger.info("Monthly CSVs written to: {}", outDir);
                processingDuration(startTime);
            }
        }
    }

    private static void processingDuration(Instant startTime) {
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        String formattedTime = String.format("%02d:%02d", minutes, seconds);
        logger.info("Process took " + formattedTime);
    }

    private static void setupAuthentication() throws Exception {
        // Load PKCS12 certificate - equivalent to C# X509CertificateLoader.LoadPkcs12FromFile
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(CERTIFICATE_FILE_NAME)) {
            keyStore.load(fis, CERTIFICATE_PASSWORD.toCharArray());
        }

        // Get the private key and certificate from the keystore
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, CERTIFICATE_PASSWORD.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

        // Create MSAL client with certificate - equivalent to C# .WithCertificate(certificate)
        app = ConfidentialClientApplication.builder(CLIENT_ID,
                ClientCredentialFactory.createFromCertificate(privateKey, certificate))
            .authority(AUTHORITY)
            .build();

        scopes = Collections.singleton(APPLICATION_ID + "/.default");
    }

    private static CloseableHttpClient createHttpClient() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();

        // 1. Load the client's keystore for key material (your private key)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream keyStoreStream = new FileInputStream("/Users/cmoullia/code/vscode/euroclear/conf/Euroclear_ExportPriv.pfx")) {
            keyStore.load(keyStoreStream, CERTIFICATE_PASSWORD.toCharArray());
        }

        // 2. Load the new trust store containing the server's CA certificate
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream trustStoreStream = new FileInputStream("/Users/cmoullia/code/vscode/euroclear/conf/euroclear-truststore.jks")) {
            trustStore.load(trustStoreStream, "euroclear".toCharArray());
        }

        // 3. Build the SSLContext using both keystores
        SSLContext sslContext = SSLContexts.custom()
            .loadKeyMaterial(keyStore, CERTIFICATE_PASSWORD.toCharArray())
            .loadTrustMaterial(trustStore, null) // Use the new trust store
            .build();

        TlsSocketStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext);
        HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(cores * 8) // Scale with thread pool
            .setMaxConnPerRoute(cores * 4) // Match thread pool size
            .setTlsSocketStrategy(tlsStrategy)
            .setDefaultTlsConfig(TlsConfig.custom()
                .setHandshakeTimeout(Timeout.ofMinutes(30))
                .setSupportedProtocols(TLS.V_1_3, TLS.V_1_2)
                .build())
            .build();

        return HttpClients.custom()
            .setConnectionManager(cm)
            .build();
    }


    private static void processAsyncWithQueue(WorkItem workItem, CloseableHttpClient httpClient, String apiToken,
                                             String[] fixedPaths, String[] expandBaseCandidates,
                                             String[] expandFields, Map<String, AsyncCSVWriter> writers,
                                             String headerLine, Path outDir, BlockingQueue<Runnable> writeQueue) throws Exception {

        String nextUrl = String.format(SINGLE_ENDPOINT_FMT,
            URLEncoder.encode(workItem.isin, StandardCharsets.UTF_8),
            workItem.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

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
                        logger.info("SKIP (no JSON): {} {} HTTP {}", workItem.isin, workItem.date, response.getCode());
                        break;
                    }
                } else {
                    logger.info("SKIP (ISIN) as no content found: {} {} HTTP {}", workItem.isin, workItem.date, response.getCode());
                    break;
                }

                logger.debug("HTTP {} Body for {} {}:\n{}", response.getCode(), workItem.isin, workItem.date, bodyText);

                if (response.getCode() != HttpStatus.SC_OK) {
                    if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
                        logger.info("SKIP (Content not found for ISIN): {} {}", workItem.isin, workItem.date);
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_UNAUTHORIZED &&
                        bodyText.toLowerCase().contains("invalid Euroclear Liquidity subscription")) {
                        logger.info("SKIP (not entitled): {} {}", workItem.isin, workItem.date);
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_NOT_FOUND ||
                        (response.getCode() == HttpStatus.SC_BAD_REQUEST &&
                            bodyText.toLowerCase().contains("not found"))) {
                        logger.info("SKIP (ISIN not found): {} {}", workItem.isin, workItem.date);
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
                    fixedCells.add(workItem.isin);
                    fixedCells.add(workItem.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

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
                    AsyncCSVWriter writer = getMonthlyWriter(workItem.date, writers, headerLine, outDir);
                    writer.write(bufferContent);
                    logger.info("Processed ISIN {} on {}", workItem.isin, workItem.date);
                } catch (IOException e) {
                    logger.error("Error writing to file for {} {}", workItem.isin, workItem.date, e);
                }
            });
        }
    }

    private static void processAsync(WorkItem workItem, CloseableHttpClient httpClient, Supplier<String> apiToken,
                                     String[] fixedPaths, String[] expandBaseCandidates,
                                     String[] expandFields, Map<String, AsyncCSVWriter> writers,
                                     String headerLine, Path outDir) throws Exception {

        String nextUrl = String.format(SINGLE_ENDPOINT_FMT,
            URLEncoder.encode(workItem.isin, StandardCharsets.UTF_8),
            workItem.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

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
                        logger.info("SKIP (no JSON): {} {} HTTP {}", workItem.isin, workItem.date, response.getCode());
                        break;
                    }
                } else {
                    logger.info("SKIP (ISIN) as no content found: {} {} HTTP {}", workItem.isin, workItem.date, response.getCode());
                    break;
                }

                logger.debug("HTTP {} Body for {} {}:\n{}", response.getCode(), workItem.isin, workItem.date, bodyText);

                if (response.getCode() != HttpStatus.SC_OK) {
                    if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
                        logger.info("SKIP (Content not found for ISIN): {} {}", workItem.isin, workItem.date);
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_UNAUTHORIZED &&
                        bodyText.toLowerCase().contains("invalid Euroclear Liquidity subscription")) {
                        logger.info("SKIP (not entitled): {} {}", workItem.isin, workItem.date);
                        break;
                    }

                    if (response.getCode() == HttpStatus.SC_NOT_FOUND ||
                        (response.getCode() == HttpStatus.SC_BAD_REQUEST &&
                            bodyText.toLowerCase().contains("not found"))) {
                        logger.info("SKIP (ISIN not found): {} {}", workItem.isin, workItem.date);
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
                    fixedCells.add(workItem.isin);
                    fixedCells.add(workItem.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

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
            AsyncCSVWriter writer = getMonthlyWriter(workItem.date, writers, headerLine, outDir);
            writer.write(localBuffer.toString());
            logger.info("Processed ISIN {} on {}", workItem.isin, workItem.date);
        }
    }

    private static CompletableFuture<String> getAccessTokenAsync(boolean forceRefresh) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                tokenGate.acquire();
                try {
                    boolean needsRefresh = forceRefresh ||
                        cachedAuth.get() == null ||
                        cachedAuth.get().expiresOnDate().before(new Date(System.currentTimeMillis() + 300000)); // 5 minutes

                    if (needsRefresh) {
                        ClientCredentialParameters parameters = ClientCredentialParameters.builder(scopes).build();
                        IAuthenticationResult result = app.acquireToken(parameters).get();
                        cachedAuth.set(result);
                    }
                    return cachedAuth.get().accessToken();
                } finally {
                    tokenGate.release();
                }
            } catch (Exception e) {
                handleAuthenticationError(e);
                throw new RuntimeException("Failed to acquire token", e);
            }
        });
    }


    /**
     * Handle authentication errors and provide graceful exit
     */
    private static void handleAuthenticationError(Exception e) {
        // Check if this is an MSAL service exception with invalid client secret
        if (e.getCause() instanceof MsalServiceException) {
            MsalServiceException msalException = (MsalServiceException) e.getCause();
            String errorCode = msalException.errorCode();
            String message = msalException.getMessage();

            // Check for specific error codes that indicate authentication issues
            if ("AADSTS7000215".equals(errorCode) ||
                (message != null && message.contains("Invalid client secret"))) {

                logger.error("=== AUTHENTICATION FAILURE ===");
                logger.error("Invalid client secret provided.");
                logger.error("Error Code: {}", errorCode);
                logger.error("Error Message: {}", message);
                logger.error("Please check your client secret configuration.");
                logger.error("Ensure you're using the client secret VALUE, not the client secret ID.");
                logger.error("Application will exit.");

                throw new RuntimeException("Failed to authenticate with client secret ID");
            }

            // Handle other MSAL authentication errors
            if (errorCode != null && errorCode.startsWith("AADSTS")) {
                logger.error("=== AZURE AD AUTHENTICATION ERROR ===");
                logger.error("Error Code: {}", errorCode);
                logger.error("Error Message: {}", message);
                logger.error("Please check your Azure AD configuration and credentials.");
                logger.error("Application will exit.");

                throw new RuntimeException("Failed to authenticate with Azure AD configuration and credentials.");
            }
        }

        // Check for other authentication-related exceptions
        if (e.getMessage() != null &&
            (e.getMessage().contains("authentication") ||
                e.getMessage().contains("unauthorized") ||
                e.getMessage().contains("invalid_client"))) {

            logger.error("=== AUTHENTICATION ERROR ===");
            logger.error("Authentication failed: {}", e.getMessage());
            logger.error("Please verify your credentials and configuration.");
            logger.error("Application will exit.");

            // Exit gracefully
            throw new RuntimeException("Failed to authenticate with your configuration and credentials.");
        }
    }
}