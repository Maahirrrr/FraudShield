package com.fraudshield.fraud;

import com.fraudshield.exception.ModelServiceUnavailableException;
import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote AI Scorer that calls the Python FastAPI model microservice.
 * Enforces a strict 300 ms SLA timeout.
 */
public class RemoteModelScorer implements FraudScorer {
    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public RemoteModelScorer(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300))
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

            String requestBody = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/score"))
                    .timeout(Duration.ofMillis(300))
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
            if (json.has("reasons")) {
                json.getAsJsonArray("reasons").forEach(r -> reasons.add(r.getAsString()));
            }

            return new ScoreResult(riskScore, decision, reasons, false);

        } catch (Exception e) {
            throw new ModelServiceUnavailableException("Failed to contact Python model service: " + e.getMessage(), e);
        }
    }
}
