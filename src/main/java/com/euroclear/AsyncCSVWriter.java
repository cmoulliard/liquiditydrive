package com.euroclear;

import com.euroclear.util.CsvFileWriter;
import org.jboss.logging.Logger;

import java.io.Flushable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous CSV writer that writes to the underlying CSVWriter
 * on a dedicated thread. Guarantees that all queued lines are written
 * before close() returns.
 */
public class AsyncCSVWriter implements AutoCloseable, Flushable {
    private static final Logger logger = Logger.getLogger(AsyncCSVWriter.class);
    private final CsvFileWriter writer;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private volatile boolean running = true;

    public AsyncCSVWriter(CsvFileWriter writer) {
        this.writer = writer;
        this.thread = new Thread(this::runLoop, "AsyncCSVWriter");
        this.thread.setDaemon(false);
        this.thread.start();
    }

    /** Enqueue raw string (must include newline if needed). */
    public void write(String line) {
        if (!running) {
            throw new IllegalStateException("Writer is closed");
        }
        queue.add(line);
    }

    /** Convenience: enqueue a line and automatically add newline. */
    public void writeLine(String line) {
        write(line + System.lineSeparator());
    }

    private void runLoop() {
        logger.debug("AsyncCSVWriter thread started: " + Thread.currentThread().getName());
        try {
            while (running || !queue.isEmpty()) {
                String line = queue.poll(500, TimeUnit.MILLISECONDS);
                if (line != null) {
                    logger.debug("Writing line: " + line.substring(0, Math.min(50, line.length())) + "...");
                    writer.write(line);
                    writer.flush(); // Force immediate flush for debugging
                }
            }
        } catch (InterruptedException e) {
            logger.debug("AsyncCSVWriter interrupted");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.debug("AsyncCSVWriter IOException: " + e.getMessage());
            throw new RuntimeException("Error writing to CSV", e);
        } finally {
            try {
                logger.debug("AsyncCSVWriter finalizing, flushing writer");
                writer.flush();
            } catch (IOException ignored) {
                logger.debug("Error during final flush: " + ignored.getMessage());
            }
        }
        logger.debug("AsyncCSVWriter thread ended: " + Thread.currentThread().getName());
    }

    @Override
    public void flush() throws IOException {
        // Wait until queue is empty, then flush underlying writer
        while (!queue.isEmpty()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        running = false;
        try {
            thread.join(); // wait until background thread drains the queue
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writer.flush();
        writer.close();
    }
}
