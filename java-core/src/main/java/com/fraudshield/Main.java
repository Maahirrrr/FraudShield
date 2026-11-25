package com.fraudshield;

import com.fraudshield.api.FraudShieldServer;
import com.fraudshield.fraud.FallbackScorer;
import com.fraudshield.fraud.RemoteModelScorer;
import com.fraudshield.fraud.RuleBasedScorer;
import com.fraudshield.service.FileStore;
import com.fraudshield.service.InventoryService;
import com.fraudshield.service.OrderService;
import com.fraudshield.service.PaymentService;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("==================================================");
            System.out.println("          STARTING FRAUDSHIELD JAVA CORE          ");
            System.out.println("==================================================");

            // 1. Initialize Inventory (synced across Store UI & REST API)
            InventoryService inventoryService = new InventoryService();
            inventoryService.resetDemoCatalog();

            // 2. Initialize Payment & Thread-safe Persistence
            PaymentService paymentService = new PaymentService();
            String baseDataDir = new File("../data").getAbsolutePath();
            FileStore fileStore = new FileStore(
                    baseDataDir + "/transactions.jsonl",
                    baseDataDir + "/analysis_export.csv"
            );

            // 3. Initialize Fraud Scorers & Resilient Fallback Engine
            String pythonServiceUrl = System.getProperty("model.service.url", "http://localhost:8000");
            RemoteModelScorer remoteScorer = new RemoteModelScorer(pythonServiceUrl);
            RuleBasedScorer ruleBasedScorer = new RuleBasedScorer();
            FallbackScorer fallbackScorer = new FallbackScorer(remoteScorer, ruleBasedScorer);

            // 4. Initialize Core Order Service (Multithreaded BlockingQueue + Velocity Tracker)
            OrderService orderService = new OrderService(inventoryService, paymentService, fallbackScorer, fileStore);

            // 5. Start REST HTTP Server
            int port = Integer.parseInt(System.getProperty("server.port", "8085"));
            FraudShieldServer server = new FraudShieldServer(
                    port, orderService, inventoryService, fallbackScorer, fileStore);
            server.start();

            System.out.println("[+] HTTP REST Server listening on http://localhost:" + port);
            System.out.println("[i] Endpoints: /api/orders, /api/orders/recent, /api/orders/verify, /api/metrics, /api/inventory, /api/override, /api/data/erase, /api/status");
            System.out.println("==================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[!] Shutting down FraudShield gracefully...");
                server.stop();
                orderService.shutdown();
            }));

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
