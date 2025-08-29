package com.euroclear;

import com.euroclear.util.ApiConfig;
import com.euroclear.util.CsvFileWriter;
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
import static com.euroclear.util.Batch.BATCH_SIZE;
import static com.euroclear.util.Calculation.eachBusinessDay;
import static com.euroclear.util.Calculation.processingDuration;
import static com.euroclear.util.CsvWriters.createMonthlyWriters;
import static com.euroclear.util.ISIN.ISINS;
import static com.euroclear.util.LiquidityRecord.populateHeaders;

public class LiquidityDriveNewClient {
    private static final Logger logger = Logger.getLogger(LiquidityDriveNewClient.class);
    private static List<WorkItem> allWorkItems;
    private static Collection<List<WorkItem>> batches;
    private static String[] isinsToProcess;

    private static final Set<Integer> LOGGABLE_ERROR_CODES = Set.of(
        HttpStatus.SC_NO_CONTENT,       // 204
        HttpStatus.SC_UNAUTHORIZED,     // 401
        HttpStatus.SC_NOT_FOUND,        // 404
        HttpStatus.SC_TOO_MANY_REQUESTS // 429
    );

    // Allow up to 5 concurrent requests
    private static final Semaphore rateLimiter = new Semaphore(5);

    public static void main(String[] args) throws Exception {
        logger.infof("Starting Euroclear Liquidity Drive Client");

        // --- 1. SETUP ---
        Instant startTime = Instant.now();
        final boolean isDryRun = "true".equalsIgnoreCase(System.getenv("DRY_RUN"));

        // Load external env variables
        loadEnvironmentVariables();

        if (isDryRun) {
            logger.infof("<<<<< RUNNING IN DRY-RUN MODE >>>>>");
            logger.infof("Connecting to http://localhost:8080. No authentication will be used.");
        } else {
            // Create the Microsoft ConfidentialClientApplication
            createConfidentialClientApplication();
        }

        // Create the folder where the CSV files will be stored
        Path outDir = Paths.get(System.getProperty("user.dir"), "out");
        Files.createDirectories(outDir);

        // --- 2. CREATE ERROR LOG WRITER ---
        Path errorLogPath = outDir.resolve("error-log.csv");
        try (CsvFileWriter errorWriter = new CsvFileWriter(errorLogPath)) {
            if (Files.size(errorLogPath) == 0) {
                errorWriter.writeLine("\"ISIN\",\"Date\",\"ErrorCode\"");
                errorWriter.flush();
            }

            // --- 3. GENERATE WORKLOAD ---
            LocalDate start = ApiConfig.START_DATE;
            LocalDate end = ApiConfig.END_DATE;

            logger.infof("Range of dates: %s - %s", start, end);

            isinsToProcess = Optional.ofNullable(System.getenv("ISINS"))
                .map(s -> s.split("\\s*,\\s*"))
                .orElse(ISINS);

            logger.infof("Processing ISINS: %s", Arrays.toString(isinsToProcess));

            allWorkItems = generateWorkload(isinsToProcess, start, end);

            // --- 3. PRE-CREATE CSV WRITERS ---
            // Initialize headers as needed for each CSV file
            populateHeaders();

            // Create the monthly securities csv files for the period
            Map<String, CsvFileWriter> writers = createMonthlyWriters(start, end, outDir);

            // --- 4. SETUP PRODUCER-CONSUMER INFRASTRUCTURE ---
            BlockingQueue<QueueItem> workQueue = new LinkedBlockingQueue<>(10000); // Bounded queue
            ExecutorService producerExecutor = Executors.newVirtualThreadPerTaskExecutor();

            int consumerThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
            logger.infof("Number of consumer threads: %d", consumerThreads);

            ExecutorService consumerExecutor = Executors.newFixedThreadPool(consumerThreads);
            CountDownLatch consumersLatch = new CountDownLatch(consumerThreads);

            logger.infof("Number of work items: %d" + allWorkItems.size());

            // --- 6. PARTITION WORKLOAD USING NATIVE JAVA AND START PRODUCERS ---
            batches = IntStream.range(0, (allWorkItems.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                .mapToObj(i -> allWorkItems.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, allWorkItems.size())))
                .collect(Collectors.toList());

            logger.infof("Number of batches calculated: %d" + batches.size());

            try (CloseableHttpClient httpClient = isDryRun ? HttpClients.createDefault() : createHttpClient()) {

                logger.infof("Submitting %d consumer tasks to the executor...", consumerThreads);
                for (int i = 0; i < consumerThreads; i++) {
                    consumerExecutor.submit(new CsvConsumer(workQueue, writers, consumersLatch));
                }
                logger.info("All consumer tasks submitted. Starting producers...");

                List<CompletableFuture<Void>> producerFutures = batches.stream()
                    .map(batch -> CompletableFuture.runAsync(() -> {
                        try {
                            processWorkBatch(batch, httpClient, workQueue, isDryRun, errorWriter);
                        } catch (Exception e) {
                            logger.errorf("Error processing a batch: %s", e.getMessage());
                        }
                    }, producerExecutor))
                    .collect(Collectors.toList());

                CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).join();
                logger.infof("All producers have finished submitting work.");
            } catch (Exception e) {
                logger.errorf("Error occurs during the processing: %s", e.getMessage());
                e.printStackTrace();
            } finally {
                // --- 7. SHUTDOWN ---
                logger.infof("Signaling consumers to shut down...");
                for (int i = 0; i < consumerThreads; i++) {
                    workQueue.put(new QueueItem("POISON_PILL", null, null));
                }

                consumerExecutor.shutdown();
                consumersLatch.await(5, TimeUnit.MINUTES);

                writers.values().forEach(writer -> {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        logger.error("Error closing writer", e);
                    }
                });

                logger.infof("Generated %s total work items to process.", allWorkItems.size());
                logger.infof("Processed %d batches containing %d records", batches.size(), BATCH_SIZE);
                logger.infof("Number of ISIN processed: %d", isinsToProcess);
                processingDuration(startTime);
            }
        } // Error writer is automatically closed here by try-with-resources
    }

    private static List<WorkItem> generateWorkload(String[] isins, LocalDate start, LocalDate end) {
        List<LocalDate> dates = eachBusinessDay(start, end, null).collect(Collectors.toList());
        return Arrays.stream(isins)
            .flatMap(isin -> dates.stream().map(date -> new WorkItem(isin, date)))
            .collect(Collectors.toList());
    }

    private static void processWorkBatch(List<WorkItem> batch, CloseableHttpClient httpClient, BlockingQueue<QueueItem> queue, boolean isDryRun, CsvFileWriter errorWriter) throws Exception {
        // Only get a token if not in dry-run mode
        String apiToken = isDryRun ? null : getAccessTokenAsync(false).get();

        for (WorkItem workItem : batch) {

            rateLimiter.acquire(); // This will block until a permit is available

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
                int statusCode = response.getCode();
                if (statusCode == HttpStatus.SC_OK && response.getEntity() != null) {
                    String bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (bodyText != null && !bodyText.trim().isEmpty()) {
                        queue.put(new QueueItem(bodyText, workItem.isin(), workItem.date()));
                    }
                } else {
                    // --- NEW ERROR LOGGING LOGIC ---
                    if (LOGGABLE_ERROR_CODES.contains(statusCode)) {
                        String errorRow = String.format("\"%s\",\"%s\",%d",
                            workItem.isin(),
                            workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            statusCode);

                        // Synchronize on the writer to ensure thread-safe writes
                        synchronized (errorWriter) {
                            errorWriter.writeLine(errorRow);
                            errorWriter.flush();
                        }
                    }
                }
                logger.warnf("Received status [%d] for ISIN %s on %s", statusCode, workItem.isin(), workItem.date());
            } catch (Exception e) {
                logger.errorf("HTTP request failed for ISIN %s on %s: %s", workItem.isin(), workItem.date(), e.getMessage());
            } finally {
                // This block runs after the request is finished
                Thread.sleep(SLEEP_TIME_MS);
                rateLimiter.release(); // Release the permit for the next thread
            }
        }
    }
}
