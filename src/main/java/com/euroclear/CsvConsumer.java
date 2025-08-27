package com.euroclear;

import com.euroclear.util.CsvFileWriter;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import static com.euroclear.util.Parsing.generateCSVfromJSON;

public class CsvConsumer implements Runnable {
    private static final Logger logger = Logger.getLogger(CsvConsumer.class);

    private final BlockingQueue<QueueItem> queue;
    private final Map<String, CsvFileWriter> writers;
    private final CountDownLatch latch;

    public CsvConsumer(BlockingQueue<QueueItem> queue, Map<String, CsvFileWriter> writers, CountDownLatch latch) {
        this.queue = queue;
        this.writers = writers;
        this.latch = latch;
    }

    @Override
    public void run() {
        logger.infof("Started consumer thread");

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

        buffer.append(generateCSVfromJSON(item));
        logger.debugf("Processing: %s" + buffer);
    }

    private void writeBuffers(Map<String, StringBuilder> monthlyBuffers) {
        for (Map.Entry<String, StringBuilder> entry : monthlyBuffers.entrySet()) {
            CsvFileWriter writer = writers.get(entry.getKey());
            if (writer != null && entry.getValue().length() > 0) {
                // This log will tell you if the consumer is actually receiving data to write
                logger.debugf("Writing %s bytes to file for month %s", entry.getValue().length(), entry.getKey());

                try {
                    synchronized (writer) {
                        writer.write(entry.getValue().toString());
                        writer.flush();
                    }
                } catch (IOException e) {
                    logger.errorf("Error writing to file for month %s: %s", entry.getKey(), e.getMessage());
                }
            }
        }
    }
}