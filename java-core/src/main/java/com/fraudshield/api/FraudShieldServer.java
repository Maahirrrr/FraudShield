package com.fraudshield.api;

import com.fraudshield.exception.*;
import com.fraudshield.fraud.FallbackScorer;
import com.fraudshield.model.*;
import com.fraudshield.payment.*;
import com.fraudshield.service.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Embedded HTTP server exposing FraudShield REST endpoints without heavy frameworks.
 */
public class FraudShieldServer {
    private static final Logger LOGGER = Logger.getLogger(FraudShieldServer.class.getName());
    private final int port;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final FallbackScorer fallbackScorer;
    private final FileStore fileStore;
    private final Gson gson = new Gson();
    private HttpServer server;

    public FraudShieldServer(int port, OrderService orderService, InventoryService inventoryService, FallbackScorer fallbackScorer) {
        this(port, orderService, inventoryService, fallbackScorer,
                new FileStore("../data/transactions.jsonl", "../data/analysis_export.csv"));
    }

    public FraudShieldServer(int port, OrderService orderService, InventoryService inventoryService,
                             FallbackScorer fallbackScorer, FileStore fileStore) {
        this.port = port;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.fallbackScorer = fallbackScorer;
        this.fileStore = fileStore;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register specific sub-paths before parent prefixes
        server.createContext("/api/orders/recent", new RecentOrdersHandler());
        server.createContext("/api/orders/verify", new VerifyOtpHandler());
        server.createContext("/api/orders", new OrdersHandler());
        server.createContext("/api/inventory/restock", new RestockHandler());
        server.createContext("/api/inventory", new InventoryHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/override", new OverrideHandler());
        server.createContext("/api/data/erase", new EraseHandler());
        server.createContext("/api/status", new StatusHandler());

        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
        LOGGER.info("[+] FraudShield Java Core Server running at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String orderId = getStringOrDefault(json, "order_id",
                        "ORD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                String merchantId = getStringOrDefault(json, "merchant_id", "MERCHANT_D2C_101");
                String customerId = getStringOrDefault(json, "customer_id", "CUST_STORE_GUEST");
                double amount = json.has("amount") && !json.get("amount").isJsonNull()
                        ? json.get("amount").getAsDouble() : 0.0;
                String paymentMethod = getStringOrDefault(json, "payment_method", "CARD");
                String productId = getStringOrDefault(json, "product_id", "PROD_SHIRT");
                int quantity = json.has("quantity") && !json.get("quantity").isJsonNull()
                        ? Math.max(1, json.get("quantity").getAsInt()) : 1;

                String ipAddress = getStringOrDefault(json, "ip_address",
                        exchange.getRemoteAddress().getAddress().getHostAddress());
                String deviceFingerprint = getStringOrDefault(json, "device_fingerprint", "FP_WEB_CLIENT");

                double latitude = 19.0760;
                if (json.has("latitude") && !json.get("latitude").isJsonNull()) {
                    latitude = json.get("latitude").getAsDouble();
                } else if (json.has("lat") && !json.get("lat").isJsonNull()) {
                    latitude = json.get("lat").getAsDouble();
                }

                double longitude = 72.8777;
                if (json.has("longitude") && !json.get("longitude").isJsonNull()) {
                    longitude = json.get("longitude").getAsDouble();
                } else if (json.has("lon") && !json.get("lon").isJsonNull()) {
                    longitude = json.get("lon").getAsDouble();
                }

                int recentTx1h = json.has("recent_tx_count_1h") && !json.get("recent_tx_count_1h").isJsonNull()
                        ? Math.max(1, json.get("recent_tx_count_1h").getAsInt()) : 1;
                boolean consentGiven = !json.has("consent_given") || json.get("consent_given").getAsBoolean();

                List<OrderItem> items = List.of(new OrderItem(productId, quantity, amount / quantity));
                Order order = new Order(
                        orderId, merchantId, customerId, items, amount,
                        ipAddress, deviceFingerprint, latitude, longitude,
                        recentTx1h, consentGiven
                );

                Payment payment;
                if ("UPI".equalsIgnoreCase(paymentMethod)) {
                    payment = new UpiPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "customer@okaxis");
                } else if ("COD".equalsIgnoreCase(paymentMethod)) {
                    payment = new CodPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "+919876543210");
                } else {
                    payment = new CardPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "tok_visa_4242", "4242");
                }

