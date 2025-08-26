package com.euroclear;

import com.euroclear.util.ApiConfig;
import com.euroclear.util.CSVWriter;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.euroclear.util.ApiConfig.*;
import static com.euroclear.util.Authentication.*;
import static com.euroclear.util.Calculation.processingDuration;
import static com.euroclear.util.ISIN.ISINS;
import static com.euroclear.util.LiquidityRecord.populateHeaders;
import static com.euroclear.util.Parsing.createMonthlyWriters;
import static com.euroclear.util.Parsing.eachBusinessDay;

public class LiquidityDriveNewClient {
    private static final Logger logger = Logger.getLogger(LiquidityDriveNewClient.class);
    private static final int BATCH_SIZE = 100;
    private static List<WorkItem> allWorkItems;

    public static void main(String[] args) throws Exception {
        logger.infof("Starting Euroclear Liquidity Drive Client");

        // --- 1. SETUP ---
        Instant startTime = Instant.now();
        final boolean isDryRun = "true".equalsIgnoreCase(System.getenv("DRY_RUN"));

        if (isDryRun) {
            logger.infof("<<<<< RUNNING IN DRY-RUN MODE >>>>>");
            logger.infof("Connecting to http://localhost:8080. No authentication will be used.");
        } else {
            // Load external env variables to set the sensitive information
            loadEnvironmentVariables();
            // Create the Microsoft ConfidentialClientApplication
            createConfidentialClientApplication();
        }

        // Create the folder where the CSV files will be stored
        Path outDir = Paths.get(System.getProperty("user.dir"), "new-out");
        Files.createDirectories(outDir);

        // --- 2. GENERATE WORKLOAD ---
        LocalDate start = ApiConfig.START_DATE;
        LocalDate end = ApiConfig.END_DATE;
        allWorkItems = generateWorkload(ISINS, start, end);

        // --- 3. PRE-CREATE CSV WRITERS ---
        // Initialize headers as needed for each CSV file
        populateHeaders();

        // Create the monthly securities csv files for the period
        Map<String, CSVWriter> writers = createMonthlyWriters(start, end, outDir);

        // --- 4. SETUP PRODUCER-CONSUMER INFRASTRUCTURE ---
        BlockingQueue<QueueItem> workQueue = new LinkedBlockingQueue<>(10000); // Bounded queue
        ExecutorService producerExecutor = Executors.newVirtualThreadPerTaskExecutor();

        int consumerThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        ExecutorService consumerExecutor = Executors.newFixedThreadPool(consumerThreads);
        CountDownLatch consumersLatch = new CountDownLatch(consumerThreads);

        try (CloseableHttpClient httpClient = isDryRun ? HttpClients.createDefault() : createHttpClient()) {

            // --- 5. START CONSUMERS ---
            for (int i = 0; i < consumerThreads; i++) {
                consumerExecutor.submit(new CsvConsumer(workQueue, writers, consumersLatch));
            }

            // --- 6. PARTITION WORKLOAD USING NATIVE JAVA AND START PRODUCERS ---
            Collection<List<WorkItem>> batches = IntStream.range(0, (allWorkItems.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                .mapToObj(i -> allWorkItems.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, allWorkItems.size())))
                .collect(Collectors.toList());

            logger.infof("Submitting %s batches to the producer pool...", batches.size());

            List<CompletableFuture<Void>> producerFutures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    try {
                        processWorkBatch(batch, httpClient, workQueue, isDryRun);
                    } catch (Exception e) {
                        logger.errorf("Error processing a batch: %s", e.getMessage());
                    }
                }, producerExecutor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).join();
            logger.infof("All producers have finished submitting work.");

        } finally {
            // --- 7. SHUTDOWN ---
            logger.infof("Signaling consumers to shut down...");
            for (int i = 0; i < consumerThreads; i++) {
                workQueue.put(new QueueItem("POISON_PILL", null, null));
            }

            consumerExecutor.shutdown();
            consumersLatch.await(5, TimeUnit.MINUTES);

            writers.values().forEach(writer -> {
                try { writer.close(); } catch (IOException e) { logger.error("Error closing writer", e); }
            });

            logger.infof("Generated %s total work items to process.", allWorkItems.size());
            logger.infof("Number of ISIN processed: %d",ISINS.length);
            processingDuration(startTime);
        }
    }

    private static List<WorkItem> generateWorkload(String[] isins, LocalDate start, LocalDate end) {
        List<LocalDate> dates = eachBusinessDay(start, end, null).collect(Collectors.toList());
        return Arrays.stream(isins)
            .flatMap(isin -> dates.stream().map(date -> new WorkItem(isin, date)))
            .collect(Collectors.toList());
    }

    private static void processWorkBatch(List<WorkItem> batch, CloseableHttpClient httpClient, BlockingQueue<QueueItem> queue, boolean isDryRun) throws Exception {
        // Only get a token if not in dry-run mode
        String apiToken = isDryRun ? null : getAccessTokenAsync(false).get();

        for (WorkItem workItem : batch) {
            // --- ADD THIS BLOCK TO SLEEP IN DRY-RUN MODE ---
            if (isDryRun) {
                try {
                    // Sleep for 1000 milliseconds (1 second)
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Preserve the interrupted status if the sleep is interrupted
                    Thread.currentThread().interrupt();
                    logger.warn("Dry-run sleep was interrupted.");
                }
            }
            // -------------------------------------------------

            String requestUrl;
            String dateString = workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String encodedIsin = URLEncoder.encode(workItem.isin(), StandardCharsets.UTF_8);

            if (isDryRun) {
                // Construct URL for the local mock server
                requestUrl = String.format("http://localhost:8080/liquidity?isin=%s&date=%s", encodedIsin, dateString);
            } else {
                // Construct URL for the production server
                String endpoint = String.format(ApiConfig.SINGLE_ENDPOINT_FMT, encodedIsin, dateString);
                requestUrl = ApiConfig.LIQUIDITY_DRIVE_ADDRESS + endpoint;
            }

            HttpGet request = new HttpGet(requestUrl);

            // Only add headers if not in dry-run mode
            if (!isDryRun) {
                request.setHeader("Authorization", "Bearer " + apiToken);
                request.setHeader("Ocp-Apim-Subscription-Key", API_KEY);
                request.setHeader("Accept", "application/json");
            }

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getCode() == HttpStatus.SC_OK && response.getEntity() != null) {
                    String bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (bodyText != null && !bodyText.trim().isEmpty()) {
                        logger.warnf("Received status [%s] for ISIN %s on %s", response.getCode(), workItem.isin(), workItem.date());
                        queue.put(new QueueItem(bodyText, workItem.isin(), workItem.date()));
                    }
                } else {
                    logger.warnf("Received non-200 status [%s] for ISIN %s on %s", response.getCode(), workItem.isin(), workItem.date());
                }
            } catch (Exception e) {
                logger.errorf("HTTP request failed for ISIN %s on %s: %s", workItem.isin(), workItem.date(), e.getMessage());
            }
        }
    }
}
