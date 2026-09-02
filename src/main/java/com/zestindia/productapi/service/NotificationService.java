package com.zestindia.productapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Simulates work that shouldn't block the HTTP response - e.g. sending a
 * notification, writing an audit record, calling a downstream system.
 * Runs on the "taskExecutor" pool defined in AsyncConfig instead of the
 * request-handling thread, so the caller (ProductServiceImpl) doesn't wait
 * on it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async("taskExecutor")
    public void notifyProductCreated(Integer productId, String productName) {
        simulateLatency();
        log.info("[async] Notification: product created id={} name={} (thread={})",
                productId, productName, Thread.currentThread().getName());
    }

    @Async("taskExecutor")
    public void notifyProductDeleted(Integer productId) {
        simulateLatency();
        log.info("[async] Notification: product deleted id={} (thread={})",
                productId, Thread.currentThread().getName());
    }

    private void simulateLatency() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
