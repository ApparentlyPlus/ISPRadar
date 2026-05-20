package com.ispradar.backend.util.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class BaseIspHttpClient {
    protected final Logger LOG = LoggerFactory.getLogger(getClass());
    protected final HttpClient httpClient;
    protected final CookieManager cookieManager;
    protected final ObjectMapper objectMapper;
    
    private final Lock initLock = new ReentrantLock();
    private volatile boolean initialized = false;

    protected BaseIspHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    protected void ensureInitialized() throws IOException, InterruptedException {
        if (initialized) return;
        initLock.lock();
        try {
            if (initialized) return;
            LOG.info("[{}] Initialising session cookies...", getProviderName());
            initializeSession();
            initialized = true;
        } finally {
            initLock.unlock();
        }
    }

    protected void resetSession() {
        cookieManager.getCookieStore().removeAll();
        initialized = false;
    }

    protected HttpResponse<String> executeWithRetry(HttpRequest request) throws IOException, InterruptedException {
        ensureInitialized();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (HttpStatusCode.requiresSessionReset(response.statusCode())) {
            LOG.warn("[{}] Session expired (HTTP {}). Resetting and retrying...", getProviderName(), response.statusCode());
            resetSession();
            ensureInitialized();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        if (!HttpStatusCode.isSuccess(response.statusCode()) && response.statusCode() != HttpStatusCode.NOT_FOUND.code()) {
            throw new IOException(String.format("[%s] API error: HTTP %d for %s", 
                    getProviderName(), response.statusCode(), request.uri()));
        }
        return response;
    }

    protected abstract HttpRequest buildInit();
    protected abstract HttpRequest buildGet(String url);
    protected abstract HttpRequest buildPost(String url, String jsonPayload);

    protected abstract void initializeSession() throws IOException, InterruptedException;
    protected abstract String getProviderName();
}