package com.fraudshield.service;

import com.fraudshield.model.Order;
import com.fraudshield.model.Transaction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe persistence, CSV analysis exporter, and DPDP Section 12 erasure store.
 */
public class FileStore {
    private static final Logger LOGGER = Logger.getLogger(FileStore.class.getName());
    private static final String CSV_HEADER = "transaction_id,order_id,merchant_id,amount,payment_method,risk_score,decision,fallback_used,timestamp";

    private final String logFilePath;
    private final String exportCsvPath;
    private final String overridesLogPath;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    public FileStore(String logFilePath, String exportCsvPath) {
        this.logFilePath = logFilePath;
        this.exportCsvPath = exportCsvPath;
        File parent = new File(logFilePath).getParentFile();
        this.overridesLogPath = new File(parent, "overrides.jsonl").getAbsolutePath();
        initializeFiles();
    }

    private void initializeFiles() {
        try {
            File logFile = new File(logFilePath);
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            File csvFile = new File(exportCsvPath);
            if (!csvFile.exists()) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, StandardCharsets.UTF_8, true))) {
                    pw.println(CSV_HEADER);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize storage files: " + e.getMessage(), e);
        }
    }

    public String getLogFilePath() { return logFilePath; }
    public String getExportCsvPath() { return exportCsvPath; }
    public String getOverridesLogPath() { return overridesLogPath; }

    /**
     * Synchronously appends transaction JSONL for immediate read-after-write visibility.
     */
    public void saveTransactionJsonl(Transaction tx) {
        fileLock.lock();
        try (FileWriter fw = new FileWriter(logFilePath, StandardCharsets.UTF_8, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(gson.toJson(tx));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error saving transaction JSONL: " + e.getMessage(), e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Appends flat CSV row for offline Data Science notebooks (invoked asynchronously or directly).
     */
    public void appendCsvExport(Transaction tx) {
        fileLock.lock();
        try (FileWriter fw = new FileWriter(exportCsvPath, StandardCharsets.UTF_8, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.printf(Locale.ROOT, "%s,%s,%s,%.2f,%s,%.4f,%s,%b,%s%n",
                    tx.getId(),
                    tx.getOrderId(),
                    tx.getMerchantId(),
                    tx.getAmount(),
                    tx.getPaymentMethod(),
                    tx.getRiskScore(),
                    tx.getDecision().name(),
                    tx.isFallbackUsed(),
                    DateTimeFormatter.ISO_INSTANT.format(tx.getTimestamp()));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error writing CSV export: " + e.getMessage(), e);
        } finally {
            fileLock.unlock();
        }
    }

    public void saveTransaction(Order order, Transaction tx) {
        saveTransactionJsonl(tx);
        appendCsvExport(tx);
    }

    public List<JsonObject> loadAllTransactions() {
        fileLock.lock();
        try {
            Path path = Paths.get(logFilePath);
            if (!Files.exists(path)) {
                return Collections.emptyList();
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<JsonObject> objects = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        objects.add(JsonParser.parseString(trimmed).getAsJsonObject());
                    } catch (Exception ignored) {}
                }
            }
            return objects;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading transactions: " + e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            fileLock.unlock();
        }
    }

    public List<JsonObject> loadRecentTransactions(int limit) {
        List<JsonObject> all = new ArrayList<>(loadAllTransactions());
        Collections.reverse(all);
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public boolean updateTransactionDecision(String txId, String newDecision, String statusNote) {
        if (txId == null || txId.isBlank()) return false;
        fileLock.lock();
        try {
            Path path = Paths.get(logFilePath);
            if (!Files.exists(path)) return false;

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size());
            boolean found = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : "";
                    if (txId.equalsIgnoreCase(id)) {
                        obj.addProperty("decision", newDecision);
                        if (statusNote != null && !statusNote.isBlank()) {
                            obj.addProperty("resolutionNote", statusNote);
                        }
                        updated.add(gson.toJson(obj));
                        found = true;
                    } else {
                        updated.add(trimmed);
                    }
                } catch (Exception e) {
                    updated.add(trimmed);
                }
            }

            if (found) {
                Files.write(path, updated, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return found;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed updating transaction decision: " + e.getMessage(), e);
            return false;
        } finally {
            fileLock.unlock();
        }
    }

    public void appendOverrideRecord(String transactionId, String merchantDecision, String reason, long loggedAt) {
        fileLock.lock();
        try {
            JsonObject record = new JsonObject();
            record.addProperty("transaction_id", transactionId);
            record.addProperty("merchant_decision", merchantDecision);
            record.addProperty("reason", reason);
            record.addProperty("logged_at", loggedAt);

            Path overridePath = Paths.get(overridesLogPath);
            if (overridePath.getParent() != null) {
                Files.createDirectories(overridePath.getParent());
            }
            Files.writeString(overridePath, gson.toJson(record) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed appending override record: " + e.getMessage(), e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * DPDP Act 2023 Section 12 Right to Erasure: purges records by orderId or transactionId.
     */
    public int eraseOrderData(String targetId) {
        if (targetId == null || targetId.isBlank()) return 0;
        fileLock.lock();
        try {
            int removedCount = 0;

            // 1. Purge JSONL
            Path logPath = Paths.get(logFilePath);
            if (Files.exists(logPath)) {
                List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
                List<String> kept = new ArrayList<>();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    boolean match = false;
                    try {
                        JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                        String oid = obj.has("orderId") ? obj.get("orderId").getAsString() : "";
                        String tid = obj.has("id") ? obj.get("id").getAsString() : "";
                        if (targetId.equalsIgnoreCase(oid) || targetId.equalsIgnoreCase(tid)) {
                            match = true;
                        }
                    } catch (Exception ignored) {}
                    if (match) removedCount++;
                    else kept.add(trimmed);
                }
                Files.write(logPath, kept, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // 2. Purge CSV export
            Path csvPath = Paths.get(exportCsvPath);
            if (Files.exists(csvPath)) {
                List<String> csvLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
                List<String> keptCsv = new ArrayList<>();
                for (int i = 0; i < csvLines.size(); i++) {
                    String row = csvLines.get(i);
                    if (i == 0) {
                        keptCsv.add(row);
                        continue;
                    }
                    String[] cols = row.split(",");
                    boolean match = cols.length >= 2 &&
                            (targetId.equalsIgnoreCase(cols[0]) || targetId.equalsIgnoreCase(cols[1]));
                    if (!match) {
                        keptCsv.add(row);
                    }
                }
                Files.write(csvPath, keptCsv, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            return removedCount;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed DPDP erasure for " + targetId + ": " + e.getMessage(), e);
            return 0;
        } finally {
            fileLock.unlock();
        }
    }
}
