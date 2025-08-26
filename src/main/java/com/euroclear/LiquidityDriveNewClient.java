package com.euroclear;

import com.euroclear.util.ApiConfig;
import com.euroclear.util.CSVWriter;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(LiquidityDriveNewClient.class);
    private static final int BATCH_SIZE = 100;

    public static void main(String[] args) throws Exception {
        logger.info("Starting Euroclear Liquidity Drive Client");

        // --- 1. SETUP ---
        Instant startTime = Instant.now();

        // Load external env variables to set the sensitive information
        loadEnvironmentVariables();

        // Create the Microsoft ConfidentialClientApplication
        createConfidentialClientApplication();

        // Create the folder where the CSV files will be stored
        Path outDir = Paths.get(System.getProperty("user.dir"), "new-out");
        Files.createDirectories(outDir);

        // --- 2. GENERATE WORKLOAD ---
        LocalDate start = ApiConfig.START_DATE;
        LocalDate end = ApiConfig.END_DATE;
        List<WorkItem> allWorkItems = generateWorkload(ISINS, start, end);
        logger.info("Generated {} total work items to process.", allWorkItems.size());

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

        try (CloseableHttpClient httpClient = createHttpClient()) {

            // --- 5. START CONSUMERS ---
            for (int i = 0; i < consumerThreads; i++) {
                consumerExecutor.submit(new CsvConsumer(workQueue, writers, consumersLatch));
            }

            // --- 6. PARTITION WORKLOAD USING NATIVE JAVA AND START PRODUCERS ---
            Collection<List<WorkItem>> batches = IntStream.range(0, (allWorkItems.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                .mapToObj(i -> allWorkItems.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, allWorkItems.size())))
                .collect(Collectors.toList());

            logger.info("Submitting {} batches to the producer pool...", batches.size());

            List<CompletableFuture<Void>> producerFutures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    try {
                        processWorkBatch(batch, httpClient, workQueue);
                    } catch (Exception e) {
                        logger.error("Error processing a batch: {}", e.getMessage());
                    }
                }, producerExecutor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).join();
            logger.info("All producers have finished submitting work.");

        } finally {
            // --- 7. SHUTDOWN ---
            logger.info("Signaling consumers to shut down...");
            for (int i = 0; i < consumerThreads; i++) {
                workQueue.put(new QueueItem("POISON_PILL", null, null));
            }

            consumerExecutor.shutdown();
            consumersLatch.await(5, TimeUnit.MINUTES);

            writers.values().forEach(writer -> {
                try { writer.close(); } catch (IOException e) { logger.error("Error closing writer", e); }
            });

            processingDuration(startTime);
        }
    }

    private static List<WorkItem> generateWorkload(String[] isins, LocalDate start, LocalDate end) {
        List<LocalDate> dates = eachBusinessDay(start, end, null).collect(Collectors.toList());
        return Arrays.stream(isins)
            .flatMap(isin -> dates.stream().map(date -> new WorkItem(isin, date)))
            .collect(Collectors.toList());
    }

    private static void processWorkBatch(List<WorkItem> batch, CloseableHttpClient httpClient, BlockingQueue<QueueItem> queue) throws Exception {
        String apiToken = getAccessTokenAsync(false).get();

        for (WorkItem workItem : batch) {
            String requestUrl = String.format(ApiConfig.SINGLE_ENDPOINT_FMT,
                URLEncoder.encode(workItem.isin(), StandardCharsets.UTF_8),
                workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            HttpGet request = new HttpGet(ApiConfig.LIQUIDITY_DRIVE_ADDRESS + requestUrl);
            request.setHeader("Authorization", "Bearer " + apiToken);
            request.setHeader("Ocp-Apim-Subscription-Key", API_KEY);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getCode() == HttpStatus.SC_OK && response.getEntity() != null) {
                    String bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (bodyText != null && !bodyText.trim().isEmpty()) {
                        logger.info("Received status [{}] for ISIN {} on {}", response.getCode(), workItem.isin(), workItem.date());
                        queue.put(new QueueItem(bodyText, workItem.isin(), workItem.date()));
                    }
                } else {
                    logger.warn("Received non-200 status [{}] for ISIN {} on {}", response.getCode(), workItem.isin(), workItem.date());
                }
            } catch (Exception e) {
                logger.error("HTTP request failed for ISIN {} on {}: {}", workItem.isin(), workItem.date(), e.getMessage());
            }
        }
    }
}
