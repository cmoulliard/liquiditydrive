package com.euroclear;

import java.time.LocalDate;

/**
 * Represents a successful result to be passed from Producers to Consumers.
 * This is an immutable data carrier.
 */
public record QueueItem(String json, String isin, LocalDate date){}