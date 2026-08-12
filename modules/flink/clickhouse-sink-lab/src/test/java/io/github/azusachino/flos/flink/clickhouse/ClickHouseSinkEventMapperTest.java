package io.github.azusachino.flos.flink.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClickHouseSinkEventMapperTest {

    @Test
    void mapsTheEventContractInTableOrder() {
        var event =
                new ClickHouseSinkEvent(
                        "event-1",
                        ZonedDateTime.parse("2026-08-01T00:00:00Z"),
                        "customer-a",
                        "BTC",
                        new BigDecimal("12.34"));
        Map<String, Object> values = new LinkedHashMap<>();

        new ClickHouseSinkEventMapper().toMap(event, values);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("event_id", "event-1");
        expected.put("occurred_at", event.occurredAt());
        expected.put("customer_id", "customer-a");
        expected.put("symbol", "BTC");
        expected.put("amount", new BigDecimal("12.34"));

        assertThat(values).containsExactlyEntriesOf(expected);
        assertThat(new ClickHouseSinkEventMapper().bindings())
                .extracting(binding -> binding.columnName)
                .containsExactly("event_id", "occurred_at", "customer_id", "symbol", "amount");
    }

    @Test
    void createsDeterministicBoundedFixture() {
        assertThat(ClickHouseSinkLabJob.events(3))
                .extracting(ClickHouseSinkEvent::eventId)
                .containsExactly("event-0", "event-1", "event-2");
    }
}
