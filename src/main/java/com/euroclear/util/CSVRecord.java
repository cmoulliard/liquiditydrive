package com.euroclear.util;

import java.time.LocalDate;

/**
 * Data class to hold CSV record information for the producer-consumer pattern
 */
public class CSVRecord {
    public final String monthKey;
    public final String data;
    public final boolean isHeader;
    
    public CSVRecord(LocalDate date, String data, boolean isHeader) {
        this.monthKey = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        this.data = data;
        this.isHeader = isHeader;
    }
    
    public CSVRecord(String monthKey, String data, boolean isHeader) {
        this.monthKey = monthKey;
        this.data = data;
        this.isHeader = isHeader;
    }
}
