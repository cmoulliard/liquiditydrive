# Euroclear Liquidity Drive Client

A Java client for accessing the Euroclear Liquidity Drive API.

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
3. **Credentials & certificate**: Update the following env variables`:
   - `CLIENT_ID`: Your Azure AD application client ID
   - `APPLICATION_ID`: Your Euroclear application ID
   - `API_KEY`: Azure Subscription key
   - `CERTIFICATE_FILE_NAME`: Path to the Euroclear certificate file
   - `CERTIFICATE_PASSWORD`: Password of the  certificate
   - `AUTHORITY`: Microsoft Azure Authority server - https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration
   - `JAVA_TRUST_STORE`: Path of the java certificate trustore

4. **Euroclean LiquiDrive Application**: 
   - `LIQUIDITY_DRIVE_ADDRESS`: https://liquiditydrive.eis.euroclear.com`
   - `SLEEP_TIME_MS`: 1000 milliseconds. Sleep time before executing new HTTP requests

   - `START_DATE`: Start date to collect securities' data. Format is "yyyy-mm-dd"
   - `END_DATE`: End date to collect securities' data. Format is "yyyy-mm-dd"
   - `ISINS`: List of ISIN codes separated by comma
   - 
## Usage

```bash
# Set the env variables (PowerShell)
[Environment]::SetEnvironmentVariable('Foo','Bar')
# Trick: to see the env var `$env:Foo`

# Set the env variables for mac, linux users using .net file

# Compile the project
mvn clean compile

# Run the application
mvn exec:java
```

## Output

The application generates the CSV files in the `out/` directory.