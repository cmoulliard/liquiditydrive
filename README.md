# Euroclear Liquidity Drive Client

A Java client for accessing the Euroclear Liquidity Drive API.

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

1. **Certificate**: Place the Euroclear certificate file (`Euroclear_ExportPriv.pfx`) in the `conf/` directory
2. **Java keystore**: Create a java keystore from the euroclear root ca certificate (optional)
```shell
openssl s_client -showcerts -connect liquiditydrive.eis.euroclear.com:443 </dev/null \
        2>/dev/null | awk '/-----BEGIN CERTIFICATE-----/{flag=1;file="conf/euroclear-root-ca.crt"} flag{print > file} /-----END CERTIFICATE-----/{flag=0}'
openssl x509 -in conf/euroclear-root-ca.crt -text -noout

keytool -importcert -alias "euroclear_root_ca" -file "conf/euroclear-root-ca.crt" -keystore "conf/euroclear-truststore.jks" -storepass "xxxx"
```
2. **Credentials**: Update the following env variables`:
   - `CLIENT_ID`: Your Azure AD application client ID
   - `APPLICATION_ID`: Your Euroclear application ID
   - `API_KEY`: Azure Subscription key
   - `CERTIFICATE_FILE_NAME`: Path to the Euroclear certificate file
   - `CERTIFICATE_PASSWORD`: Password of the  certificate
   - `AUTHORITY`: Microsoft Azure Authority server - https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration

## Usage

```bash
# Set the needed env variables

# Compile the project
mvn clean compile

# Run the application
exec:java -Dexec.mainClass="com.euroclear.LiquidityDriveNewClient"
```

## Output

The application generates the CSV files in the `out/` directory.