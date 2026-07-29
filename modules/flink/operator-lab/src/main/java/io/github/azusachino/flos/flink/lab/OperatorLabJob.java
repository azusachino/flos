package io.github.azusachino.flos.flink.lab;

import io.github.azusachino.flos.flink.operators.NormalizePurchase;
import io.github.azusachino.flos.flink.operators.PurchaseEvent;
import io.github.azusachino.flos.flink.operators.RunningSpend;
import io.github.azusachino.flos.flink.operators.ValidPurchaseFilter;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class OperatorLabJob {

    private static final String JOB_NAME = "flos-flink-operator-lab";

    private OperatorLabJob() {}

    public static void main(String[] args) throws Exception {
        var environment = StreamExecutionEnvironment.getExecutionEnvironment();

        environment
                .fromData(
                        purchase(" Alice ", "Flink Book", "12.50", "2026-01-01T00:00:00Z"),
                        purchase("alice", "Streaming Course", "20.00", "2026-01-01T00:01:00Z"),
                        purchase("bob", "Notebook", "8.00", "2026-01-01T00:02:00Z"),
                        purchase("bob", "Invalid", "0", "2026-01-01T00:03:00Z"))
                .name("purchase-source")
                .filter(new ValidPurchaseFilter())
                .name("valid-purchases")
                .map(new NormalizePurchase())
                .name("normalize-purchases")
                .keyBy(PurchaseEvent::customerId)
                .reduce(new RunningSpend())
                .name("running-customer-spend")
                .print()
                .name("tutorial-output");

        environment.execute(JOB_NAME);
    }

    private static PurchaseEvent purchase(
            String customerId, String product, String amount, String occurredAt) {
        return new PurchaseEvent(
                customerId, product, new BigDecimal(amount), Instant.parse(occurredAt));
    }
}
