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

import static com.euroclear.util.LiquidityRecord.*;

public class Parsing {
    private static final Logger logger = Logger.getLogger(Parsing.class);

    private static ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static StringBuffer generateCSVfromJSON(QueueItem item) {
        StringBuffer buffer = new StringBuffer();
        try {
            JsonNode json = objectMapper.readTree(item.json());
            List<String> fixedCells = new ArrayList<>();
            fixedCells.add(item.isin());
            fixedCells.add(item.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            for (String path : FIXED_PATHS) {
                fixedCells.add(formatJsonValue(json.at("/" + path.replace(".", "/").replace("['", "/").replace("']", ""))));
            }

            JsonNode txItems = selectFirstNonEmpty(json, EXPAND_BASE_CANDIDATES);
            if (txItems == null || !txItems.isArray() || txItems.size() == 0) {
                List<String> row = new ArrayList<>(fixedCells);
                for (int i = 0; i < EXPAND_FIELDS.length; i++) {
                    row.add("");
                }
                buffer.append(row.stream().map(Parsing::escapeCSV).collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
            } else {
                for (JsonNode txItem : txItems) {
                    List<String> row = new ArrayList<>(fixedCells);
                    for (String field : EXPAND_FIELDS) {
                        row.add(formatJsonValue(txItem.get(field)));
                    }
                    buffer.append(row.stream().map(Parsing::escapeCSV).collect(Collectors.joining(String.valueOf(DELIM)))).append("\n");
                }
            }
        } catch (IOException e) {
            logger.errorf("Error processing JSON for ISIN %s on %s: %s", item.isin(), item.date(), e.getMessage());
        }
        return buffer;
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
