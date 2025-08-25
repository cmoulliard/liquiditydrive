package com.euroclear.util;

public class ApiConfig {
    public static final String CLIENT_ID = "2f09fb7d-10c0-441e-83e7-b10b821c0972";
    public static final String APPLICATION_ID = "2a68d266-61e0-4c07-8edb-c331004b3dd7";
    public static final String CERTIFICATE_FILE_NAME = "/Users/cmoullia/code/vscode/euroclear/conf/Euroclear_ExportPriv.pfx";
    public static final String CERTIFICATE_PASSWORD = "1203472740Aa$";
    public static final String API_KEY = "e9ce4959f79c410ea68542d7985a7788";
    public static final String AUTHORITY = "https://login.microsoftonline.com/d33d1ba6-ea24-46f5-a96b-0fcd7063ba95";
    public static final String LIQUIDITY_DRIVE_ADDRESS = "https://liquiditydrive.eis.euroclear.com";

    // Per-ISIN endpoint format
    public static final String SINGLE_ENDPOINT_FMT = "/liquidity/v1/securities/%s?referenceDate=%s";
}
