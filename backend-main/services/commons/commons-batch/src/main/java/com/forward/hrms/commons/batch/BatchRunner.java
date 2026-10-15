package com.forward.hrms.commons.batch;

import com.forward.hrms.commons.core.error.BusinessError;

import lombok.extern.slf4j.Slf4j;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Per-row {@code REQUIRES_NEW} batch helper. Replaces the project's
 * "single tx for all rows" anti-pattern: payroll compute, increment
 * apply, short-term advance bulk-create, bonus-installment fan-out.
 *
 * <p>{@link #runWithPartial(Iterable, Function)} runs each item's
 * worker in its own transaction (PROPAGATION_REQUIRES_NEW). A failure
 * on one row is collected as a {@link BatchOutcomes.Failure} and the
 * batch continues. Callers get per-row outcomes back and decide
 * whether to surface them in a response body, an outbox event, an
 * audit row, or just a counter.
 *
 * <p>The runner does NOT enforce any retry / backoff policy — callers
 * pick that up if they need it. Errors that escape the worker as
 * {@link BusinessError} have their {@code code().name()} captured;
 * anything else falls back to {@code "INTERNAL"}. The full throwable
 * is preserved on the {@link BatchOutcomes.Failure#cause()} field for
 * upstream logging.
 *
 * <p>Auto-wired by {@link CommonsBatchAutoConfiguration} — services
 * that pull the {@code commons-batch} dependency get a singleton
 * {@code BatchRunner} bean for free.
 */
@Slf4j
public class BatchRunner {

    private final TransactionTemplate perItemTx;

    public BatchRunner(PlatformTransactionManager txManager) {
        this.perItemTx = new TransactionTemplate(txManager);
        this.perItemTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Run {@code worker} once per item in its own transaction. Returns
     * a {@link BatchOutcomes} carrying the successes and failures in
     * iteration order. The whole batch always completes — a thrown
     * exception is captured, the transaction is rolled back, and the
     * loop moves on.
     */
    public <I, R> BatchOutcomes<I, R> runWithPartial(Iterable<I> items, Function<I, R> worker) {
        List<BatchOutcomes.Success<I, R>> ok = new ArrayList<>();
        List<BatchOutcomes.Failure<I>> failed = new ArrayList<>();
        for (I item : items) {
            try {
                R result = perItemTx.execute(status -> worker.apply(item));
                ok.add(new BatchOutcomes.Success<>(item, result));
            } catch (Throwable t) {
                failed.add(toFailure(item, t));
            }
        }
        return new BatchOutcomes<>(ok, failed);
    }

    /**
     * Void variant — wraps a {@link Consumer} so callers don't have to
     * return null. Renamed (vs an overload) so a method-reference like
     * {@code computeService::computeOne} doesn't ambiguity-resolve to
     * the wrong target.
     */
    public <I> BatchOutcomes<I, Void> runWithPartialVoid(Iterable<I> items, Consumer<I> worker) {
        return runWithPartial(items, item -> {
            worker.accept(item);
            return null;
        });
    }

    /**
     * Run {@code work} in its own {@code REQUIRES_NEW} transaction.
     * Useful for the orchestrator pattern: persist a parent row in an
     * independent tx so subsequent per-row {@link #runWithPartial}
     * calls (also REQUIRES_NEW, on a different connection) can see and
     * FK-reference it. Re-throws the supplier's exception with the tx
     * rolled back; callers handle as usual.
     */
    public <T> T runInNewTx(java.util.function.Supplier<T> work) {
        return perItemTx.execute(status -> work.get());
    }

    public void runInNewTxVoid(Runnable work) {
        perItemTx.executeWithoutResult(status -> work.run());
    }

    private static <I> BatchOutcomes.Failure<I> toFailure(I item, Throwable t) {
        String code = t instanceof BusinessError be ? be.code().name() : "INTERNAL";
        String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return new BatchOutcomes.Failure<>(item, code, message, t);
    }
}
