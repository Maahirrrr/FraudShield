package com.fraudshield.api;

import com.fraudshield.exception.*;
import com.fraudshield.fraud.FallbackScorer;
import com.fraudshield.fraud.RemoteModelScorer;
import com.fraudshield.fraud.RuleBasedScorer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Embedded HTTP server exposing FraudShield REST endpoints.
 * Operates without external web frameworks to show clean Java core OOP and concurrency.
 */
public class FraudShieldServer {
    private static final Logger LOGGER = Logger.getLogger(FraudShieldServer.class.getName());
    private final int port;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final FallbackScorer fallbackScorer;
    private final Gson gson = new Gson();
    private HttpServer server;

    public FraudShieldServer(int port, OrderService orderService, InventoryService inventoryService, FallbackScorer fallbackScorer) {
        this.port = port;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.fallbackScorer = fallbackScorer;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoints — registration order matters for prefix matching
        server.createContext("/api/orders/recent", new RecentOrdersHandler());
        server.createContext("/api/orders", new OrdersHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/inventory", new InventoryHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/override", new OverrideHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
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

                String orderId = json.has("order_id") ? json.get("order_id").getAsString() : "ORD_" + UUID.randomUUID().toString().substring(0, 8);
                String merchantId = json.has("merchant_id") ? json.get("merchant_id").getAsString() : "MERCHANT_D2C_101";
                String customerId = json.has("customer_id") ? json.get("customer_id").getAsString() : "CUST_99";
                double amount = json.has("amount") ? json.get("amount").getAsDouble() : 1500.0;
                String paymentMethod = json.has("payment_method") ? json.get("payment_method").getAsString() : "CARD";

                String productId = json.has("product_id") ? json.get("product_id").getAsString() : "PROD_DEFAULT";
                int quantity = json.has("quantity") ? json.get("quantity").getAsInt() : 1;

                List<OrderItem> items = List.of(new OrderItem(productId, quantity, amount / quantity));
                Order order = new Order(
                        orderId, merchantId, customerId, items, amount,
                        "192.168.1.100", "FP_WEB_CLIENT", 19.0760, 72.8777
                );

                // Build Payment based on method
                Payment payment;
                if ("UPI".equalsIgnoreCase(paymentMethod)) {
                    payment = new UpiPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "customer@okaxis");
                } else if ("COD".equalsIgnoreCase(paymentMethod)) {
                    payment = new CodPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "+919876543210");
                } else {
                    payment = new CardPayment("PAY_" + UUID.randomUUID().toString().substring(0, 6), "tok_visa_4242", "4242");
                }

                Transaction tx = orderService.processOrder(order, payment);

                Map<String, Object> resp = new HashMap<>();
                resp.put("status", "SUCCESS");
                resp.put("transaction_id", tx.getId());
                resp.put("order_id", tx.getOrderId());
                resp.put("risk_score", tx.getRiskScore());
                resp.put("decision", tx.getDecision().name());
                resp.put("reasons", tx.getReasons());
                resp.put("fallback_used", tx.isFallbackUsed());
                resp.put("order_status", order.getStatus());

