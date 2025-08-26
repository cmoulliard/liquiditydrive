# Euroclear Liquidity Drive Client

A Java client for accessing the Euroclear Liquidity Drive API, converted from the original C# implementation.

## Features

- **Certificate-based OAuth authentication** using MSAL4J with PKCS12 certificates
- **HTTP client** with Apache HttpClient 5 for API requests
- **JSON processing** with Jackson for parsing API responses
- **CSV output generation** with proper field escaping
- **Concurrent processing** using Java CompletableFuture and ExecutorService
- **Error handling** for authentication failures and API errors
- **Organized constants** in dedicated utility classes

## Requirements

- Java 17+
- Maven 3.6+
- Valid Euroclear API credentials and certificate

## Configuration

Before running the application, you need to configure:

1. **Certificate**: Place your PKCS12 certificate file (`.pfx`) in the `conf/` directory
2. **Credentials**: Update the following constants in `LiquidityDriveClient.java`:
   - `CLIENT_ID`: Your Azure AD application client ID
   - `APPLICATION_ID`: Your Euroclear application ID
   - `CERTIFICATE_FILE_NAME`: Path to your certificate file
   - `CERTIFICATE_PASSWORD`: Password for your certificate

## Usage

```bash
# Compile the project
mvn clean compile

# Run the application
mvn exec:java

# Run tests
mvn test
```

## Architecture

### Key Components

- **`LiquidityDriveClient`**: Main application class with OAuth authentication and API processing
- **`LiquidityDriveConstants`**: Utility class containing ISIN lists, field mappings, and date ranges
- **Certificate Authentication**: Uses MSAL4J with PKCS12 certificates for secure API access
- **Concurrent Processing**: Parallel execution of API calls for improved performance

### Conversion from C#

This Java implementation mirrors the original C# program structure:

- **Authentication**: Converted from .NET MSAL to MSAL4J with certificate-based authentication
- **HTTP Client**: Migrated from .NET HttpClient to Apache HttpClient 5
- **JSON Processing**: Replaced Newtonsoft.Json with Jackson
- **Concurrency**: Converted from C# Tasks to Java CompletableFuture
- **Error Handling**: Maintained equivalent error handling patterns
- **CSV Output**: Preserved CSV generation with proper field escaping

### Dependencies

- **MSAL4J**: Microsoft Authentication Library for OAuth
- **Apache HttpClient 5**: HTTP communication
- **Jackson**: JSON processing and data binding
- **SLF4J + Logback**: Logging framework
- **JUnit 5**: Testing framework

## Output

The application generates CSV files in the `output/` directory with liquidity drive data for the configured ISINs and date ranges.

## Notes

- The application currently uses basic SSL configuration for development
- For production use, implement proper certificate validation and SSL configuration
- Ensure your certificate and credentials are properly secured
- The application processes multiple ISINs concurrently for improved performance