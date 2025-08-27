package com.euroclear.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CsvFileWriter implements Closeable, Flushable {

    private final BufferedWriter writer;

    /**
     * Modern constructor using java.nio.Path. This is the recommended one to use.
     * It uses UTF-8 encoding and handles file creation/appending safely.
     * @param path The path to the output file.
     * @throws IOException
     */
    public CsvFileWriter(Path path) throws IOException {
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Legacy constructor using java.io.File.
     * @param file The file to write to.
     * @throws IOException
     */
    public CsvFileWriter(File file) throws IOException {
        // This constructor now delegates to the Path constructor for consistency.
        this(file.toPath());
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

