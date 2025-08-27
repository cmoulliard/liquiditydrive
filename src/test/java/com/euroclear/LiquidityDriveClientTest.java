package com.euroclear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
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
        String isinJSON = new String(Files.readAllBytes(Paths.get("samples/ISIN-AT0000A326N4.json")));
        QueueItem item = new QueueItem(isinJSON,"AT0000A326N4", LocalDate.parse("2025-01-02",DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        String csvExpected = "AT0000A326N4;2025-01-02;2025-01-02T00:00:00;0.0;0.0;0.9385394071;0.0;AT0000A326N4;A;T1;H02.50;N;N;2026-01-31T00:00:00;2026-01-30T00:00:00;2025-01-31T00:00:00;2026-01-31T00:00:00;ANNUA;FIX;3.125;31.25;4.4E7;4.4E7;4.445232E7;5.0;8800000.0;8890464.0;1100000.0;1111308.0;4.0;0.0;4.311E7;0.0206;9837500.0;-0.1055;1213333.3333;-0.0934;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;";

        StringBuffer sb = generateCSVfromJSON(item);
        assertNotNull(sb.toString());
        assertEquals(sb.toString(),csvExpected);
    }
}
