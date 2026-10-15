package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.service.EmployeeCodeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for {@link EmployeeCodeService}. The allocator relies on
 * Postgres row-level locking inside an {@code INSERT … ON CONFLICT … RETURNING}
 * to serialize concurrent allocations. If that ever regresses (e.g. someone
 * swaps to a non-atomic find-then-update) two onboarding calls firing the
 * same millisecond would issue duplicate codes — which the unique index on
 * {@code employees.employee_code} would then reject, but only one onboarding
 * would succeed and the other would surface as a 5xx mid-saga.
 *
 * <p>Real Postgres via Testcontainers; JVM-side concurrency via
 * {@code Executors.newFixedThreadPool}. Each task wraps {@code allocate()}
 * in its own {@code REQUIRES_NEW} transaction (the service uses MANDATORY).
 */
@SpringBootTest
@WithTestcontainers
class EmployeeCodeAllocatorIntegrationTest {

    private static final Pattern CODE = Pattern.compile("FS-(\\d{4})-(\\d{4})");

    @Autowired EmployeeCodeService codes;
    @Autowired PlatformTransactionManager txm;
    @PersistenceContext EntityManager em;

    private TransactionTemplate newTx;

    @BeforeEach
    void resetSequence() {
        // Wipe the sequence row for the current year so each test starts
        // from 1. Must be in a tx — the native delete commits via
        // TransactionTemplate.
        new TransactionTemplate(txm).executeWithoutResult(s ->
                em.createNativeQuery("delete from employee_code_sequence")
                        .executeUpdate());

        newTx = new TransactionTemplate(txm);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void sequentialAllocations_areMonotonicAndZeroPadded() {
        String first  = newTx.execute(s -> codes.allocate());
        String second = newTx.execute(s -> codes.allocate());
        String third  = newTx.execute(s -> codes.allocate());

        int year = LocalDate.now().getYear();
        assertThat(first).isEqualTo("FS-%d-0001".formatted(year));
        assertThat(second).isEqualTo("FS-%d-0002".formatted(year));
        assertThat(third).isEqualTo("FS-%d-0003".formatted(year));
    }

    @Test
    void concurrentAllocations_yieldNoGapsOrDuplicates() throws Exception {
        int parallelism = 16;
        int totalAllocations = 100;

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<String>> futures = new java.util.ArrayList<>(totalAllocations);
            for (int i = 0; i < totalAllocations; i++) {
                futures.add(pool.submit(() -> newTx.execute(s -> codes.allocate())));
            }
            List<String> results = new java.util.ArrayList<>();
            for (Future<String> f : futures) results.add(f.get());

            assertThat(results).hasSize(totalAllocations);
            assertThat(results).doesNotHaveDuplicates();

            int year = LocalDate.now().getYear();
            List<Integer> seqs = results.stream().map(c -> {
                Matcher m = CODE.matcher(c);
                if (!m.matches()) throw new AssertionError("bad code: " + c);
                assertThat(Integer.parseInt(m.group(1))).isEqualTo(year);
                return Integer.parseInt(m.group(2));
            }).sorted().toList();

            assertThat(seqs).startsWith(1).endsWith(totalAllocations);
            for (int i = 0; i < seqs.size(); i++) {
                assertThat(seqs.get(i)).as("sequence at index %d", i).isEqualTo(i + 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
