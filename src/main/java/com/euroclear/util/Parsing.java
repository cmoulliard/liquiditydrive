package com.euroclear.util;

import com.euroclear.AsyncCSVWriter;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.euroclear.util.LiquidityRecord.DELIM;
import static com.euroclear.util.LiquidityRecord.headerLine;

public class Parsing {
    private static final Set<String> headersWritten = ConcurrentHashMap.newKeySet();
    
    public static AsyncCSVWriter getMonthlyWriter(LocalDate date,
                                                  Map<String, AsyncCSVWriter> writers,
                                                  String headerLine,
                                                  Path outDir) throws IOException {
        String monthKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return writers.computeIfAbsent(monthKey, k -> {
            try {
                Path outFile = outDir.resolve("securities_" + k + ".csv");
                CSVWriter baseWriter = new CSVWriter(outFile.toFile());
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

    public static Map<String, CSVWriter> createMonthlyWriters(LocalDate start, LocalDate end, Path outDir) throws IOException {
        Map<String, CSVWriter> writers = new HashMap<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            String monthKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            if (!writers.containsKey(monthKey)) {
                Path filePath = outDir.resolve(monthKey + ".csv");
                CSVWriter writer = new CSVWriter(filePath);
                // Add the header line
                writer.writeLine(headerLine());
                writers.put(monthKey, writer);
            }
            current = current.plusMonths(1);
        }
        return writers;
    }

    public static Stream<LocalDate> eachBusinessDay(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        return start.datesUntil(end.plusDays(1))
            .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY &&
                date.getDayOfWeek() != DayOfWeek.SUNDAY)
            .filter(date -> holidays == null || !holidays.contains(date));
    }

    public static JsonNode selectFirstNonEmpty(JsonNode obj, String[] candidates) {
        for (String candidate : candidates) {
            JsonNode node = obj.at("/" + candidate.replace("[*]", "").replace("['", "/").replace("']", ""));
            if (node != null && !node.isMissingNode() && node.isArray() && node.size() > 0) {
                return node;
            }
        }
        return null;
    }

    public static String formatJsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isNumber()) {
            return node.asText();
        }
        return node.toString();
    }

    public static String escapeCSV(String s) {
        if (s == null) return "";

        if (s.contains("\r") || s.contains("\n")) {
            s = s.replace("\r", " ").replace("\n", " ");
        }

        if (s.indexOf(DELIM) >= 0 || s.indexOf('"') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return s;
    }
}
