package dev.snowdrop;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates how to create secure connections with a custom SSL
 * context.
 */
public class ClientCustomSSL {

    public final static void main(final String[] args) throws Exception {
        // Trust standard CA and those trusted by our custom strategy
        final SSLContext sslContext = SSLContexts.custom()
            // Custom TrustStrategy implementations are intended for verification
            // of certificates whose CA is not trusted by the system, and where specifying
            // a custom truststore containing the certificate chain is not an option.
            .loadTrustMaterial((chain, authType) -> {
                // Please note that validation of the server certificate without validation
                // of the entire certificate chain in this example is preferred to completely
                // disabling trust verification, however this still potentially allows
                // for man-in-the-middle attacks.
                final X509Certificate cert = chain[0];
                return "CN=httpbin.org".equalsIgnoreCase(cert.getSubjectDN().getName());
            })
            .build();
        final TlsSocketStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext);
        // Allow TLSv1.3 protocol only
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(tlsStrategy)
            .setDefaultTlsConfig(TlsConfig.custom()
                .setHandshakeTimeout(Timeout.ofSeconds(30))
                .setSupportedProtocols(TLS.V_1_3)
                .build())
            .build();
        try (CloseableHttpClient httpclient = HttpClients.custom()
            .setConnectionManager(cm)
            .build()) {

            final HttpGet httpget = new HttpGet("https://httpbin.org/");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            final HttpClientContext clientContext = HttpClientContext.create();
            httpclient.execute(httpget, clientContext, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                final SSLSession sslSession = clientContext.getSSLSession();
                if (sslSession != null) {
                    System.out.println("SSL protocol " + sslSession.getProtocol());
                    System.out.println("SSL cipher suite " + sslSession.getCipherSuite());
                }
                return null;
            });
        }
    }

}