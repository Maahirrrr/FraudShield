package com.fraudshield.model;

public class Merchant {
    private final String id;
    private final String name;
    private final String category;
    private final String apiKey;
    private double riskThresholdBlock;
    private double riskThresholdVerify;

    public Merchant(String id, String name, String category, String apiKey) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.apiKey = apiKey;
        this.riskThresholdBlock = 0.75;
        this.riskThresholdVerify = 0.35;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getApiKey() { return apiKey; }
    public double getRiskThresholdBlock() { return riskThresholdBlock; }
    public double getRiskThresholdVerify() { return riskThresholdVerify; }

    public void setThresholds(double verify, double block) {
        this.riskThresholdVerify = verify;
        this.riskThresholdBlock = block;
    }
}
