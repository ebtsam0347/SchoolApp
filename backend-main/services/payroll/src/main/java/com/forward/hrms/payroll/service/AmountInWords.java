package com.forward.hrms.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rupee amount in words, Pakistani numbering (thousand / lakh / crore):
 * {@code 103500.50} → {@code "Rupees One Lakh Three Thousand Five Hundred and Fifty Paisa Only"}.
 */
final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
            "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty",
            "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {}

    static String of(BigDecimal amount) {
        if (amount == null) return "-";
        BigDecimal scaled = amount.abs().setScale(2, RoundingMode.HALF_UP);
        long rupees = scaled.longValue();
        int paisa = scaled.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        String words = rupees == 0 ? "Zero" : words(rupees);
        StringBuilder out = new StringBuilder(amount.signum() < 0 ? "Minus Rupees " : "Rupees ").append(words);
        if (paisa > 0) out.append(" and ").append(below100(paisa)).append(" Paisa");
        return out.append(" Only").toString();
    }

    private static String words(long n) {
        StringBuilder sb = new StringBuilder();
        n = chunk(sb, n, 10_000_000L, "Crore");
        n = chunk(sb, n, 100_000L, "Lakh");
        n = chunk(sb, n, 1_000L, "Thousand");
        n = chunk(sb, n, 100L, "Hundred");
        if (n > 0) append(sb, below100((int) n));
        return sb.toString();
    }

    private static long chunk(StringBuilder sb, long n, long unit, String name) {
        if (n < unit) return n;
        long count = n / unit;
        // Crores can exceed 99 — spell the multiplier with the same system.
        append(sb, (count >= 100 ? words(count) : below100((int) count)) + " " + name);
        return n % unit;
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(part);
    }
}
