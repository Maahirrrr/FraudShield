package com.fraudshield.service;

import com.fraudshield.model.Order;
import com.fraudshield.model.Transaction;
import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe persistence and analysis exporter.
 * Writes orders to append-only JSONL and exports analysis_export.csv for Data Science notebooks.
 */
public class FileStore {
    private static final Logger LOGGER = Logger.getLogger(FileStore.class.getName());
    private final String logFilePath;
    private final String exportCsvPath;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final Gson gson = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, (com.google.gson.JsonSerializer<java.time.Instant>)
                    (src, typeOfSrc, context) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    public FileStore(String logFilePath, String exportCsvPath) {
        this.logFilePath = logFilePath;
        this.exportCsvPath = exportCsvPath;
        initializeFiles();
    }

    private void initializeFiles() {
        try {
            File logFile = new File(logFilePath);
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            File csvFile = new File(exportCsvPath);
            if (!csvFile.exists()) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, true))) {
                    pw.println("transaction_id,order_id,merchant_id,amount,payment_method,risk_score,decision,fallback_used,timestamp");
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize storage files: " + e.getMessage(), e);
        }
    }

    public void saveTransaction(Order order, Transaction tx) {
        fileLock.lock();
        try {
            // 1. Append JSONL log
            try (FileWriter fw = new FileWriter(logFilePath, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.println(gson.toJson(tx));
            }

            // 2. Append CSV analysis export
            try (FileWriter fw = new FileWriter(exportCsvPath, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.printf("%s,%s,%s,%.2f,%s,%.4f,%s,%b,%s%n",
                        tx.getId(),
                        tx.getOrderId(),
                        tx.getMerchantId(),
                        tx.getAmount(),
                        tx.getPaymentMethod(),
                        tx.getRiskScore(),
                        tx.getDecision().name(),
                        tx.isFallbackUsed(),
                        DateTimeFormatter.ISO_INSTANT.format(tx.getTimestamp()));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error saving transaction to disk: " + e.getMessage(), e);
        } finally {
            fileLock.unlock();
        }
    }
}
