package com.euroclear;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelSleepTest {

    // --- Configuration for the test ---
    private static final int NUMBER_OF_THREADS = 1;
    private static final int TOTAL_WORK_ITEMS = 20;
    private static final int BATCH_SIZE = 5;
    private static final long SLEEP_TIME_MS = 3000; // 3 seconds

    // --- Dummy/Placeholder classes to make the code runnable ---

    record WorkItem(String isin, LocalDate date) {}
    record QueueItem(String body, String isin, LocalDate date) {}
    static class ApiConfig {
        public static final String SINGLE_ENDPOINT_FMT = "/liquidity?isin=%s&date=%s";
        public static final String LIQUIDITY_DRIVE_ADDRESS = "https://production.api";
    }

    static class CsvFileWriter {
        public void writeLine(String line) throws IOException {
            // In a real scenario, this would write to a file.
            // For this test, we can just print it to see the output.
            log("ERROR_WRITER: " + line);
        }
        public void flush() throws IOException { /* MOCKED */ }
    }

    // --- Mocked external dependencies ---
    private static final String API_KEY = "mock-api-key";
    private static final Set<Integer> LOGGABLE_ERROR_CODES = Set.of(400, 404, 500);
    private static final SimpleLogger logger = new SimpleLogger();

    public static void main(String[] args) throws Exception {
        logger.info("--- Starting Parallel Processing Test ---");
        logger.info("CONFIG: Threads=" + NUMBER_OF_THREADS + ", Total Items=" + TOTAL_WORK_ITEMS + ", Batch Size=" + BATCH_SIZE);

        // 1. Setup a thread pool to manage our concurrent tasks
        ExecutorService producerExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        // 2. Create Mock Dependencies
        CloseableHttpClient mockHttpClient = createMockHttpClient();
        BlockingQueue<QueueItem> workQueue = new LinkedBlockingQueue<>();
        CsvFileWriter errorWriter = new CsvFileWriter();

        // 3. Create sample data
        List<WorkItem> allWorkItems = IntStream.range(0, TOTAL_WORK_ITEMS)
            .mapToObj(i -> new WorkItem("ISIN" + i, LocalDate.now()))
            .collect(Collectors.toList());

        // 4. Split data into batches
        List<List<WorkItem>> batches = partition(allWorkItems, BATCH_SIZE);
        logger.info("Created " + batches.size() + " batches to process.");
        logger.info("---------------------------------------------");


        // --- CORE EXECUTION LOGIC ---
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Void>> producerFutures = batches.stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                try {
                    // Each batch is processed on a separate thread
                    processWorkBatch(batch, mockHttpClient, workQueue, false, errorWriter);
                } catch (Exception e) {
                    logger.errorf("Error processing a batch: %s", e.getMessage());
                }
            }, producerExecutor))
            .toList();

        // Wait for all the CompletableFuture tasks to finish
        CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).join();

        long endTime = System.currentTimeMillis();
        logger.info("---------------------------------------------");
        logger.info("--- All batches processed. ---");
        logger.info("Total execution time: " + (endTime - startTime) + " ms");
        logger.info("Items in queue: " + workQueue.size());

        // 5. Shutdown the executor service
        producerExecutor.shutdown();
        logger.info("--- Test Finished ---");
    }

    /**
     * The method under test. It processes a list of WorkItems, making a simulated
     * HTTP call for each and sleeping between calls.
     */
    private static void processWorkBatch(List<WorkItem> batch, CloseableHttpClient httpClient, BlockingQueue<QueueItem> queue, boolean isDryRun, CsvFileWriter errorWriter) throws Exception {
        logger.infof("Starting to process batch of %d items...", batch.size());

        for (WorkItem workItem : batch) {
            // This method is just a placeholder for the test
            String apiToken = "dummy-token-for-" + Thread.currentThread().getName();

            // Construct URL (logic is kept for completeness)
            String dateString = workItem.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String encodedIsin = URLEncoder.encode(workItem.isin(), StandardCharsets.UTF_8);
            String endpoint = String.format(ApiConfig.SINGLE_ENDPOINT_FMT, encodedIsin, dateString);
            String requestUrl = ApiConfig.LIQUIDITY_DRIVE_ADDRESS + endpoint;
            HttpGet request = new HttpGet(requestUrl);

            // Set headers
            request.setHeader("Authorization", "Bearer " + apiToken);
            request.setHeader("Ocp-Apim-Subscription-Key", API_KEY);
            request.setHeader("Accept", "application/json");

            long actionStartTime = System.currentTimeMillis();

            logger.infof("-> Will execute request for %s. Sleeping for %d ms...", workItem.isin(), SLEEP_TIME_MS);
            Thread.sleep(SLEEP_TIME_MS);

            try (CloseableHttpResponse response = httpClient.execute(request)) {

                // Stop timing after the response is received
                long actionEndTime = System.currentTimeMillis();
                long duration = actionEndTime - actionStartTime;

                int statusCode = response.getCode();
                if (statusCode == HttpStatus.SC_OK && response.getEntity() != null) {
                    String bodyText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    queue.put(new QueueItem(bodyText, workItem.isin(), workItem.date()));
                } else {
                    if (LOGGABLE_ERROR_CODES.contains(statusCode)) {
                        String errorRow = String.format("\"%s\",\"%s\",%d", workItem.isin(), dateString, statusCode);
                        synchronized (errorWriter) {
                            errorWriter.writeLine(errorRow);
                            errorWriter.flush();
                        }
                    }
                }
                logger.infof("<- Received status [%d] for %s (Total time: %d ms)", statusCode, workItem.isin(), duration);
            } catch (Exception e) {
                logger.errorf("HTTP request failed for %s: %s", workItem.isin(), e.getMessage());
            }
        }
        logger.infof("Finished processing batch of %d items.", batch.size());
    }

    // --- UTILITY METHODS ---

    /**
     * Creates a mock HttpClient that always returns a 200 OK response.
     */
    private static CloseableHttpClient createMockHttpClient() throws IOException {
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);

        // Configure the mock response
        Mockito.when(mockResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(mockResponse.getEntity()).thenReturn(new StringEntity("{\"status\":\"ok\"}"));

        // Make the mock client return the mock response for any HTTP GET request
        Mockito.when(mockClient.execute(Mockito.any(HttpGet.class))).thenReturn(mockResponse);

        return mockClient;
    }

    /**
     * Partitions a list into sublists of a specified size.
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        final AtomicInteger counter = new AtomicInteger(0);
        return new ArrayList<>(collection.stream()
            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / size))
            .values());
    }

    /**
     * A simple logger to print messages with timestamps and thread names.
     */
    private static void log(String message) {
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).format(java.time.LocalTime.now());
        System.out.printf("[%s] [%s] %s%n", time, Thread.currentThread().getName(), message);
    }

    // A simple logger class to mimic the original code's logger.
    static class SimpleLogger {
        public void info(String msg) { log(msg); }
        public void infof(String format, Object... args) { log(String.format(format, args)); }
        public void errorf(String format, Object... args) { log("ERROR: " + String.format(format, args)); }
    }
}