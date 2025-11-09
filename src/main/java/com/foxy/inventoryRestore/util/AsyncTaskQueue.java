package com.foxy.inventoryRestore.util;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AsyncTaskQueue {

    private final Logger logger;
    private final ExecutorService executor;

    public AsyncTaskQueue(Logger logger, String threadName) {
        this.logger = Objects.requireNonNull(logger, "logger");
        String workerName = (threadName == null || threadName.isBlank())
                ? "InventoryRestore-Worker"
                : threadName;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, workerName);
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    public void execute(String description, Runnable task) {
        Objects.requireNonNull(task, "task");
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                if (description == null || description.isBlank()) {
                    logger.log(Level.SEVERE, "Async task execution failed", throwable);
                } else {
                    logger.log(Level.SEVERE, "Async task execution failed: " + description, throwable);
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}

