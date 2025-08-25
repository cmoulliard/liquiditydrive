package com.euroclear.util;

import java.io.*;

public class CSVWriter implements Closeable, Flushable {

    private final BufferedWriter writer;

    public CSVWriter(File file) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file, true)); // append mode
    }

    public CSVWriter(Writer writer) {
        this.writer = new BufferedWriter(writer);
    }

    /**
     * Write a full line (adds newline automatically).
     */
    public void writeLine(String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    /**
     * Write raw content (no newline added).
     */
    public void write(String content) throws IOException {
        writer.write(content);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}

