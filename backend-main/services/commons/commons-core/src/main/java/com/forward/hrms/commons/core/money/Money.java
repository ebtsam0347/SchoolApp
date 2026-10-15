package com.forward.hrms.commons.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Money in Pakistani rupees, stored as {@code numeric(15,2)} on the
 * wire and as {@link BigDecimal} (scale = 2) here. Any operation that
 * could produce a fraction below scale 2 — multiplication by a
 * percentage, proration over days — rounds UP to the next paisa via
 * {@link RoundingMode#CEILING} so payroll, attendance OT, and any
 * future money math agree byte-for-byte across services.
 *
 * <p>The legacy {@code Long} paisa accessor was removed when columns
 * moved to {@code numeric(15,2)}; callers go through {@link #amount()}
 * or {@link #of(BigDecimal)}. There is deliberately no {@code of(double)}
 * — money never originates in a {@code double}.
 */
public final class Money implements Comparable<Money> {

    public static final RoundingMode ROUNDING = RoundingMode.CEILING;
    public static final int SCALE = 2;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final Currency PKR = Currency.getInstance("PKR");

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.signum() == 0 ? ZERO : new Money(amount);
    }

    public static Money of(long rupees) {
        return rupees == 0L ? ZERO : new Money(BigDecimal.valueOf(rupees));
    }

    public BigDecimal amount() { return amount; }

    public Money add(Money other) {
        return of(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return of(amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return of(amount.multiply(factor));
    }

    public Money multiply(long n) {
        return multiply(BigDecimal.valueOf(n));
    }

    /**
     * Divide by a non-zero {@link BigDecimal}, scale 2, CEILING. Replaces
     * every ad-hoc {@code BigDecimal.divide(divisor, scale, RoundingMode)}
     * in the payroll codebase so the rounding rule lives in one place.
     */
    public Money divide(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        return new Money(amount.divide(divisor, SCALE, ROUNDING));
    }

    /**
     * Pro-rate by {@code active / total}. Multiplication first preserves
     * precision; the final division rounds CEILING at scale 2. Replaces
     * the {@code (days - lop) / days} pattern in compute.
     */
    public Money proration(BigDecimal active, BigDecimal total) {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(total, "total");
        if (total.signum() == 0) {
            throw new ArithmeticException("proration by zero total");
        }
        return new Money(amount.multiply(active).divide(total, SCALE, ROUNDING));
    }

    public Money min(Money other) { return amount.compareTo(other.amount) <= 0 ? this : other; }
    public Money max(Money other) { return amount.compareTo(other.amount) >= 0 ? this : other; }

    public boolean isZero() { return amount.signum() == 0; }
    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isNegative() { return amount.signum() < 0; }

    /** Render as PKR currency (e.g. "Rs 1,234.50"). */
    public String format(Locale locale) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        nf.setCurrency(PKR);
        return nf.format(amount);
    }

    @Override
    public int compareTo(Money other) { return amount.compareTo(other.amount); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() { return amount.stripTrailingZeros().hashCode(); }

    @Override
    public String toString() { return amount.toPlainString(); }
}
