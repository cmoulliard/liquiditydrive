package com.euroclear.util;

import com.euroclear.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.euroclear.util.LiquidityRecord.*;

public class Parsing {
    private static final Logger logger = Logger.getLogger(Parsing.class);

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // --- OPTIMIZATION: Pre-process the JSON paths once ---
    private static final String[] COMPILED_FIXED_PATHS = Stream.of(FIXED_PATHS)
        .map(path -> "/" + path.replace(".", "/").replace("['", "/").replace("']", ""))
        .toArray(String[]::new);

    private static final String[] COMPILED_EXPAND_BASE_CANDIDATES = Stream.of(EXPAND_BASE_CANDIDATES)
        .map(path -> "/" + path.replace("[*]", "").replace("['", "/").replace("']", ""))
        .toArray(String[]::new);

    public static StringBuilder generateCSVfromJSON(QueueItem item) {
        StringBuilder buffer = new StringBuilder(8192);
        try {
            JsonNode json = objectMapper.readTree(item.json());
            List<String> fixedCells = new ArrayList<>(FIXED_PATHS.length + 2);
            fixedCells.add(item.isin());
            fixedCells.add(item.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            for (String compiledPath : COMPILED_FIXED_PATHS) {
                //fixedCells.add(formatJsonValue(json.at("/" + path.replace(".", "/").replace("['", "/").replace("']", ""))));
                fixedCells.add(formatJsonValue(json.at(compiledPath)));
            }

            JsonNode txItems = selectFirstNonEmpty(json, COMPILED_EXPAND_BASE_CANDIDATES);
            if (txItems == null || !txItems.isArray() || txItems.isEmpty()) {
                List<String> row = new ArrayList<>(fixedCells);
                for (int i = 0; i < EXPAND_FIELDS.length; i++) {
                    row.add("");
                }
                //buffer.append(row.stream().map(Parsing::escapeCSV).collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                appendRowToBuffer(buffer, row);
            } else {
                // --- OPTIMIZATION: Reuse the 'row' list to avoid object creation ---
                List<String> row = new ArrayList<>(fixedCells.size() + EXPAND_FIELDS.length);
                for (JsonNode txItem : txItems) {
                    row.clear();
                    row.addAll(fixedCells);
                    for (String field : EXPAND_FIELDS) {
                        row.add(formatJsonValue(txItem.get(field)));
                    }
                    appendRowToBuffer(buffer, row);
                }
            }
        } catch (IOException e) {
            logger.errorf("Error processing JSON for ISIN %s on %s: %s", item.isin(), item.date(), e.getMessage());
        }
        return buffer;
    }

    /**
     * OPTIMIZATION: A dedicated, high-performance method to join strings for a CSV row.
     * This avoids the overhead of using streams in a tight loop.
     */
    private static void appendRowToBuffer(StringBuilder buffer, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            buffer.append(escapeCSV(row.get(i)));
            if (i < row.size() - 1) {
                buffer.append(DELIM);
            }
        }
        buffer.append("\n");
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
/*        if (s == null) return "";

        if (s.contains("\r") || s.contains("\n")) {
            s = s.replace("\r", " ").replace("\n", " ");
        }

        if (s.indexOf(DELIM) >= 0 || s.indexOf('"') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return s;*/
        if (s == null || s.isEmpty()) {
            return "\"\""; // Return an empty quoted string for null or empty values
        }
        // Always escape internal quotes and wrap the result in quotes.
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