                Transaction tx = orderService.processOrder(order, payment);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "SUCCESS");
                resp.put("transaction_id", tx.getId());
                resp.put("order_id", tx.getOrderId());
                resp.put("risk_score", tx.getRiskScore());
                resp.put("decision", tx.getDecision().name());
                resp.put("reasons", tx.getReasons());
                resp.put("fallback_used", tx.isFallbackUsed());
                resp.put("latency_ms", tx.getLatencyMs());
                resp.put("engine_breakdown", tx.getEngineBreakdown());
                resp.put("order_status", order.getStatus());

                sendJsonResponse(exchange, 200, resp);

            } catch (FraudBlockedException e) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "BLOCKED");
                resp.put("transaction_id", e.getTransactionId());
                resp.put("order_id", e.getOrderId());
                resp.put("decision", "BLOCK");
                resp.put("risk_score", e.getRiskScore());
                resp.put("reasons", e.getReasons());
                resp.put("fallback_used", e.isFallbackUsed());
                resp.put("latency_ms", e.getLatencyMs());
                resp.put("engine_breakdown", e.getEngineBreakdown());
                resp.put("message", e.getMessage());
                sendJsonResponse(exchange, 403, resp);

            } catch (InvalidInputException e) {
                sendJsonResponse(exchange, 400, Map.of("status", "INVALID_INPUT", "error", e.getMessage()));
            } catch (OutOfStockException e) {
                sendJsonResponse(exchange, 409, Map.of("status", "OUT_OF_STOCK", "error", e.getMessage()));
            } catch (PaymentFailedException e) {
                sendJsonResponse(exchange, 402, Map.of("status", "PAYMENT_FAILED", "error", e.getMessage()));
            } catch (Exception e) {
                LOGGER.warning("Order error: " + e.getMessage());
                sendJsonResponse(exchange, 500, Map.of("status", "ERROR", "error",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
            }
        }
    }

    private class VerifyOtpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String txId = getStringOrDefault(json, "transaction_id", "");
                String otpCode = getStringOrDefault(json, "otp", "").replaceAll("[^0-9]", "");

                if (txId.isBlank() || otpCode.length() != 6) {
                    sendJsonResponse(exchange, 400, Map.of("status", "INVALID_OTP", "error", "Valid 6-digit OTP and transaction_id required"));
                    return;
                }

                boolean updated = fileStore.updateTransactionDecision(txId, "APPROVE", "Step-up 2FA OTP verified");
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", updated ? "VERIFIED" : "NOT_FOUND");
                resp.put("transaction_id", txId);
                resp.put("decision", "APPROVE");
                resp.put("verified_at", System.currentTimeMillis());
                sendJsonResponse(exchange, updated ? 200 : 404, resp);
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, Map.of("error", "Malformed verification payload"));
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("service", "FraudShield Java Core");
            status.put("version", "2.0.0");
            status.put("uptime", "HEALTHY");
            status.put("fallback_active", fallbackScorer.isLastCallUsedFallback());
            status.put("p95_latency_ms", orderService.getP95LatencyMs());
            status.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, status);
        }
    }

    private class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            Collection<Product> allProducts = inventoryService.getAllProducts();
            List<Map<String, Object>> productList = new ArrayList<>();
            for (Product p : allProducts) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("product_id", p.getId());
                item.put("name", p.getName());
                item.put("price", p.getPrice());
                item.put("stock_available", p.getStock());
                productList.add(item);
            }
            Product shirt = inventoryService.getProduct("PROD_SHIRT");
            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("products", productList);
            // Backward compatibility fields
            inv.put("product_id", shirt != null ? shirt.getId() : "PROD_SHIRT");
            inv.put("product_name", shirt != null ? shirt.getName() : "Heritage Linen Shirt");
            inv.put("stock_available", shirt != null ? shirt.getStock() : 0);
            sendJsonResponse(exchange, 200, inv);
        }
    }

    private class RestockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            inventoryService.resetDemoCatalog();
            sendJsonResponse(exchange, 200, Map.of("status", "RESTOCKED", "message", "Demo catalog inventory reset to baseline."));
        }
    }

    private class RecentOrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            List<JsonObject> recent = fileStore.loadRecentTransactions(25);
            sendJsonResponse(exchange, 200, recent);
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            List<JsonObject> all = fileStore.loadAllTransactions();
            long total = all.size();
            long blocked = 0, approved = 0, verified = 0, fallbackCount = 0;
            double moneySavedActual = 0.0;
            double totalVolume = 0.0;
            double riskSum = 0.0;
            List<Double> storedLatencies = new ArrayList<>();

            for (JsonObject obj : all) {
                String decision = obj.has("decision") ? obj.get("decision").getAsString() : "";
                double amount = obj.has("amount") && !obj.get("amount").isJsonNull() ? obj.get("amount").getAsDouble() : 0.0;
                double risk = obj.has("riskScore") && !obj.get("riskScore").isJsonNull() ? obj.get("riskScore").getAsDouble()
                        : (obj.has("risk_score") ? obj.get("risk_score").getAsDouble() : 0.0);

                totalVolume += amount;
                riskSum += risk;

                if ("BLOCK".equalsIgnoreCase(decision)) {
                    blocked++;
                    moneySavedActual += amount;
                } else if ("APPROVE".equalsIgnoreCase(decision)) {
                    approved++;
                } else if ("VERIFY".equalsIgnoreCase(decision)) {
                    verified++;
                }

                boolean fb = (obj.has("fallbackUsed") && obj.get("fallbackUsed").getAsBoolean())
                        || (obj.has("fallback_used") && obj.get("fallback_used").getAsBoolean());
                if (fb) {
                    fallbackCount++;
                }
                if (obj.has("latencyMs") && !obj.get("latencyMs").isJsonNull()) {
                    double l = obj.get("latencyMs").getAsDouble();
                    if (l > 0) storedLatencies.add(l);
                }
            }

            double p95 = orderService.getP95LatencyMs();
            if (p95 == 0.0 && !storedLatencies.isEmpty()) {
                Collections.sort(storedLatencies);
                int idx = Math.min(storedLatencies.size() - 1, (int) Math.ceil(0.95 * storedLatencies.size()) - 1);
                p95 = Math.round(storedLatencies.get(idx) * 100.0) / 100.0;
            }

            String fallbackRate = total > 0
                    ? String.format(Locale.ROOT, "%.1f%%", (fallbackCount * 100.0 / total))
                    : "0.0%";

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("total_scored", total);
            metrics.put("fraud_blocked", blocked);
            metrics.put("approved", approved);
            metrics.put("verified", verified);
            metrics.put("money_saved_inr", Math.round(moneySavedActual * 100.0) / 100.0);
            metrics.put("money_saved_estimate", Math.round(moneySavedActual));
            metrics.put("total_volume_inr", Math.round(totalVolume * 100.0) / 100.0);
            metrics.put("avg_risk_score", total > 0 ? Math.round((riskSum / total) * 10000.0) / 10000.0 : 0.0);
            metrics.put("fallback_count", fallbackCount);
            metrics.put("fallback_rate", fallbackRate);
            metrics.put("p95_latency_ms", p95);
            sendJsonResponse(exchange, 200, metrics);
        }
    }

    private class OverrideHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String transactionId = getStringOrDefault(json, "transaction_id", "");
                String merchantDecision = getStringOrDefault(json, "merchant_decision", "APPROVE");
                String reason = getStringOrDefault(json, "reason", "Merchant manual review");
                long loggedAt = System.currentTimeMillis();

                fileStore.appendOverrideRecord(transactionId, merchantDecision, reason, loggedAt);
                boolean updated = fileStore.updateTransactionDecision(transactionId, merchantDecision, "Merchant override: " + reason);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "LOGGED");
                resp.put("transaction_id", transactionId);
                resp.put("merchant_decision", merchantDecision);
                resp.put("persisted_update", updated);
                resp.put("logged_at", loggedAt);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error"));
            }
        }
    }

    private class EraseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed (use DELETE)"));
                return;
            }
            String query = exchange.getRequestURI().getRawQuery();
            String targetId = "";
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && ("order_id".equals(kv[0]) || "transaction_id".equals(kv[0]))) {
                        targetId = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (targetId.isBlank()) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing order_id or transaction_id query parameter"));
                return;
            }
            int removed = fileStore.eraseOrderData(targetId);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", removed > 0 ? "ERASED" : "NOT_FOUND");
            resp.put("target_id", targetId);
            resp.put("records_removed", removed);
            resp.put("compliance_basis", "DPDP Act 2023 Section 12");
            sendJsonResponse(exchange, 200, resp);
        }
    }

    private static String getStringOrDefault(JsonObject json, String key, String def) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            String val = json.get(key).getAsString();
            if (!val.isBlank()) return val;
        }
        return def;
    }

    private void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
