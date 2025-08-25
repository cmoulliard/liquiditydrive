package com.euroclear.util;

import java.time.LocalDate;

/**
 * Constants and configuration data for Liquidity Drive API client
 */
public class Constants {

    // Column names
    public static final String COL_REQUESTED_ISIN = "RequestedISIN";
    public static final String COL_REQUESTED_DATE = "RequestedDate";

    // CSV delimiter
    public static final char DELIM = ';';

    // Expand field candidates for transaction data
    public static final String[] EXPAND_BASE_CANDIDATES = {
        "transactions"
    };

    // Specific transaction fields to extract
    public static final String[] EXPAND_FIELDS = {
        "transactionId",
        "transactionDate",
        "settlementDate",
        "transactionType",
        "transactionStatus",
        "quantity",
        "quantityUnit",
        "cashAmount",
        "cashSettlementCurrency",
        "grossSettlementPrice",
        "receivingPartyType",
        "deliveringPartyType"
    };

    // Date range for processing
    public static final LocalDate START_DATE = LocalDate.of(2024, 6, 30);
    public static final LocalDate END_DATE = LocalDate.of(2025, 7,1);

    // public constructor to prevent instantiation
    public Constants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
