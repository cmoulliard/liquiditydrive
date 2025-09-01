package com.euroclear.util;

import com.euroclear.LiquidityDriveNewClient;
import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.ITenantProfile;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A private static inner class that provides a mock implementation of the
 * IAuthenticationResult interface. It returns hardcoded or generated test data.
 */
public class SimpleAuthentication implements IAuthenticationResult {
    private static final Logger logger = Logger.getLogger(SimpleAuthentication.class);

    private final String accessToken;
    private final Date expiresOnDate;

    public SimpleAuthentication(Long timeOut) {
        this.accessToken = "dummy-jwt-token-" + UUID.randomUUID();;
        this.expiresOnDate = expiringDate(timeOut);
    }

    @Override
    public String accessToken() {
        return this.accessToken;
    }

    @Override
    public Date expiresOnDate() {
        return this.expiresOnDate;
    }

    // For a simple dummy, we can return null or empty values for the other methods.
    @Override
    public IAccount account() {
        return null;
    }

    @Override
    public ITenantProfile tenantProfile() {
        return null;
    }

    @Override
    public String environment() {
        return "";
    }

    @Override
    public String idToken() {
        return null;
    }

    @Override
    public String scopes() {
        return "";
    }

    private Date expiringDate(long timeOut) {
        long currentDate = System.currentTimeMillis();
        Date expiringDate = new Date(currentDate + timeOut);
        logger.infof("### Current date: %s => expiring on: %s",new Date(currentDate),expiringDate);
        return expiringDate;
    }
}
