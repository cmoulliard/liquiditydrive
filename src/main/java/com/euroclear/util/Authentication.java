package com.euroclear.util;

import com.microsoft.aad.msal4j.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.euroclear.LiquidityDriveNewClient.isDryRun;
import static com.euroclear.util.ApiConfig.*;

public class Authentication {

    private static final Logger logger = Logger.getLogger(Authentication.class);
    private static final ThreadLocal<IAuthenticationResult> currentToken = new ThreadLocal<>();
    private static Set<String> scopes = Collections.singleton(APPLICATION_ID + "/.default");
    private static IConfidentialClientApplication app;

    public static CloseableHttpClient createHttpClient() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();

        // 1. Load the client's keystore for key material (your private key)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream keyStoreStream = new FileInputStream(CERTIFICATE_FILE_NAME)) {
            keyStore.load(keyStoreStream, CERTIFICATE_PASSWORD.toCharArray());
        }

        // 2. Load the new trust store containing the server's CA certificate
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream trustStoreStream = new FileInputStream(JAVA_TRUST_STORE)) {
            trustStore.load(trustStoreStream, "euroclear".toCharArray());
        }

        // 3. Build the SSLContext using both keystores
        SSLContext sslContext = SSLContexts.custom()
            .loadKeyMaterial(keyStore, CERTIFICATE_PASSWORD.toCharArray())
            .loadTrustMaterial(trustStore, null) // Use the new trust store
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMinutes(3)) // How long to wait to establish a connection
            .setResponseTimeout(Timeout.ofMinutes(3)) // How long to wait for a response
            .setConnectionRequestTimeout(Timeout.ofMinutes(3)) // How long to wait for a connection from the pool
            .build();

        TlsSocketStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext);
        HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(cores * 8) // Scale with thread pool
            .setMaxConnPerRoute(cores * 4) // Match thread pool size
            .setTlsSocketStrategy(tlsStrategy)
            .setDefaultTlsConfig(TlsConfig.custom()
                .setHandshakeTimeout(Timeout.ofMinutes(30))
                .setSupportedProtocols(TLS.V_1_3, TLS.V_1_2)
                .build())
            .build();

        return HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    public static void createConfidentialClientApplication() throws Exception {
        // Load PKCS12 certificate - equivalent to C# X509CertificateLoader.LoadPkcs12FromFile
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(CERTIFICATE_FILE_NAME)) {
            keyStore.load(fis, CERTIFICATE_PASSWORD.toCharArray());
        }

        // Get the private key and certificate from the keystore
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, CERTIFICATE_PASSWORD.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

        // Create MSAL client with certificate - equivalent to C# .WithCertificate(certificate)
        app = ConfidentialClientApplication.builder(CLIENT_ID,
                ClientCredentialFactory.createFromCertificate(privateKey, certificate))
            .authority(AUTHORITY)
            .build();
    }

    public static String getAccessTokenForCurrentThread() {
        IAuthenticationResult token = currentToken.get();

        try {
            // Check if the token doesn't exist or is expired.
            if (token == null) {
                logger.infof("### Thread - %s, No token found. Acquiring new token...", Thread.currentThread().getName());
                IAuthenticationResult result = createAuthenticationResult();
                token = result;
                currentToken.set(result);
            } else {
                Long futureExpiredDate = token.expiresOnDate().getTime();
                Long currentTime = System.currentTimeMillis();
                if (currentTime >= (futureExpiredDate - TOKEN_EXPIRATION_SECOND)) {
                    logger.infof("### Thread - %s - acquiring a new token as renewal time is over: %s. Current time: %s", Thread.currentThread().getName(),token.expiresOnDate(),new Date(currentTime));
                    IAuthenticationResult result = createAuthenticationResult();
                    token = result;
                    currentToken.set(result);
                }
            }
            return token.accessToken();

        } catch (Exception e) {
            handleAuthenticationError(e);
            throw new RuntimeException("Failed to acquire token", e);
        }
    }

    private static IAuthenticationResult createAuthenticationResult() throws ExecutionException, InterruptedException {
        IAuthenticationResult result;
        if (!isDryRun) {
            ClientCredentialParameters parameters = ClientCredentialParameters.builder(scopes).build();
            result = app.acquireToken(parameters).get();
        } else {
            // Add 20s to the current time
            result = new SimpleAuthentication(20000L);
        }
        logger.infof("### Thread - %s, Token: %s - expiring on: %s", Thread.currentThread().getName(), result.accessToken(), result.expiresOnDate());
        return result;
    }

    /**
     * Handle authentication errors and provide graceful exit
     */
    private static void handleAuthenticationError(Exception e) {
        // Check if this is an MSAL service exception with invalid client secret
        if (e.getCause() instanceof MsalServiceException) {
            MsalServiceException msalException = (MsalServiceException) e.getCause();
            String errorCode = msalException.errorCode();
            String message = msalException.getMessage();

            // Check for specific error codes that indicate authentication issues
            if ("AADSTS7000215".equals(errorCode) ||
                (message != null && message.contains("Invalid client secret"))) {

                logger.errorf("=== AUTHENTICATION FAILURE ===");
                logger.errorf("Invalid client secret provided.");
                logger.errorf("Error Code: %s", errorCode);
                logger.errorf("Error Message: %s", message);
                logger.errorf("Please check your client secret configuration.");
                logger.errorf("Ensure you're using the client secret VALUE, not the client secret ID.");
                logger.errorf("Application will exit.");

                throw new RuntimeException("Failed to authenticate with client secret ID");
            }

            // Handle other MSAL authentication errors
            if (errorCode != null && errorCode.startsWith("AADSTS")) {
                logger.errorf("=== AZURE AD AUTHENTICATION ERROR ===");
                logger.errorf("Error Code: %s", errorCode);
                logger.errorf("Error Message: %s", message);
                logger.errorf("Please check your Azure AD configuration and credentials.");
                logger.errorf("Application will exit.");

                throw new RuntimeException("Failed to authenticate with Azure AD configuration and credentials.");
            }
        }

        // Check for other authentication-related exceptions
        if (e.getMessage() != null &&
            (e.getMessage().contains("authentication") ||
                e.getMessage().contains("unauthorized") ||
                e.getMessage().contains("invalid_client"))) {

            logger.errorf("=== AUTHENTICATION ERROR ===");
            logger.errorf("Authentication failed: %s", e.getMessage());
            logger.errorf("Please verify your credentials and configuration.");
            logger.errorf("Application will exit.");

            // Exit gracefully
            throw new RuntimeException("Failed to authenticate with your configuration and credentials.");
        }
    }
}
