package com.euroclear.util;

import com.euroclear.LiquidityDriveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class ApiConfig {
    private static final Logger logger = LoggerFactory.getLogger(ApiConfig.class);

    public static String CLIENT_ID;
    public static String APPLICATION_ID;
    public static String CERTIFICATE_FILE_NAME;
    public static String CERTIFICATE_PASSWORD;
    public static String API_KEY;
    public static String AUTHORITY;
    public static String LIQUIDITY_DRIVE_ADDRESS;

    // Per-ISIN endpoint format
    public static final String SINGLE_ENDPOINT_FMT = "/liquidity/v1/securities/%s?referenceDate=%s";

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
    public static final LocalDate START_DATE = LocalDate.of(2023, 6,29);
    public static final LocalDate END_DATE = LocalDate.of(2024, 7,2);

    public ApiConfig() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void loadEnvironmentVariables() {
        CLIENT_ID = getEnvOrExit("CLIENT_ID");
        APPLICATION_ID = getEnvOrExit("APPLICATION_ID");
        CERTIFICATE_PASSWORD = getEnvOrExit("CERTIFICATE_PASSWORD");
        API_KEY = getEnvOrExit("API_KEY");
        AUTHORITY = getEnvOrExit("AUTHORITY");
        CERTIFICATE_FILE_NAME = getEnvOrExit("CERTIFICATE_FILE_NAME");
    }

    public static String getEnvOrExit(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            logger.error("Required environment variable '{}' is not set. Exiting.", name);
            System.exit(1);
        }
        return value;
    }
}
