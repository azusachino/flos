package io.github.azusachino.flos.flink.clickhouse;

import com.clickhouse.data.ClickHouseDataType;
import java.util.List;
import java.util.Map;
import org.apache.flink.connector.clickhouse.convertor.ColumnBinding;
import org.apache.flink.connector.clickhouse.convertor.DataMapper;

final class ClickHouseSinkEventMapper extends DataMapper<ClickHouseSinkEvent> {

    @Override
    public void toMap(ClickHouseSinkEvent input, Map<String, Object> map) {
        map.put("event_id", input.eventId());
        map.put("occurred_at", input.occurredAt());
        map.put("customer_id", input.customerId());
        map.put("symbol", input.symbol());
        map.put("amount", input.amount());
    }

    @Override
    public List<ColumnBinding> bindings() {
        return List.of(
                ColumnBinding.scalar("event_id", "event_id", ClickHouseDataType.String),
                ColumnBinding.dateTime64("occurred_at", "occurred_at", 3, "UTC"),
                ColumnBinding.scalar(
                        "customer_id", "customer_id", ClickHouseDataType.String, false, true),
                ColumnBinding.scalar("symbol", "symbol", ClickHouseDataType.String),
                ColumnBinding.decimal("amount", "amount", 12, 2));
    }
}
