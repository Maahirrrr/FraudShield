-- FraudShield Production Analytics Schema (PostgreSQL / ClickHouse compatible)
-- Implements partitioning by day and indices on risk scores and merchant IDs

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    risk_score NUMERIC(5, 4) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    fallback_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (created_at);

-- Analytical Indices
CREATE INDEX IF NOT EXISTS idx_tx_merchant_created ON transactions(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_risk_score ON transactions(risk_score) WHERE decision = 'BLOCK';

-- Daily Rollup Materialized View for Merchant Dashboard
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_fraud_summary AS
SELECT 
    DATE_TRUNC('day', created_at) AS tx_date,
    merchant_id,
    COUNT(*) AS total_transactions,
    SUM(CASE WHEN decision = 'BLOCK' THEN 1 ELSE 0 END) AS blocked_count,
    SUM(CASE WHEN decision = 'APPROVE' THEN 1 ELSE 0 END) AS approved_count,
    SUM(CASE WHEN decision = 'VERIFY' THEN 1 ELSE 0 END) AS verify_count,
    ROUND(SUM(CASE WHEN decision = 'BLOCK' THEN amount ELSE 0 END), 2) AS estimated_loss_prevented,
    ROUND(AVG(risk_score), 4) AS avg_risk_score
FROM transactions
GROUP BY 1, 2;
