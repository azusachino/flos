package io.github.azusachino.flos.flink.state;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A purchase writes to the customer's cart state. A probe only reads it, to demonstrate that
 * StateTtlConfig's update type controls whether reading also refreshes the TTL clock.
 *
 * <p>{@code delayBeforeMillis} is applied by {@link CartTotalFunction} immediately before it
 * touches state, not by the source. A keyBy shuffle sits between the two, so pacing the source's
 * emission does not reliably reproduce the same wall-clock gap at the point TTL is actually
 * evaluated; sleeping right before the state access does.
 */
public record CartActivity(
        String customerId, BigDecimal amount, boolean probe, long delayBeforeMillis)
        implements Serializable {

    public static CartActivity purchase(String customerId, String amount, long delayBeforeMillis) {
        return new CartActivity(customerId, new BigDecimal(amount), false, delayBeforeMillis);
    }

    public static CartActivity probe(String customerId, long delayBeforeMillis) {
        return new CartActivity(customerId, BigDecimal.ZERO, true, delayBeforeMillis);
    }
}
