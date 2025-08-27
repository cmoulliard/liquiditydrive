package com.euroclear.util;

import com.euroclear.AsyncCSVWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.euroclear.util.LiquidityRecord.headerLine;

public class CsvWriters {
    private static final Set<String> headersWritten = ConcurrentHashMap.newKeySet();

    public static AsyncCSVWriter getMonthlyWriter(LocalDate date,
                                                  Map<String, AsyncCSVWriter> writers,
                                                  String headerLine,
                                                  Path outDir) throws IOException {
        String monthKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return writers.computeIfAbsent(monthKey, k -> {
            try {
                Path outFile = outDir.resolve("securities_" + k + ".csv");
                CsvFileWriter baseWriter = new CsvFileWriter(outFile.toFile());
                AsyncCSVWriter asyncWriter = new AsyncCSVWriter(baseWriter);

                // Thread-safe header writing - only write once per month
                if (headersWritten.add(k)) {
                    asyncWriter.write(headerLine + System.lineSeparator());
                }

                return asyncWriter;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create writer for " + k, e);
            }
        });
    }

    public static Map<String, CsvFileWriter> createMonthlyWriters(LocalDate start, LocalDate end, Path outDir) throws IOException {
        Map<String, CsvFileWriter> writers = new HashMap<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            String monthKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            if (!writers.containsKey(monthKey)) {
                Path filePath = outDir.resolve(monthKey + ".csv");

                // Check if the file is new or empty before writing the header
                boolean needsHeader = !Files.exists(filePath) || Files.size(filePath) == 0;
                CsvFileWriter writer = new CsvFileWriter(filePath);
                writers.put(monthKey, writer);

                if (needsHeader) {
                    writer.writeLine(headerLine());
                    writer.flush(); // Ensure the header is written to disk immediately
                }

            }
            current = current.plusMonths(1);
        }
        return writers;
    }

}
