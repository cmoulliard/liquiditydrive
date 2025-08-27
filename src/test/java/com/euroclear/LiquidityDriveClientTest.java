package com.euroclear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.euroclear.util.Parsing.generateCSVfromJSON;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for LiquidityNewDriveClient
 */
public class LiquidityDriveClientTest {
    
    @Test
    @DisplayName("JSON to CSV should not be null")
    void testJsonToCSVFormatting() throws IOException {
        // CSV result
        InputStream is = getClass().getClassLoader().getResourceAsStream("samples/AT0000A326N4/csv.txt");
        assertNotNull(is);
        String csvExpected = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        // Read the JSON File as String
        is = getClass().getClassLoader().getResourceAsStream("samples/AT0000A326N4/isin.json");
        assertNotNull(is);
        String isinJSON = new String(is.readAllBytes(), "UTF-8");
        QueueItem item = new QueueItem(isinJSON,"AT0000A326N4", LocalDate.parse("2025-01-02",DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        StringBuilder sb = generateCSVfromJSON(item);
        assertNotNull(sb.toString());
        assertEquals(sb.toString(),csvExpected);
    }
}
