package com.forward.hrms.payroll.client;

import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.payroll.config.FairPriceShopProperties;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Fair Price Shop purchases, read straight from the shop's SQL Server view
 * {@code dbo.View_FairPrice_Out} (one row per CardNo + calendar month,
 * {@code TotalAmount} already summed). Payroll's card number is the
 * employee code.
 *
 * <p>The pool is private to this client and not a Spring bean, so it can't
 * displace the Postgres {@code DataSource} JPA and Flyway autoconfigure on,
 * and the shop DB being down doesn't touch payroll's readiness. It opens
 * lazily on the first query.
 *
 * <p>A failure to reach or read the view throws {@link UpstreamError}
 * (Audit Finding 3): "couldn't reach the shop" must never be read as
 * "nothing owed". A payroll run then fails its compute step and the saga
 * retries; settlement creation rolls back for HR to retry.
 */
@Slf4j
@Component
public class FairPriceShopClient implements DisposableBean {

    private static final String MONTH_SQL = """
            select CardNo, TotalAmount
              from dbo.View_FairPrice_Out
             where Month = ? and Year = ?
            """;

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public FairPriceShopClient(FairPriceShopProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("fair-price-shop");
        ds.setJdbcUrl(props.jdbcUrl());
        ds.setUsername(props.username());
        ds.setPassword(props.password());
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10_000);
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.jdbc.setQueryTimeout(30);
    }

    /** Every card's purchases for {@code month}, keyed by trimmed CardNo. */
    public Map<String, BigDecimal> amountsForMonth(YearMonth month) {
        try {
            Map<String, BigDecimal> amounts = new HashMap<>();
            jdbc.query(MONTH_SQL, rs -> {
                String cardNo = rs.getString("CardNo");
                BigDecimal amount = rs.getBigDecimal("TotalAmount");
                if (cardNo != null && amount != null) {
                    amounts.merge(cardNo.trim(), amount, BigDecimal::add);
                }
            }, month.getMonthValue(), month.getYear());
            log.debug("Fair Price Shop {}: {} cards", month, amounts.size());
            return amounts;
        } catch (RuntimeException ex) {
            throw new UpstreamError(PayrollErrorCode.FAIR_PRICE_SHOP_CHECK_FAILED,
                    "Fair Price Shop read failed for %s: %s".formatted(month, ex.getMessage()), ex);
        }
    }

    /**
     * Purchases for the employee in the month of {@code lastWorkingDay} —
     * the final-settlement rule. Blank code returns ZERO without a query.
     */
    public BigDecimal outstandingAmount(String employeeCode, LocalDate lastWorkingDay) {
        if (employeeCode == null || employeeCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        return amountsForMonth(YearMonth.from(lastWorkingDay))
                .getOrDefault(employeeCode.trim(), BigDecimal.ZERO);
    }

    @Override
    public void destroy() {
        dataSource.close();
    }
}
