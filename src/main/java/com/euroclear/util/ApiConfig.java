package com.euroclear.util;

import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ApiConfig {
    private static final Logger logger = Logger.getLogger(ApiConfig.class);

    public static String CLIENT_ID;
    public static String APPLICATION_ID;
    public static String CERTIFICATE_FILE_NAME;
    public static String JAVA_TRUST_STORE;
    public static String CERTIFICATE_PASSWORD;
    public static String API_KEY;
    public static String AUTHORITY;
    public static String LIQUIDITY_DRIVE_ADDRESS;

    // Per-ISIN endpoint format
    public static final String SINGLE_ENDPOINT_FMT = "/liquidity/v1/securities/%s?referenceDate=%s";

    // Date range for processing
    public static LocalDate START_DATE;
    public static LocalDate END_DATE;
    static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Sleep time to wait before to execute a new HTTP request
    public static Long SLEEP_TIME_MS;

    // Token Euroclear expiration time
    public static Long TOKEN_EXPIRATION_SECOND;

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
        LIQUIDITY_DRIVE_ADDRESS = getEnvOrExit("LIQUIDITY_DRIVE_ADDRESS");
        JAVA_TRUST_STORE = getEnvOrExit("JAVA_TRUST_STORE");

        // Range of dates
        START_DATE = LocalDate.parse(getEnvOrExit("START_DATE"), DATE_FORMAT);
        END_DATE = LocalDate.parse(getEnvOrExit("END_DATE"), DATE_FORMAT);

        // Sleep Time
        SLEEP_TIME_MS = Optional
            .ofNullable(System.getenv("SLEEP_TIME_MS"))
            .map(s -> Long.parseLong(s))
            .orElse(1000L);

        // Token Euroclear expiration time. Default: 10 min - 600s - 600000ms
        TOKEN_EXPIRATION_SECOND = Optional
            .ofNullable(System.getenv("TOKEN_EXPIRATION_SECOND"))
            .map(s -> Long.parseLong(s) * 1000)
            .orElse(600000L);
    }

    public static String getEnvOrExit(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            logger.errorf("Required environment variable '%s' is not set. Exiting.", name);
            System.exit(1);
        }
        return value;
    }
}