                sendJsonResponse(exchange, 200, resp);

            } catch (FraudBlockedException e) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("status", "BLOCKED");
                resp.put("decision", "BLOCK");
                resp.put("risk_score", e.getRiskScore());
                resp.put("reasons", e.getReasons());
                resp.put("message", e.getMessage());
                sendJsonResponse(exchange, 403, resp);

            } catch (OutOfStockException e) {
                sendJsonResponse(exchange, 409, Map.of("status", "ERROR", "error", e.getMessage()));
            } catch (PaymentFailedException e) {
                sendJsonResponse(exchange, 402, Map.of("status", "PAYMENT_FAILED", "error", e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("status", "ERROR", "error", e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            Map<String, Object> status = new HashMap<>();
            status.put("service", "FraudShield Java Core");
            status.put("version", "1.0.0");
            status.put("uptime", "HEALTHY");
            status.put("fallback_active", fallbackScorer.isLastCallUsedFallback());
            status.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, status);
        }
    }

    private class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            Product p = inventoryService.getProduct("PROD_SHIRT");
            Map<String, Object> inv = new HashMap<>();
            inv.put("product_id", p != null ? p.getId() : "PROD_SHIRT");
            inv.put("product_name", p != null ? p.getName() : "Heritage Linen Shirt");
            inv.put("stock_available", p != null ? p.getStock() : 0);
            sendJsonResponse(exchange, 200, inv);
        }
    }

    private static final String BASE = "/home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection";

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
            try {
                Path txPath = Paths.get(BASE + "/data/transactions.jsonl");
                if (!Files.exists(txPath)) {
                    sendJsonResponse(exchange, 200, Collections.emptyList());
                    return;
                }
                List<String> lines = Files.readAllLines(txPath, StandardCharsets.UTF_8);
                List<JsonObject> objects = new ArrayList<>();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        objects.add(JsonParser.parseString(trimmed).getAsJsonObject());
                    }
                }
                Collections.reverse(objects);
                List<JsonObject> recent = objects.stream().limit(20).collect(Collectors.toList());
                sendJsonResponse(exchange, 200, recent);
            } catch (IOException e) {
                sendJsonResponse(exchange, 200, Collections.emptyList());
            }
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
            try {
                Path txPath = Paths.get(BASE + "/data/transactions.jsonl");
                long total = 0, blocked = 0, approved = 0, verified = 0, fallbackCount = 0;
                if (Files.exists(txPath)) {
                    List<String> lines = Files.readAllLines(txPath, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        total++;
                        try {
                            JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                            if (obj.has("decision")) {
                                String decision = obj.get("decision").getAsString();
                                if ("BLOCK".equalsIgnoreCase(decision)) blocked++;
                                else if ("APPROVE".equalsIgnoreCase(decision)) approved++;
                                else if ("VERIFY".equalsIgnoreCase(decision)) verified++;
                            }
                            if (obj.has("fallback_used") && obj.get("fallback_used").getAsBoolean()) {
                                fallbackCount++;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                String fallbackRate = total > 0
                        ? String.format("%.1f%%", (fallbackCount * 100.0 / total))
                        : "0.0%";
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("total_scored", total);
                metrics.put("fraud_blocked", blocked);
                metrics.put("approved", approved);
                metrics.put("verified", verified);
                metrics.put("money_saved_estimate", blocked * 2500);
                metrics.put("fallback_rate", fallbackRate);
                sendJsonResponse(exchange, 200, metrics);
            } catch (IOException e) {
                sendJsonResponse(exchange, 500, Map.of("error", "Failed to read metrics"));
            }
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
                String transactionId = json.has("transaction_id") ? json.get("transaction_id").getAsString() : "";
                String merchantDecision = json.has("merchant_decision") ? json.get("merchant_decision").getAsString() : "";
                String reason = json.has("reason") ? json.get("reason").getAsString() : "";
                long loggedAt = System.currentTimeMillis();

                JsonObject record = new JsonObject();
                record.addProperty("transaction_id", transactionId);
                record.addProperty("merchant_decision", merchantDecision);
                record.addProperty("reason", reason);
                record.addProperty("logged_at", loggedAt);

                Path overridePath = Paths.get(BASE + "/data/overrides.jsonl");
                Files.createDirectories(overridePath.getParent());
                Files.writeString(overridePath, gson.toJson(record) + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "LOGGED");
                resp.put("transaction_id", transactionId);
                resp.put("merchant_decision", merchantDecision);
                resp.put("logged_at", loggedAt);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error"));
            }
        }
    }

    private void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        // Security hardening headers (Hunt Host Header & Web Security Guardrails)
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
        exchange.getResponseHeaders().set("X-XSS-Protection", "1; mode=block");
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
