package com.forward.hrms.commons.batch;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchRunnerTest {

    enum TestCode implements ErrorCode { ROW_BAD }

    /** Stub tx manager that records begin/commit/rollback per call without touching a real DB. */
    static final class StubTxManager extends AbstractPlatformTransactionManager {
        int begins, commits, rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition def) { begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    @Test
    void allSucceeded_collectsResultsInOrder() {
        StubTxManager tx = new StubTxManager();
        BatchRunner runner = new BatchRunner(tx);
        BatchOutcomes<Integer, String> out = runner.runWithPartial(List.of(1, 2, 3),
                i -> "row-" + i);

        assertEquals(3, out.succeededCount());
        assertEquals(0, out.failedCount());
        assertTrue(out.allSucceeded());
        assertEquals("row-1", out.succeeded().get(0).result());
        assertEquals("row-3", out.succeeded().get(2).result());
        assertEquals(3, tx.begins);
        assertEquals(3, tx.commits);
        assertEquals(0, tx.rollbacks);
    }

    @Test
    void oneFailureDoesNotAbortBatch_andCarriesItem() {
        StubTxManager tx = new StubTxManager();
        BatchRunner runner = new BatchRunner(tx);
        BatchOutcomes<Integer, String> out = runner.runWithPartial(List.of(1, 2, 3),
                i -> {
                    if (i == 2) throw new ConflictError(TestCode.ROW_BAD, "boom for " + i);
                    return "row-" + i;
                });

        assertEquals(2, out.succeededCount());
        assertEquals(1, out.failedCount());
        assertEquals(2, (int) out.failed().get(0).item());
        assertEquals("ROW_BAD", out.failed().get(0).errorCode());
        assertEquals("boom for 2", out.failed().get(0).message());
        assertEquals(3, tx.begins);
        assertEquals(2, tx.commits);
        assertEquals(1, tx.rollbacks);
    }

    @Test
    void unknownThrowableIsCapturedAsInternal() {
        StubTxManager tx = new StubTxManager();
        BatchRunner runner = new BatchRunner(tx);
        Function<Integer, String> worker = i -> { throw new RuntimeException("vanilla"); };
        BatchOutcomes<Integer, String> out = runner.runWithPartial(List.of(1), worker);

        assertEquals(1, out.failedCount());
        assertEquals("INTERNAL", out.failed().get(0).errorCode());
        assertEquals("vanilla", out.failed().get(0).message());
    }
}
