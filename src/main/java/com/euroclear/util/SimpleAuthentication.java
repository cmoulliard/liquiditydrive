package com.euroclear.util;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.ITenantProfile;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A private static inner class that provides a mock implementation of the
 * IAuthenticationResult interface. It returns hardcoded or generated test data.
 */
public class SimpleAuthentication implements IAuthenticationResult {
    private final String accessToken;
    private final Date expiresOnDate;

    public SimpleAuthentication(Long timeOut) {
        this.accessToken = "dummy-jwt-token-" + UUID.randomUUID();;
        this.expiresOnDate = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeOut));;
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
}
