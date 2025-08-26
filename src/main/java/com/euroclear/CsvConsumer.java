package com.euroclear;

import com.euroclear.util.CSVWriter;
import com.euroclear.util.Parsing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static com.euroclear.util.LiquidityRecord.*;
import static com.euroclear.util.Parsing.formatJsonValue;
import static com.euroclear.util.Parsing.selectFirstNonEmpty;

public class CsvConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CsvConsumer.class);

    private final BlockingQueue<QueueItem> queue;
    private final Map<String, CSVWriter> writers;
    private final CountDownLatch latch;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String[] fixedPaths = FIXED_PATHS;
    private static final String[] expandBaseCandidates = EXPAND_BASE_CANDIDATES;
    private static final String[] expandFields = EXPAND_FIELDS;

    public CsvConsumer(BlockingQueue<QueueItem> queue, Map<String, CSVWriter> writers, CountDownLatch latch) {
        this.queue = queue;
        this.writers = writers;
        this.latch = latch;
    }

    @Override
    public void run() {
        logger.info("Started consumer thread");

        try {
            // The consumer will loop and take items from the queue.
            // It will automatically block here until an item is available.
            // It will only exit the loop when a "POISON_PILL" is received.
            QueueItem item;
            while ((item = queue.take()).json().equals("POISON_PILL") == false) {
                // Put the item back into a local batch for processing
                List<QueueItem> localBatch = new ArrayList<>();
                localBatch.add(item);
                queue.drainTo(localBatch, 99); // Drain any other items that arrived

                // Correct and clean way to process the items
                Map<String, StringBuilder> monthlyBuffers = new java.util.HashMap<>();
                for (QueueItem currentItem : localBatch) {
                    processItem(currentItem, monthlyBuffers);
                }

                writeBuffers(monthlyBuffers);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
        }
    }

    private void processItem(QueueItem item, Map<String, StringBuilder> monthlyBuffers) {
        String monthKey = item.date().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        StringBuilder buffer = monthlyBuffers.computeIfAbsent(monthKey, k -> new StringBuilder(8192));

        try {
            JsonNode json = objectMapper.readTree(item.json());
            List<String> fixedCells = new ArrayList<>();
            fixedCells.add(item.isin());
            fixedCells.add(item.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            for (String path : fixedPaths) {
                fixedCells.add(formatJsonValue(json.at("/" + path.replace(".", "/").replace("['", "/").replace("']", ""))));
            }

            JsonNode txItems = selectFirstNonEmpty(json, expandBaseCandidates);
            if (txItems == null || !txItems.isArray() || txItems.size() == 0) {
                List<String> row = new ArrayList<>(fixedCells);
                for (int i = 0; i < expandFields.length; i++) {
                    row.add("");
                }
                buffer.append(row.stream().map(Parsing::escapeCSV).collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
            } else {
                for (JsonNode txItem : txItems) {
                    List<String> row = new ArrayList<>(fixedCells);
                    for (String field : expandFields) {
                        row.add(formatJsonValue(txItem.get(field)));
                    }
                    buffer.append(row.stream().map(Parsing::escapeCSV).collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                }
            }
            logger.debug("Processing : " + buffer);

        } catch (IOException e) {
            logger.error("Error processing JSON for ISIN {} on {}: {}", item.isin(), item.date(), e.getMessage());
        }
    }

    private void writeBuffers(Map<String, StringBuilder> monthlyBuffers) {
        for (Map.Entry<String, StringBuilder> entry : monthlyBuffers.entrySet()) {
            CSVWriter writer = writers.get(entry.getKey());
            if (writer != null && entry.getValue().length() > 0) {
                // This log will tell you if the consumer is actually receiving data to write
                logger.debug("Writing {} bytes to file for month {}", entry.getValue().length(), entry.getKey());

                try {
                    synchronized (writer) {
                        writer.write(entry.getValue().toString());
                        writer.flush();
                    }
                } catch (IOException e) {
                    logger.error("Error writing to file for month {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }
}