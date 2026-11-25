package com.fraudshield.fraud;

import com.fraudshield.exception.ModelServiceUnavailableException;
import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote AI Scorer calling the Python FastAPI ML/DL scoring microservice.
 * Enforces a bounded SLA timeout so checkout degrades gracefully if offline.
 */
public class RemoteModelScorer implements FraudScorer {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    private final long timeoutMs;

    public RemoteModelScorer(String serviceUrl) {
        this(serviceUrl, Long.parseLong(System.getProperty("model.timeout.ms", "500")));
    }

    public RemoteModelScorer(String serviceUrl, long timeoutMs) {
        this.serviceUrl = serviceUrl;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.gson = new Gson();
    }

    @Override
    public ScoreResult score(Order order) throws ModelServiceUnavailableException {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("order_id", order.getId());
            payload.put("customer_id", order.getCustomerId());
            payload.put("amount", order.getTotalAmount());
            payload.put("ip_address", order.getIpAddress());
            payload.put("device_fingerprint", order.getDeviceFingerprint());
            payload.put("latitude", order.getShippingLatitude());
            payload.put("longitude", order.getShippingLongitude());
            payload.put("recent_tx_count_1h", order.getRecentTxCount1h());

            String requestBody = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/score"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ModelServiceUnavailableException("Python model service responded with HTTP " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            double riskScore = json.get("risk_score").getAsDouble();
            String decStr = json.get("decision").getAsString();
            Decision decision = Decision.valueOf(decStr.toUpperCase());

            List<String> reasons = new ArrayList<>();
            if (json.has("reasons") && json.get("reasons").isJsonArray()) {
                json.getAsJsonArray("reasons").forEach(r -> reasons.add(r.getAsString()));
            }

            double latencyMs = json.has("latency_ms") ? json.get("latency_ms").getAsDouble() : 0.0;
            Map<String, Object> breakdown = Collections.emptyMap();
            if (json.has("engine_breakdown") && json.get("engine_breakdown").isJsonObject()) {
                breakdown = gson.fromJson(json.get("engine_breakdown"), MAP_TYPE);
            }

            return new ScoreResult(riskScore, decision, reasons, false, latencyMs, breakdown);

        } catch (Exception e) {
            throw new ModelServiceUnavailableException("Failed to contact Python model service: " + e.getMessage(), e);
        }
    }
}
