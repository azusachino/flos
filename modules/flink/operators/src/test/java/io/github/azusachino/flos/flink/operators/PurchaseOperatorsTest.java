package io.github.azusachino.flos.flink.operators;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class PurchaseOperatorsTest {

    private static final Instant FIRST_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SECOND_INSTANT = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void filtersInvalidPurchases() throws Exception {
        var filter = new ValidPurchaseFilter();

        assertThat(filter.filter(event("alice", "book", "12.50", FIRST_INSTANT))).isTrue();
        assertThat(filter.filter(event("", "book", "12.50", FIRST_INSTANT))).isFalse();
        assertThat(filter.filter(event("alice", "book", "0", FIRST_INSTANT))).isFalse();
    }

    @Test
    void normalizesCustomerAndProductFields() throws Exception {
        var normalized =
                new NormalizePurchase()
                        .map(event(" Alice ", " Flink Book ", "12.50", FIRST_INSTANT));

        assertThat(normalized.customerId()).isEqualTo("alice");
        assertThat(normalized.product()).isEqualTo("Flink Book");
        assertThat(normalized.amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void reducesPurchasesIntoRunningCustomerSpend() throws Exception {
        var reduced =
                new RunningSpend()
                        .reduce(
                                event("alice", "book", "12.50", FIRST_INSTANT),
                                event("alice", "course", "20.00", SECOND_INSTANT));

        assertThat(reduced.customerId()).isEqualTo("alice");
        assertThat(reduced.product()).isEqualTo("running-total");
        assertThat(reduced.amount()).isEqualByComparingTo("32.50");
        assertThat(reduced.occurredAt()).isEqualTo(SECOND_INSTANT);
    }

    private static PurchaseEvent event(
            String customerId, String product, String amount, Instant occurredAt) {
        return new PurchaseEvent(customerId, product, new BigDecimal(amount), occurredAt);
    }
}
