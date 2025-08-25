package com.euroclear.util;

import java.time.LocalDate;

public class WorkItem {
    public String isin;
    public LocalDate date;

    public WorkItem(String isin, LocalDate date) {
        this.isin = isin;
        this.date = date;
    }
}