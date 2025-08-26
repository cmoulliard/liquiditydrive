package com.euroclear;

import java.time.LocalDate;
/**
 * Represents a single unit of work: one ISIN for one Date.
 */
public record WorkItem(String isin, LocalDate date){}