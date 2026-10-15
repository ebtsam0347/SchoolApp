package com.forward.hrms.commons.core.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transaction-boundary helpers. Two patterns repeat across the codebase
 * and both are footguns when written by hand:
 *
 * <ol>
 *   <li><b>Side effects that must not happen on rollback</b> — Redis
 *       SETs, message publishes, cache invalidations. Done inside the
 *       business {@code @Transactional} they fire before commit; if the
 *       commit fails or the tx rolls back, the side effect is already
 *       out the door. Wrap them in {@link #afterCommit(Runnable)} so the
 *       Spring tx manager runs them only after a successful commit.</li>
 *   <li><b>Same code path called inside or outside a tx</b> — when no tx
 *       is active there is nothing to wait for, so the runnable should
 *       just execute inline. {@link #afterCommit(Runnable)} handles
 *       that fall-through.</li>
 * </ol>
 *
 * <p>If a tx is active but rolls back, the runnable is silently dropped
 * — which is the entire point. Callers that need a fallback on rollback
 * should use Spring's {@link TransactionSynchronization} directly.
 */
public final class Tx {

    private Tx() {}

    /**
     * Run {@code action} after the current transaction commits
     * successfully, or immediately if no transaction is active. Exceptions
     * thrown by the action propagate out of {@code afterCompletion} and
     * are logged by the Spring tx manager — they cannot poison the
     * commit, which already happened.
     */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
