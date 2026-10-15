/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/employees/{id}/bank-details": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get bank details (404 if none)
         * @description Requires `payroll.read`.
         */
        get: operations["getBankDetails"];
        /**
         * Upsert bank details. Account number encrypted at rest.
         * @description Requires `payroll.write`.
         */
        put: operations["putBankDetails"];
        post?: never;
        /**
         * Delete bank details. Only allowed when payment_mode=CASH.
         * @description Requires `payroll.write`.
         */
        delete: operations["deleteBankDetails"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List batches
         * @description Default sort by id desc. Requires `payroll.read`.
         */
        get: operations["list"];
        put?: never;
        /**
         * Create — opens batch + lines in PENDING
         * @description Requires `payroll.write`.
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject — PENDING / HR_APPROVED → REJECTED
         * @description Requires `payroll.advance.hr` or `payroll.advance.accounts`.
         */
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/hr-approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * HR-approve — PENDING → HR_APPROVED
         * @description Requires `payroll.advance.hr`.
         */
        post: operations["hrApprove"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/disburse": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark disbursed — APPROVED → DISBURSED
         * @description Requires `payroll.advance.accounts`.
         */
        post: operations["disburse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel — pre-deduction only
         * @description Requires `payroll.write`.
         */
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/accounts-approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Accounts-approve — flips lines to APPROVED
         * @description Requires `payroll.advance.accounts`.
         */
        post: operations["accountsApprove"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List increment batches
         * @description Requires `payroll.read`.
         */
        get: operations["list_1"];
        put?: never;
        /**
         * Create a DRAFT batch (JSON form — also covers ad-hoc 1-line batches)
         * @description Requires `payroll.admin`.
         */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches/{id}/lines": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add a line to a DRAFT batch
         * @description 409 if the batch already has a line for that employee. Requires `payroll.admin`.
         */
        post: operations["addLine"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel a DRAFT batch
         * @description Requires `payroll.admin`.
         */
        post: operations["cancel_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches/{id}/apply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply a DRAFT batch (DRAFT → APPLIED, single transaction)
         * @description Refuses if any line is ERROR. Re-apply on an APPLIED batch is a no-op. Requires `payroll.admin`.
         */
        post: operations["apply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get current basic salary
         * @description Requires `payroll.read`.
         */
        get: operations["current"];
        put?: never;
        /**
         * Revise basic salary
         * @description Closes the existing current row and inserts a new one. HR is expected to revise the INCOME_TAX line on the components endpoint when basic changes. Requires `payroll.write`.
         */
        post: operations["revise"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/components": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List components
         * @description Requires `payroll.read`.
         */
        get: operations["list_2"];
        put?: never;
        /**
         * Create a component
         * @description Requires `payroll.admin`.
         */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/payslips-run/jobs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Payslips whole-run — enqueue ZIP of every payslip PDF
         * @description Zips every payslip PDF that {@code PayslipPdfService.generateAll} has already written to MinIO for the named run. Skips payslips with no {@code pdf_object_key}. Throws if the run has no generated PDFs at all (finalize the run first). Requires `payroll.reports.run`.
         */
        post: operations["enqueuePayslipsRun"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/pay-register/jobs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Pay register — enqueue an async Excel render
         * @description Enqueues a {@code PAY_REGISTER} job; the runner renders the Excel and uploads to MinIO. Poll {@code GET /reports/jobs/{id}} for status; once READY, {@code GET /reports/jobs/{id}/download} returns a 302 to a signed URL. Requires `payroll.reports.run`.
         */
        post: operations["enqueuePayRegister"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/increment-letters/jobs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Increment letters batch — enqueue ZIP of per-employee PDFs
         * @description Renders one A4 increment letter per employee in the named increment batch and packages the bundle as a ZIP. Poll {@code GET /reports/jobs/{id}}; once READY, download via {@code /reports/jobs/{id}/download}. Requires `payroll.reports.run`.
         */
        post: operations["enqueueIncrementLetters"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List permanent advances
         * @description Default sort by id desc, page size 20 (max 200). Requires `payroll.read`.
         */
        get: operations["list_3"];
        put?: never;
        /**
         * Create — opens in PENDING
         * @description Self-apply when the request's `employeeId` matches the caller's own employee id (resolved via employee-service /me). HR with `payroll.write` may file on behalf of any employee. Approval still requires HR + Accounts.
         */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject — PENDING / HR_APPROVED → REJECTED
         * @description Either the HR or Accounts reviewer can reject. Requires `payroll.advance.hr` or `payroll.advance.accounts`.
         */
        post: operations["reject_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/hr-approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * HR-approve — PENDING → HR_APPROVED
         * @description Requires `payroll.advance.hr`.
         */
        post: operations["hrApprove_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/disburse": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark disbursed — APPROVED → DISBURSED
         * @description Records that the cash has been paid to the employee. Deductions can begin once disbursed. Requires `payroll.advance.accounts`.
         */
        post: operations["disburse_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/deductions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List deductions for an advance
         * @description Requires `payroll.read`.
         */
        get: operations["listDeductions"];
        put?: never;
        /**
         * Record an out-of-band deduction
         * @description For payroll-runner-driven deductions the runner uses the same service helper directly. Closes the advance when the running balance hits zero. Requires `payroll.advance.accounts`.
         */
        post: operations["recordDeduction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel — pre-disbursement only
         * @description Requires `payroll.write`.
         */
        post: operations["cancel_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}/accounts-approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Accounts-approve — HR_APPROVED → APPROVED
         * @description Requires `payroll.advance.accounts`.
         */
        post: operations["accountsApprove_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/{id}/unhold": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Unhold (HELD → DRAFT). Only valid before the run is POSTED.
         * @description Returns the payslip to the review state so the run's post step can promote it to PAID. Requires `payroll.admin`.
         */
        post: operations["unhold"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/{id}/release": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Release a held payslip (HELD in POSTED run → RELEASE_PENDING).
         * @description The next compute picks this up and adds a HELD_SALARY_RELEASE earning line to that employee's new payslip. Requires `payroll.admin`.
         */
        post: operations["release"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/{id}/hold": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Hold a payslip (DRAFT or PAID → HELD)
         * @description Pre-post on a DRAFT payslip the post step skips the row; post-post on a PAID payslip the operator can later `release` in a future run. Requires `payroll.admin`.
         */
        post: operations["hold"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/sagas/{id}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Re-arm a FAILED saga
         * @description Resets a FAILED saga to RUNNING at its current step with attempt_count=0. No-op for sagas in any other state. Use after fixing the underlying upstream issue (MinIO outage, etc.). Requires `payroll.admin`.
         */
        post: operations["retry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List runs in a fiscal year
         * @description Requires `payroll.read`.
         */
        get: operations["list_4"];
        put?: never;
        /**
         * Create a DRAFT run for a calendar month
         * @description Requires `payroll.admin`.
         */
        post: operations["create_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/retry-skips": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Re-run compute for skipped employees
         * @description After HR fixes the underlying data (salary, bank details, etc.), this re-runs compute for the listed skip rows (or every row if the body is empty). Resolved employees lose their skip row and gain a payslip; rows that still fail are updated with the new reason. Run stays `COMPUTED`. Requires `payroll.admin`.
         */
        post: operations["retrySkips"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/reset": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reset a COMPUTED run back to LOCKED
         * @description Drops every payslip and skip row on the run (payslip_lines cascade) and clears `computedAt` + `skippedCount`, leaving the attendance period locked so the operator can fix employee data and re-run `/compute`. Only valid in `COMPUTED`. Requires `payroll.admin`.
         */
        post: operations["reset"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/post": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Post — generates PDFs and emits per-employee events. Irreversible.
         * @description Requires `payroll.admin`.
         */
        post: operations["post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/lock-attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Lock attendance for the run's period
         * @description Verifies the period is already locked in attendance (the operator closes the month from the attendance side) and flips the run DRAFT → LOCKED. Does not command the lock: 409 if the attendance period isn't locked yet — close it in attendance first. Requires `payroll.admin`.
         */
        post: operations["lockAttendance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/compute": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Compute payslips
         * @description Builds payslips for every active employee against the locked attendance period. To re-compute after fixing data, use `/reset` first. Requires `payroll.admin`.
         */
        post: operations["compute"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel a DRAFT or LOCKED run
         * @description Requires `payroll.admin`.
         */
        post: operations["cancel_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve a computed run
         * @description Requires `payroll.admin`.
         */
        post: operations["approve"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/config": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Current effective row
         * @description Requires `payroll.read`.
         */
        get: operations["current_1"];
        put?: never;
        /**
         * Insert a new effective-dated row
         * @description Closes the previous open-ended row at effectiveFrom-1. Requires `payroll.admin`.
         */
        post: operations["create_5"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/off-cycle-payments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Filter off-cycle payments
         * @description Requires `payroll.read`.
         */
        get: operations["list_5"];
        put?: never;
        /**
         * Record an HR-driven off-cycle payment
         * @description Requires `payroll.write`.
         */
        post: operations["create_6"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leave-encashments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List encashments
         * @description Default sort by id desc. Requires `payroll.read`.
         */
        get: operations["list_6"];
        put?: never;
        /**
         * Create — opens in PENDING. Amount is derived.
         * @description Requires `payroll.write`.
         */
        post: operations["create_7"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leave-encashments/{id}/mark-paid": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark paid — PENDING → PAID
         * @description Optionally tag the payroll run that disbursed it. Requires `payroll.write`.
         */
        post: operations["markPaid"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leave-encashments/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel — pre-payment only
         * @description Requires `payroll.write`.
         */
        post: operations["cancel_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/fiscal-years": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List all fiscal years (most recent first)
         * @description Requires `payroll.read`.
         */
        get: operations["list_7"];
        put?: never;
        /**
         * Create a fiscal year
         * @description Requires `payroll.admin`.
         */
        post: operations["create_8"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/salary-components": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Current effective lines
         * @description Requires `payroll.read`.
         */
        get: operations["list_8"];
        put?: never;
        /**
         * Add a line
         * @description Picks an active row from the salary-components catalog. amount defaults to component.defaultAmount when omitted. Requires `payroll.write`.
         */
        post: operations["add"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/salary-components/{lineId}/stop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Stop a line
         * @description Sets effective_to on the current row. Requires `payroll.write`.
         */
        post: operations["remove"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List bonus events
         * @description Requires `payroll.read`.
         */
        get: operations["list_9"];
        put?: never;
        /**
         * Create a DRAFT event
         * @description Requires `payroll.admin`.
         */
        post: operations["create_9"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events/{id}/close": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Close (ACTIVE → CLOSED) — no further installments
         * @description Requires `payroll.admin`.
         */
        post: operations["close"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events/{id}/activate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Activate (DRAFT → ACTIVE) — freezes per-employee amounts
         * @description Requires `payroll.admin`.
         */
        post: operations["activate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events/{eventId}/installments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add an installment to a bonus event
         * @description Requires `payroll.admin`.
         */
        post: operations["addInstallment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events/{eventId}/installments/{id}/apply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply an installment (DRAFT → APPLIED). Idempotent.
         * @description Walks bonus_event_amounts, writes one off_cycle_payments row per eligible employee with category=BONUS. Requires `payroll.admin`.
         */
        post: operations["apply_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/banks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List banks
         * @description Active only by default. Pass `includeInactive=true` for the admin catalog UI. Requires `payroll.read`.
         */
        get: operations["list_10"];
        put?: never;
        /**
         * Create a bank
         * @description Requires `payroll.admin`.
         */
        post: operations["create_10"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one (with per-line diffs and validation errors)
         * @description Requires `payroll.read`.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Update a DRAFT batch
         * @description Requires `payroll.admin`.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/salary/increment-batches/{id}/lines/{lineId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete a DRAFT batch line
         * @description Requires `payroll.admin`.
         */
        delete: operations["deleteLine"];
        options?: never;
        head?: never;
        /**
         * Update a DRAFT batch line
         * @description Requires `payroll.admin`.
         */
        patch: operations["updateLine"];
        trace?: never;
    };
    "/salary/components/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Update a component (name / default amount / displayOrder / active)
         * @description Requires `payroll.admin`.
         */
        patch: operations["update_1"];
        trace?: never;
    };
    "/salary/components/reorder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Bulk-reorder salary components
         * @description Applies the supplied `displayOrder` to each listed component in a single transaction. Returns the full unfiltered catalog. Requires `payroll.admin`.
         */
        patch: operations["reorder"];
        trace?: never;
    };
    "/off-cycle-payments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one
         * @description Requires `payroll.read`.
         */
        get: operations["get_1"];
        put?: never;
        post?: never;
        /**
         * Delete an HR-recorded payment (hard delete with audit-event trail).
         * @description Requires `payroll.write`.
         */
        delete: operations["delete"];
        options?: never;
        head?: never;
        /**
         * Patch an HR-recorded payment
         * @description Bonus-sourced rows are immutable; fix at the bonus-event level. Requires `payroll.write`.
         */
        patch: operations["update_2"];
        trace?: never;
    };
    "/fiscal-years/{code}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Mark closed / current / future
         * @description Requires `payroll.admin`.
         */
        patch: operations["setStatus"];
        trace?: never;
    };
    "/employees/{id}/salary-components/{lineId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Update a line — closes the current row and inserts a new one
         * @description Requires `payroll.write`.
         */
        patch: operations["update_3"];
        trace?: never;
    };
    "/employees/{id}/payroll-settings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get payroll settings
         * @description Requires `payroll.read`.
         */
        get: operations["getSettings"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Update payroll settings (eobi enrollment, payment mode)
         * @description Requires `payroll.write`.
         */
        patch: operations["updateSettings"];
        trace?: never;
    };
    "/bonuses/events/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one (with installments + per-employee amounts)
         * @description Requires `payroll.read`.
         */
        get: operations["get_2"];
        put?: never;
        post?: never;
        /**
         * Delete a DRAFT event
         * @description Requires `payroll.admin`.
         */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        /**
         * Patch a DRAFT event
         * @description Requires `payroll.admin`.
         */
        patch: operations["update_4"];
        trace?: never;
    };
    "/bonuses/events/{eventId}/installments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete a DRAFT installment
         * @description Requires `payroll.admin`.
         */
        delete: operations["deleteInstallment"];
        options?: never;
        head?: never;
        /**
         * Patch a DRAFT installment
         * @description Requires `payroll.admin`.
         */
        patch: operations["updateInstallment"];
        trace?: never;
    };
    "/banks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a bank
         * @description Requires `payroll.read`.
         */
        get: operations["get_3"];
        put?: never;
        post?: never;
        /**
         * Deactivate a bank
         * @description Soft-delete — flips `active=false`. Existing employee_bank_details rows are preserved. Requires `payroll.admin`.
         */
        delete: operations["deactivate"];
        options?: never;
        head?: never;
        /**
         * Patch a bank
         * @description Null fields stay unchanged. `code` is immutable. Requires `payroll.admin`.
         */
        patch: operations["update_5"];
        trace?: never;
    };
    "/short-term-advance-batches/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one (with lines)
         * @description Requires `payroll.read`.
         */
        get: operations["get_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-term-advance-batches/{id}/lines": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List per-employee lines for a batch
         * @description Requires `payroll.read`.
         */
        get: operations["listLines"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/settlement/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Preview the settlement payslip
         * @description Requires `payroll.read`.
         */
        get: operations["preview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/increment-batches/{id}/detail": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Increment batch with employee enrichment
         * @description Returns the batch + lines, each line resolved to a name + employee code. Requires `payroll.read`.
         */
        get: operations["incrementBatch"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/salary/employees/{id}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Revision history (most recent first)
         * @description Requires `payroll.read`.
         */
        get: operations["history"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/variance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Variance vs previous run — Excel
         * @description Per-employee component-level diff between {@code currentRunId} and either the supplied {@code priorRunId} or the immediate predecessor on the same fiscal year. Only components that changed are listed. Requires `payroll.reports.run`.
         */
        get: operations["variance"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/pessi-return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * PESSI return — monthly PDF
         * @description Lists every employee with a non-zero employer-PESSI contribution in the period. PESSI is employer-only and does not reduce employee net. Requires `payroll.reports.run`.
         */
        get: operations["pessiReturn"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/payslips/{id}/pdf-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Single payslip — signed PDF URL
         * @description Returns a short-lived MinIO URL for the payslip's PDF. Exposed under /reports/... so the report catalogue routes here; the underlying object is the same one /payslips/{id}/pdf-url returns. Requires `payroll.reports.run`.
         */
        get: operations["payslipPdfUrl"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/off-cycle-payments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Off-cycle payments log — Excel
         * @description Bonuses, ad-hoc adjustments, leave encashments, and any other payments outside the regular monthly run, filtered by date range and optional category. Requires `payroll.reports.run`.
         */
        get: operations["offCyclePayments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/income-tax-deducted": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Income-tax deducted — Excel
         * @description Per-employee income tax deducted in the named period plus fiscal-year cumulative through the period end. Both bounds must lie inside the same fiscal year. Requires `payroll.reports.run`.
         */
        get: operations["incomeTaxDeducted"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/eobi-return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * EOBI return — monthly PDF
         * @description Lists every EOBI-enrolled employee with a non-zero contribution in the named payroll period, with employee code, EOBI registration number, name, CNIC, and contribution amount. Source data for the regulator's portal entry. Requires `payroll.reports.run`.
         */
        get: operations["eobiReturn"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/payroll/advances-outstanding": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Salary advances outstanding — Excel
         * @description Two sheets: open permanent advances (DISBURSED with remaining balance > 0) and approved short-term advances not yet deducted in any payroll run. Requires `payroll.reports.run`.
         */
        get: operations["advancesOutstanding"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/jobs/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Read a report job's status
         * @description Single source of truth for the FE's poll loop. Requires `payroll.reports.run`.
         */
        get: operations["get_5"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/jobs/{id}/download": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download a READY report
         * @description Returns a 302 to a short-lived signed MinIO URL. The FE follows it automatically, so the response never exposes the bucket. Throws 409 if the job is still QUEUED / RUNNING / FAILED. Requires `payroll.reports.run`.
         */
        get: operations["download"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one
         * @description Requires `payroll.read`.
         */
        get: operations["get_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permanent-advances/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Self-serve: list permanent advances for an employee
         * @description Self-serve when the path id matches the caller's employee id (resolved via employee-service /me). HR / payroll-officer callers with `payroll.read` may read any employee. Default sort is `id desc`.
         */
        get: operations["forEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List payslips by status (HELD / RELEASE_PENDING / RELEASED / PAID)
         * @description Requires `payroll.read`.
         */
        get: operations["filter"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a payslip with line breakdown
         * @description Visible to `payroll.read` / `payroll.admin` (HR / ADMIN / ACCOUNTS — any employee, all statuses), the owning employee (self-serve, posted only), and a manager holding `payroll.read.team` when the payslip's employee is a direct report (posted only). A DRAFT returns 404 to restricted callers.
         */
        get: operations["get_7"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/{id}/pdf-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Short-lived signed MinIO URL for the PDF
         * @description Same access rules as GET /payslips/{id}: `payroll.read` / `payroll.admin`, the owning employee, or a manager (`payroll.read.team`) over a direct report.
         */
        get: operations["pdfUrl"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/team": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Manager: list my team's payslips (paginated)
         * @description The calling manager's direct reports' payslips for a year (optionally a single month). Scope is resolved server-side via employee-service /me/team; DRAFT is hidden and a caller with no reports gets an empty page. Requires `payroll.read.team` (managers) or `payroll.read` (HR / ADMIN / ACCOUNTS). Default sort is `periodMonth desc`.
         */
        get: operations["team"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/queue": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Disbursement queue for HELD / RELEASE_PENDING payslips
         * @description Page of payslips at the given status with employee names + run period merged in. Requires `payroll.admin`.
         */
        get: operations["payslipQueue"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Self-serve payslips for the caller, by year
         * @description Resolves the caller's employee id server-side, fetches the year's payslips, and mints a short-lived signed PDF URL per row when generated.
         */
        get: operations["myPayslips"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payslips/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List payslips for an employee + year (paginated)
         * @description Self-serve when the path id matches the caller's employee id (resolved via employee-service /me). `payroll.read` / `payroll.admin` callers may read any employee (incl. DRAFT); a manager with `payroll.read.team` may read a direct report (posted only). Default sort is `periodMonth desc`.
         */
        get: operations["forEmployeeYear"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/sagas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sagas (paginated)
         * @description Filtered listing for operator views and the admin dashboard's failed-sagas slice. Both filters are optional. Default sort is `updatedAt,desc`. Requires `payroll.read`.
         */
        get: operations["list_11"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/sagas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a saga by id
         * @description Requires `payroll.read`.
         */
        get: operations["get_8"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/sagas/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Saga health rollup
         * @description Aggregate RUNNING + last-24h FAILED counts plus a per-name 24-hour failure-rate series for payroll-owned sagas (PAYROLL_RUN_POST, …). Mirrors the employee-service shape so the BFF can merge both sources. Requires `payroll.read`.
         */
        get: operations["health"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/sagas/by-key": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Look up a saga by (name, correlationKey)
         * @description Useful for FE to find the saga linked to a payroll run, e.g. `/sagas/by-key?name=PAYROLL_RUN_POST&correlationKey=payroll_run:42`. Requires `payroll.read`.
         */
        get: operations["find"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a run by id
         * @description Requires `payroll.read`.
         */
        get: operations["get_9"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/totals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pre-aggregated totals for a run
         * @description Returns count + sums (basic, allowances, gross, deductions, tax, eobi, pessiEmployer, net) and HELD / RELEASE_PENDING counts as a single row. Optional `status` (PAID / HELD / RELEASE_PENDING / RELEASED) and `q` (case-insensitive `employeeCode` substring) match the same filters as `/payslips`, so the BFF can show a filter-aware rollup alongside the table. Requires `payroll.read`.
         */
        get: operations["totals"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Live run summary for FE polling
         * @description One call per poll. Returns status + live counts (`totalCount`, `progressCount`, `skippedCount`, `successCount`) and pre-aggregated payslip totals in a single response. Counts pulse every ~25 rows; totals come from a SUM over committed payslips so they move live mid-compute. Poll every ~1s while status is `COMPUTING` / `POSTING`; stop polling and load `/payslips` + `/skips` once status reaches `COMPUTED` / `POSTED`. Requires `payroll.read`.
         */
        get: operations["summary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/skips": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List employees skipped during compute
         * @description Each row carries the actionable reason (`errorCode` + `message`) and the snapshot employee code/name so the FE can render and deeplink without an extra lookup. Requires `payroll.read`.
         */
        get: operations["skips"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/payslips": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Payslips on a run (paginated, filterable)
         * @description Optional filters: `status` (PAID / HELD / RELEASE_PENDING / RELEASED) and `q` (case-insensitive substring on `employeeCode`). Requires `payroll.read`.
         */
        get: operations["payslips"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/{id}/detail": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Run detail with server-side rollup + employee enrichment
         * @description Returns the run, a summary across all payslips matching the filters, and a paginated slice already enriched with employee name + code. Filters: `status` (PAID/HELD/RELEASE_PENDING/RELEASED) and `q` (case-insensitive `employeeCode` substring). Requires `payroll.read`.
         */
        get: operations["runDetail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/runs/with-summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Runs in a fiscal year, each with a server-side rollup
         * @description Lists every run for the given fiscal year and computes the per-run summary (employees / gross / net / tax / EOBI / employer PESSI). Requires `payroll.read`.
         */
        get: operations["runsWithSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/people/{id}/salary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Employee salary tab, one shot
         * @description Bundles current basic, basic history, effective component lines, the active component catalog, payroll settings, bank details (or null), gratuity, and the last 12 months of off-cycle payments. Replaces 8 fan-out calls. Requires `payroll.read`.
         */
        get: operations["salaryTab"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/payroll/config/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Full history (most recent first)
         * @description Requires `payroll.read`.
         */
        get: operations["history_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/off-cycle-payments/with-employee": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Off-cycle payments enriched with employee names
         * @description Forwards the upstream filters (`employeeId`, `from`, `to`, `category`) and resolves each row's employee server-side. Requires `payroll.read`.
         */
        get: operations["offCyclePaymentsWithEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leave-encashments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one
         * @description Requires `payroll.read`.
         */
        get: operations["get_10"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/gratuity/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Compute gratuity for an employee
         * @description Requires `payroll.read`.
         */
        get: operations["compute_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/fiscal-years/current": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current fiscal year
         * @description Requires `payroll.read`.
         */
        get: operations["current_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/salary-components/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Full history (most recent first)
         * @description Requires `payroll.read`.
         */
        get: operations["history_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/off-cycle-payments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee off-cycle payment list
         * @description Requires `payroll.read`.
         */
        get: operations["list_12"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bonuses/events/{id}/detail": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Bonus event with employee enrichment
         * @description Returns the event + frozen amounts per employee with names resolved. Requires `payroll.read`.
         */
        get: operations["bonusEvent"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/payroll/salary-distribution": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Salary distribution snapshot
         * @description Min / p25 / median / p75 / max of the current basic across every employee. Includes the raw sorted list when ≤5000 employees so the FE can re-bucket by grade with an employee-service join.
         */
        get: operations["salaryDistribution"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/payroll/cost-trend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly payroll cost trend
         * @description Per month: employer cost (gross + employer-PESSI) + headcount + cost-per-head + breakdown (gross / EOBI / PESSI / tax / net). Window 1..36 months; defaults to 24.
         */
        get: operations["costTrend"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        UpsertBankDetailsRequest: {
            /**
             * Format: int64
             * @description ID of a row in the banks catalog.
             */
            bankId: number;
            branch?: string;
            accountTitle: string;
            /** @description Pakistan IBAN: PK + 2 digits + 4-letter bank code + 16 alphanumeric (24 chars). */
            iban: string;
            accountNumber?: string;
        };
        EmployeeBankDetailsDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            bankId?: number;
            /** @description Catalog code (denormalized). */
            bankCode?: string | null;
            /** @description Catalog display name (denormalized). */
            bankName?: string | null;
            branch?: string | null;
            accountTitle?: string;
            iban?: string;
            /** @description Encrypted at rest. Returned only to HR / payroll roles. */
            accountNumber?: string | null;
        };
        /** @description Bulk short-term advance request. All lines are deducted in full from the target month's payroll. */
        CreateShortTermAdvanceBatchRequest: {
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int32 */
            targetYear: number;
            /**
             * Format: int32
             * @description 1..12 — the payroll month to deduct in.
             */
            targetMonth: number;
            notes?: string | null;
            /** @description One row per employee. Each employeeId must be unique within the batch. */
            lines: components["schemas"]["ShortTermAdvanceLineInput"][];
        };
        /** @description One employee + amount inside a batch. Amount is rupees. */
        ShortTermAdvanceLineInput: {
            /** Format: int64 */
            employeeId: number;
            amount: number;
        };
        ShortTermAdvanceBatchDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. STA-2026-0042. */
            requestCode?: string;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int32 */
            targetYear?: number;
            /** Format: int32 */
            targetMonth?: number;
            totalAmount?: number;
            /** Format: int32 */
            totalEmployees?: number;
            /** @enum {string} */
            status?: "PENDING" | "HR_APPROVED" | "APPROVED" | "REJECTED" | "DISBURSED" | "CLOSED" | "CANCELLED";
            /** Format: int64 */
            hrDecidedBy?: number | null;
            /** Format: date-time */
            hrDecidedAt?: string | null;
            hrNote?: string | null;
            /** Format: int64 */
            accountsDecidedBy?: number | null;
            /** Format: date-time */
            accountsDecidedAt?: string | null;
            accountsNote?: string | null;
            rejectionReason?: string | null;
            notes?: string | null;
            /** @description Per-employee detail. Included on detail reads, omitted on list pages. */
            lines?: components["schemas"]["ShortTermAdvanceLineDto"][] | null;
            /** Format: date-time */
            createdAt?: string;
        };
        ShortTermAdvanceLineDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            batchId?: number;
            /** Format: int64 */
            employeeId?: number;
            amount?: number;
            /** Format: int32 */
            targetYear?: number;
            /** Format: int32 */
            targetMonth?: number;
            /** @enum {string} */
            status?: "PENDING" | "APPROVED" | "REJECTED" | "DEDUCTED";
            rejectionReason?: string | null;
            /** Format: int64 */
            deductedInRunId?: number | null;
        };
        AdvanceRejectRequest: {
            reason: string;
        };
        /** @description Optional note on an approval / rejection action. */
        AdvanceDecisionRequest: {
            note?: string | null;
        };
        /** @description JSON form for ad-hoc 1-line batches (promotion / retention) and direct uploads. */
        CreateIncrementBatchRequest: {
            name: string;
            /** Format: date */
            effectiveFrom: string;
            notes?: string;
            lines: components["schemas"]["CreateIncrementBatchRequest.Line"][];
        };
        "CreateIncrementBatchRequest.Line": {
            /** Format: int64 */
            employeeId: number;
            newBasic: number;
            newIncomeTax?: number;
            /** Format: date */
            effectiveFrom?: string;
            notes?: string;
        };
        IncrementBatchDto: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** @enum {string} */
            status?: "DRAFT" | "APPLIED" | "PARTIALLY_APPLIED" | "CANCELLED";
            /** Format: date */
            effectiveFrom?: string;
            sourceFilename?: string | null;
            notes?: string | null;
            /** Format: date-time */
            appliedAt?: string | null;
            /** Format: int64 */
            appliedBy?: number | null;
            lines?: components["schemas"]["IncrementBatchLineDto"][] | null;
        };
        IncrementBatchLineDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            batchId?: number;
            /** Format: int64 */
            employeeId?: number;
            oldBasic?: number;
            newBasic?: number;
            oldIncomeTax?: number | null;
            newIncomeTax?: number | null;
            /** Format: date */
            effectiveFrom?: string | null;
            /** @enum {string} */
            status?: "VALID" | "ERROR" | "APPLIED";
            errorMessage?: string | null;
        };
        /** @description Add one line to a DRAFT increment batch. Per-line `effectiveFrom` overrides the batch default; omit to inherit it. */
        CreateIncrementBatchLineRequest: {
            /**
             * Format: int64
             * @example 1234
             */
            employeeId: number;
            /**
             * @description New basic in rupees.
             * @example 125000
             */
            newBasic: number;
            /** @description Optional new income-tax deduction (rupees). Replaces any current INCOME_TAX line if set. */
            newIncomeTax?: number | null;
            /**
             * Format: date
             * @description Per-line override; falls back to the batch's effectiveFrom when null.
             */
            effectiveFrom?: string | null;
        };
        ReviseBasicSalaryRequest: {
            /**
             * @description New monthly basic in rupees.
             * @example 50000
             */
            basic: number;
            /** Format: date */
            effectiveFrom: string;
            revisionReason?: string;
        };
        /** @description Effective-dated basic-salary row. Open-ended (effective_to=null) row is current. */
        EmployeeSalaryDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @description Monthly basic in rupees. */
            basic?: number;
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            effectiveTo?: string | null;
            revisionReason?: string | null;
        };
        CreateSalaryComponentRequest: {
            /** @example HRA */
            code: string;
            /** @example House Rent Allowance */
            name: string;
            /** @enum {string} */
            type: "EARNING" | "DEDUCTION";
            /**
             * @description Defaults to FLAT. PER_DAY restricted to EARNING.
             * @enum {string}
             */
            calcType?: "FLAT" | "PER_DAY";
            /**
             * @description Hint HR can override per employee. Rupees.
             * @example 15000
             */
            defaultAmount?: number | null;
            /**
             * Format: int32
             * @example 10
             */
            displayOrder?: number | null;
        };
        /** @description Catalog row. All amounts are flat (no percentage, no formula). */
        SalaryComponentDto: {
            /**
             * Format: int64
             * @example 5
             */
            id?: number;
            /** @example HRA */
            code?: string;
            /** @example House Rent Allowance */
            name?: string;
            /** @enum {string} */
            type?: "EARNING" | "DEDUCTION";
            /** @enum {string} */
            calcType?: "FLAT" | "PER_DAY";
            /**
             * @description Optional default amount (rupees) HR can override per employee.
             * @example 15000
             */
            defaultAmount?: number | null;
            /**
             * Format: int32
             * @example 10
             */
            displayOrder?: number;
            active?: boolean;
        };
        /** @description Async report job. Polled until status flips to READY, then the FE calls /reports/jobs/{id}/download. */
        ReportJobDto: {
            /**
             * Format: int64
             * @description Job id.
             * @example 42
             */
            id: number;
            /**
             * @description Report type discriminator.
             * @example PAY_REGISTER
             */
            type: string;
            /**
             * @description Lifecycle status.
             * @enum {string}
             */
            status: "QUEUED" | "RUNNING" | "READY" | "FAILED";
            /**
             * @description Username that requested the report.
             * @example saqib
             */
            requestedBy?: string;
            /**
             * Format: date-time
             * @description When the job was queued.
             */
            createdAt: string;
            /**
             * Format: date-time
             * @description When the runner picked it up.
             */
            startedAt?: string;
            /**
             * Format: date-time
             * @description When the runner finished (success or failure).
             */
            completedAt?: string;
            /**
             * Format: int64
             * @description Output file size in bytes; populated on READY.
             */
            outputSize?: number;
            /** @description Error code on FAILED. */
            errorCode?: string;
            /** @description Error message on FAILED. */
            errorMessage?: string;
        };
        /** @description Pay register report request — one specific payroll run. */
        PayRegisterRequest: {
            /**
             * Format: int64
             * @description Payroll run id whose payslips form the register.
             * @example 42
             */
            payrollRunId: number;
        };
        /** @description Long-term advance against future salary. Created in PENDING; HR approves first, then Accounts. All money fields are rupees. */
        CreatePermanentAdvanceRequest: {
            /** Format: int64 */
            employeeId: number;
            /** @description Principal amount, rupees. */
            amount: number;
            /** @description Regular monthly installment, rupees. */
            monthlyDeduction: number;
            /** @description Rupees taken against bonus when paid. */
            bonusOffset?: number | null;
            /** @description Rupees taken against AL encashment when paid. */
            annualLeaveOffset?: number | null;
            /** @description Snapshot collateral check, rupees. */
            gratuityAtRequest?: number | null;
            /** Format: date */
            receivingDate?: string | null;
            description?: string | null;
        };
        PermanentAdvanceDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. PA-2026-0042. */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            amount?: number;
            monthlyDeduction?: number;
            bonusOffset?: number;
            annualLeaveOffset?: number;
            gratuityAtRequest?: number | null;
            /** Format: date */
            receivingDate?: string | null;
            description?: string | null;
            /** @enum {string} */
            status?: "PENDING" | "HR_APPROVED" | "APPROVED" | "REJECTED" | "DISBURSED" | "CLOSED" | "CANCELLED";
            /** @description Sum of all deductions applied so far, rupees. */
            deductedTotal?: number;
            /** @description Outstanding balance, rupees = amount - deductedTotal. */
            remainingBalance?: number;
            /** Format: int64 */
            hrDecidedBy?: number | null;
            /** Format: date-time */
            hrDecidedAt?: string | null;
            hrNote?: string | null;
            /** Format: int64 */
            accountsDecidedBy?: number | null;
            /** Format: date-time */
            accountsDecidedAt?: string | null;
            accountsNote?: string | null;
            rejectionReason?: string | null;
            /**
             * Format: date-time
             * @description §7.payroll #38 — populated on disburse(). null until then.
             */
            disbursedAt?: string | null;
            /**
             * Format: int64
             * @description §7.payroll #38 — userId of the disbursing actor.
             */
            disbursedBy?: number | null;
            /** Format: date-time */
            closedAt?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Manual / off-cycle ledger entry for a permanent advance. The payroll runner writes its own rows directly via the service helper; this endpoint covers out-of-band deductions HR records by hand. */
        RecordPermanentAdvanceDeductionRequest: {
            /** @description Rupees. */
            deductedAmount: number;
            bonusDeduction?: number | null;
            annualLeaveDeduction?: number | null;
            /**
             * Format: int64
             * @description Optional payroll run id this deduction belongs to.
             */
            payrollRunId?: number | null;
            /** Format: date */
            transactionDate: string;
            description?: string | null;
        };
        PermanentAdvanceDeductionDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            advanceId?: number;
            /** Format: int64 */
            payrollRunId?: number | null;
            deductedAmount?: number;
            bonusDeduction?: number;
            annualLeaveDeduction?: number;
            remainingBalance?: number;
            /** Format: date */
            transactionDate?: string;
            description?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        HoldPayslipRequest: {
            reason: string;
        };
        PayslipDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            payrollRunId?: number;
            /** Format: int64 */
            employeeId?: number;
            employeeCode?: string;
            /**
             * Format: int32
             * @description Calendar year of the run period.
             * @example 2026
             */
            periodYear?: number;
            /**
             * Format: int32
             * @description Calendar month of the run period (1-12).
             * @example 4
             */
            periodMonth?: number;
            /**
             * @description Fiscal year code of the run period.
             * @example FY26
             */
            fiscalYearCode?: string;
            basic?: number;
            allowances?: number;
            gross?: number;
            deductions?: number;
            tax?: number;
            eobi?: number;
            pessiEmployer?: number;
            net?: number;
            lopDays?: number;
            /** Format: int32 */
            workingDays?: number | null;
            paidDays?: number | null;
            absentDays?: number | null;
            /** Format: int32 */
            holidaysInPeriod?: number | null;
            /**
             * Format: int32
             * @description Count of WEEKEND-status days in the period. Renamed from sundaysInPeriod (§7.payroll #25); the system supports any day-of-week as the weekly off.
             */
            weekendsInPeriod?: number | null;
            otHours?: number;
            otAmount?: number;
            pdfObjectKey?: string | null;
            /** Format: date-time */
            generatedAt?: string | null;
            /** @enum {string} */
            status?: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
            heldReason?: string | null;
            /** Format: int64 */
            releasedInRunId?: number | null;
            /** @description Optional — included only on detail reads. */
            lines?: components["schemas"]["PayslipLineDto"][] | null;
        };
        PayslipLineDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            componentId?: number | null;
            code?: string;
            name?: string;
            /** @enum {string} */
            type?: "EARNING" | "DEDUCTION";
            /** @enum {string} */
            calcType?: "FLAT" | "PER_DAY";
            /** @description Per-day rate in rupees (PER_DAY only). */
            rate?: number | null;
            /** @description Days multiplier (PER_DAY only); fractional for half-days. */
            days?: number | null;
            /** @description Final line amount in rupees. */
            amount?: number;
            /** Format: int32 */
            displayOrder?: number;
        };
        /** @description Status snapshot of a durable saga row. */
        SagaResponse: {
            /** Format: int64 */
            id?: number;
            sagaName?: string;
            correlationKey?: string;
            /** @description RUNNING | COMPLETED | FAILED. */
            status?: string;
            currentStep?: string;
            /** Format: int32 */
            stepIndex?: number;
            /** Format: int32 */
            attemptCount?: number;
            /** Format: date-time */
            nextAttemptAt?: string;
            lastError?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
            createdBy?: string;
        };
        CreatePayrollRunRequest: {
            /** Format: int32 */
            year: number;
            /** Format: int32 */
            month: number;
        };
        PayrollRunDto: {
            /** Format: int64 */
            id?: number;
            fiscalYearCode?: string;
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            /** @enum {string} */
            status?: "DRAFT" | "LOCKED" | "COMPUTING" | "COMPUTED" | "APPROVED" | "POSTING" | "POSTED" | "FAILED" | "CANCELLED";
            attendanceLocked?: boolean;
            /** Format: date-time */
            computedAt?: string | null;
            /** Format: date-time */
            approvedAt?: string | null;
            /** Format: date-time */
            postedAt?: string | null;
            /** Format: int64 */
            approvedBy?: number | null;
            notes?: string | null;
            /**
             * Format: int32
             * @description Count of employees compute could not produce a payslip for. Drill-in via GET /payroll/runs/{id}/skips.
             */
            skippedCount?: number;
            /**
             * Format: int32
             * @description Rows processed by the currently-running step (compute or post). Updated every ~25 rows. Reset to 0 between steps and on /reset.
             */
            progressCount?: number;
            /**
             * Format: int32
             * @description Total candidates for the currently-running step. Set at saga kickoff: active payroll settings count for compute, unrendered payslip count for post. FE renders progressCount / totalCount.
             */
            totalCount?: number;
        };
        /** @description Re-run compute for previously-skipped employees on a COMPUTED run. Empty/omitted `employeeIds` retries every skip row on the run; otherwise only the listed employees are retried. Ids that aren't currently skipped are quietly ignored. */
        RetrySkipsRequest: {
            /** @description Subset of employees to retry; omit or pass an empty list to retry every skip. */
            employeeIds?: number[] | null;
        };
        /** @description Inserts a new effective-dated row and closes the previous one. */
        CreatePayrollConfigRequest: {
            /** Format: date */
            effectiveFrom: string;
            /** @description Monthly EOBI deduction in rupees. */
            eobiAmount: number;
            /** @example 6 */
            pessiRatePercent: number;
            /** @description PESSI cap in rupees. */
            pessiSalaryCap: number;
            /** Format: int32 */
            gratuityMinServiceMonths?: number;
            /** Format: int32 */
            gratuityPartialYearThresholdMonths?: number;
            /** @description Canteen meal price (rupees per meal) for MANAGEMENT-grade employees. Defaults to 0 when omitted. */
            canteenPerDayRateManagement?: number | null;
            /** @description Canteen meal price (rupees per meal) for SUPERVISOR / LABOUR grades. Defaults to 0 when omitted. */
            canteenPerDayRateOther?: number | null;
            notes?: string;
        };
        PayrollConfigDto: {
            /** Format: int64 */
            id?: number;
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            effectiveTo?: string | null;
            /** @description Monthly EOBI deduction in rupees (employee share). */
            eobiAmount?: number;
            pessiRatePercent?: number;
            /** @description PESSI cap on gross in rupees. */
            pessiSalaryCap?: number;
            /** Format: int32 */
            gratuityMinServiceMonths?: number;
            /** Format: int32 */
            gratuityPartialYearThresholdMonths?: number;
            /** @description Canteen meal price (rupees per meal) for MANAGEMENT-grade employees. Compute charges weeks-with-canteen × (rate × 7). */
            canteenPerDayRateManagement?: number;
            /** @description Canteen meal price (rupees per meal) for SUPERVISOR / LABOUR grades. Compute charges days × rate. */
            canteenPerDayRateOther?: number;
            notes?: string | null;
        };
        CreateOffCyclePaymentRequest: {
            /** Format: int64 */
            employeeId: number;
            /** Format: date */
            paidOn: string;
            amount: number;
            /** @enum {string} */
            category: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            reason: string;
            /** @enum {string} */
            paymentMode: "CASH" | "BANK" | "OTHER";
            referenceNo?: string;
        };
        OffCyclePaymentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            paidOn?: string;
            amount?: number;
            /** @enum {string} */
            category?: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            reason?: string;
            /** @enum {string} */
            paymentMode?: "CASH" | "BANK" | "OTHER";
            referenceNo?: string | null;
            /** Format: int64 */
            sourceEventId?: number | null;
            /** Format: int64 */
            sourceInstallmentId?: number | null;
        };
        /** @description Cash out unused annual leaves. Amount = days × (basicSalary / 26). One row per (employee, calculationMonth). */
        CreateLeaveEncashmentRequest: {
            /** Format: int64 */
            employeeId: number;
            /**
             * Format: date
             * @description 1st of the period start month.
             */
            calculationMonth: string;
            /**
             * Format: int32
             * @description Period length in months — usually 12 (annual cycle).
             * @example 12
             */
            periodInMonths: number;
            daysEncashed: number;
            /** @description Snapshot basic salary used for the calc, rupees. */
            basicSalary: number;
            description?: string | null;
        };
        LeaveEncashmentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            calculationMonth?: string;
            /** Format: int32 */
            periodInMonths?: number;
            daysEncashed?: number;
            /** @description Snapshot basic at calc time, rupees. */
            basicSalary?: number;
            /** @description Computed amount, rupees. */
            amount?: number;
            /** @enum {string} */
            paymentStatus?: "PENDING" | "PAID" | "CANCELLED";
            /** Format: date-time */
            paidAt?: string | null;
            /** Format: int64 */
            payrollRunId?: number | null;
            description?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        MarkLeaveEncashmentPaidRequest: {
            /**
             * Format: int64
             * @description Optional payroll run id when paid as part of a regular run. Leave null for off-cycle / cash payouts.
             */
            payrollRunId?: number | null;
        };
        CreateFiscalYearRequest: {
            /** @example 2026-27 */
            code: string;
            /** Format: date */
            startDate: string;
            /** Format: date */
            endDate: string;
        };
        FiscalYearDto: {
            /** @example 2025-26 */
            code?: string;
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string;
            /** @enum {string} */
            status?: "CLOSED" | "CURRENT" | "FUTURE";
        };
        AddSalaryLineRequest: {
            /** Format: int64 */
            componentId: number;
            /**
             * @description Rupees. Optional — defaults to component.defaultAmount when omitted.
             * @example 15000
             */
            amount?: number | null;
            /** Format: date */
            effectiveFrom: string;
            notes?: string;
        };
        EmployeeSalaryComponentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            componentId?: number;
            /** @example HRA */
            componentCode?: string;
            componentName?: string;
            /** @enum {string} */
            type?: "EARNING" | "DEDUCTION";
            /** @enum {string} */
            calcType?: "FLAT" | "PER_DAY";
            /** @description Rupees. Monthly for FLAT, per-day for PER_DAY. */
            amount?: number;
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            effectiveTo?: string | null;
            notes?: string | null;
        };
        /** @description Sets effective_to on the current row. */
        RemoveSalaryLineRequest: {
            /** Format: date */
            effectiveTo: string;
        };
        CreateBonusEventRequest: {
            name: string;
            /** @enum {string} */
            eventType: "EID_UL_FITR" | "EID_UL_ADHA" | "OTHER";
            fiscalYearCode: string;
            /** Format: date */
            referenceDate: string;
            /** Format: int32 */
            fullServiceDaysThreshold?: number;
            notes?: string;
        };
        /** @description Frozen per-employee snapshot at activation. */
        BonusEventAmountDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            bonusEventId?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            hireDate?: string;
            /** Format: int32 */
            serviceDays?: number;
            basicSnapshot?: number;
            /** @description 100% bonus amount in rupees. */
            fullBonus?: number;
        };
        BonusEventDto: {
            /** Format: int64 */
            id?: number;
            fiscalYearCode?: string;
            name?: string;
            /** @enum {string} */
            eventType?: "EID_UL_FITR" | "EID_UL_ADHA" | "OTHER";
            /** Format: date */
            referenceDate?: string;
            /** Format: int32 */
            fullServiceDaysThreshold?: number;
            /** @enum {string} */
            status?: "DRAFT" | "ACTIVE" | "CLOSED";
            notes?: string | null;
            /** Format: date-time */
            activatedAt?: string | null;
            /** Format: int64 */
            activatedBy?: number | null;
            installments?: components["schemas"]["BonusInstallmentDto"][] | null;
            amounts?: components["schemas"]["BonusEventAmountDto"][] | null;
        };
        BonusInstallmentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            bonusEventId?: number;
            /** Format: int32 */
            installmentNo?: number;
            percentage?: number;
            /** Format: date */
            paidOn?: string;
            /** @enum {string} */
            status?: "DRAFT" | "APPLIED";
            /** Format: date-time */
            appliedAt?: string | null;
            /** Format: int64 */
            appliedBy?: number | null;
        };
        CreateBonusInstallmentRequest: {
            /** Format: int32 */
            installmentNo: number;
            percentage: number;
            /** Format: date */
            paidOn: string;
        };
        ApplyBonusInstallmentRequest: {
            /**
             * @description Defaults to BANK when omitted.
             * @enum {string}
             */
            paymentMode?: "CASH" | "BANK" | "OTHER";
        };
        /** @description HR/Accounts create payload for the bank catalog. The same row carries the company's own account at this bank — payroll runs read iban / accountNumber as the disbursement source. `code` must be unique and is immutable post-create. */
        CreateBankRequest: {
            /** @example MCB */
            code: string;
            /** @example MCB Bank Limited */
            name: string;
            branch?: string | null;
            address?: string | null;
            /** @description Title of the company's own account at this bank. */
            accountTitle?: string | null;
            /** @description Pakistan IBAN, 24 chars. */
            iban?: string | null;
            accountNumber?: string | null;
        };
        /** @description Bank catalog row. Carries the company's own account at this bank (payroll disbursement source) and serves as the controlled list employees pick from. */
        BankDto: {
            /** Format: int64 */
            id?: number;
            code?: string;
            name?: string;
            branch?: string | null;
            address?: string | null;
            /** @description Title of the company's account at this bank. */
            accountTitle?: string | null;
            iban?: string | null;
            accountNumber?: string | null;
            active?: boolean;
        };
        /** @description DRAFT-only. */
        UpdateIncrementBatchRequest: {
            name?: string;
            /** Format: date */
            effectiveFrom?: string;
            notes?: string;
        };
        /** @description DRAFT-only. */
        UpdateIncrementBatchLineRequest: {
            newBasic?: number;
            newIncomeTax?: number;
            /** Format: date */
            effectiveFrom?: string;
        };
        /** @description Null fields are left unchanged. */
        UpdateSalaryComponentRequest: {
            name?: string;
            defaultAmount?: number;
            /** Format: int32 */
            displayOrder?: number;
            active?: boolean;
        };
        /** @description One entry of a bulk reorder payload — the new displayOrder for a catalog row. */
        ReorderEntryRequest: {
            /**
             * Format: int64
             * @description Catalog row id.
             * @example 42
             */
            id: number;
            /**
             * Format: int32
             * @description New display order. Lower sorts first.
             * @example 20
             */
            displayOrder: number;
        };
        /** @description Null fields are left unchanged. */
        UpdateOffCyclePaymentRequest: {
            amount?: number;
            /** @enum {string} */
            category?: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            reason?: string;
            /** @enum {string} */
            paymentMode?: "CASH" | "BANK" | "OTHER";
            referenceNo?: string;
            /** Format: date */
            paidOn?: string;
        };
        UpdateFiscalYearRequest: {
            /** @enum {string} */
            status: "CLOSED" | "CURRENT" | "FUTURE";
        };
        /** @description Closes the current row and inserts a new one with the new amount. */
        UpdateSalaryLineRequest: {
            amount: number;
            /** Format: date */
            effectiveFrom: string;
            notes?: string;
        };
        /** @description Null fields are left unchanged. */
        UpdateEmployeePayrollSettingsRequest: {
            eobiEnrolled?: boolean;
            /** @enum {string} */
            paymentMode?: "CASH" | "BANK_TRANSFER";
            /**
             * Format: date
             * @description When gratuity accrual began. Usually equals hireDate but can differ — set explicitly when prior service is recognised. Locked once set.
             */
            gratuityStartDate?: string | null;
            /**
             * Format: date
             * @description When senior-allowance eligibility began. Null = ineligible. Set non-null to start accruing the seniority-tied allowance from that date.
             */
            seniorAllowanceStartDate?: string | null;
        };
        EmployeePayrollSettingsDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            hireDate?: string;
            eobiEnrolled?: boolean;
            /** @enum {string} */
            paymentMode?: "CASH" | "BANK_TRANSFER";
            /**
             * Format: date
             * @description When gratuity accrual began. Falls back to hireDate when null.
             */
            gratuityStartDate?: string | null;
            /**
             * Format: date
             * @description When senior-allowance eligibility began. Null = ineligible.
             */
            seniorAllowanceStartDate?: string | null;
        };
        /** @description DRAFT-only. Null fields are left unchanged. */
        UpdateBonusEventRequest: {
            name?: string;
            /** Format: date */
            referenceDate?: string;
            /** Format: int32 */
            fullServiceDaysThreshold?: number;
            notes?: string;
        };
        /** @description DRAFT-only. */
        UpdateBonusInstallmentRequest: {
            percentage?: number;
            /** Format: date */
            paidOn?: string;
        };
        /** @description Patch a bank catalog row. Null fields are unchanged. `code` is immutable. */
        UpdateBankRequest: {
            name?: string | null;
            branch?: string | null;
            address?: string | null;
            accountTitle?: string | null;
            iban?: string | null;
            accountNumber?: string | null;
            active?: boolean | null;
        };
        PageResponseShortTermAdvanceBatchDto: {
            content?: components["schemas"]["ShortTermAdvanceBatchDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Read-only simulation. Same shape as the eventual termination payslip. */
        SettlementPreviewDto: {
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            lastWorkingDay?: string;
            earnedSalary?: components["schemas"]["SettlementPreviewDto.EarnedSalary"];
            gratuity?: components["schemas"]["SettlementPreviewDto.Gratuity"];
            outstandingDeductions?: components["schemas"]["SettlementPreviewDto.OutstandingDeduction"][];
            tax?: number;
            net?: number;
        };
        "SettlementPreviewDto.EarnedSalary": {
            basic?: number;
            allowances?: number;
            gross?: number;
        };
        "SettlementPreviewDto.Gratuity": {
            eligible?: boolean;
            /** Format: int32 */
            completedYears?: number;
            amount?: number;
        };
        "SettlementPreviewDto.OutstandingDeduction": {
            code?: string;
            amount?: number;
        };
        /** @description Compact employee row for resolving names against an id list. Used by the approvals inbox, payroll composites, attendance composites, and any surface that comes back keyed by employee id without names attached. */
        EmployeeSummary: {
            /**
             * Format: int64
             * @description Employee id.
             */
            id?: number;
            /** @description Display name. */
            fullName?: string;
            /** @description Human-readable employee code (e.g. FS-2026-0042). Also the RFID/punch-card number used by attendance devices. */
            employeeCode?: string;
            /**
             * Format: int64
             * @description Department id.
             */
            departmentId?: number | null;
            /** @description Department name, looked up from the catalog. */
            departmentName?: string | null;
            /**
             * Format: int64
             * @description Section id.
             */
            sectionId?: number | null;
            /** @description Section name, looked up from the catalog. */
            sectionName?: string | null;
            /**
             * Format: int64
             * @description Designation id.
             */
            designationId?: number | null;
            /** @description Designation title, looked up from the catalog. */
            designationName?: string | null;
            /** @description Signed URL for the employee's profile photo. */
            profilePictureUrl?: string | null;
        };
        IncrementBatchDetail: {
            /** Format: int64 */
            id?: number;
            name?: string;
            status?: string;
            /** Format: date */
            effectiveFrom?: string;
            notes?: string | null;
            /** Format: date-time */
            appliedAt?: string | null;
            /** Format: int64 */
            appliedBy?: number | null;
            lines?: components["schemas"]["IncrementBatchLineWithEmployee"][];
        };
        /** @description Increment-batch line with the employee resolved to a name + code. */
        IncrementBatchLineWithEmployee: {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            oldBasic?: number;
            newBasic?: number;
            oldIncomeTax?: number | null;
            newIncomeTax?: number | null;
            /** Format: date */
            effectiveFrom?: string | null;
            status?: string;
            errorMessage?: string | null;
        };
        StreamingResponseBody: unknown;
        PdfUrlResponse: {
            /** @description Short-lived signed MinIO URL. */
            url?: string;
        };
        PageResponsePermanentAdvanceDto: {
            content?: components["schemas"]["PermanentAdvanceDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponsePayslipDto: {
            content?: components["schemas"]["PayslipDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponsePayslipQueueRow: {
            content?: components["schemas"]["PayslipQueueRow"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PayslipQueueResponse: {
            status?: string;
            page?: components["schemas"]["PageResponsePayslipQueueRow"];
        };
        /** @description Disbursement queue row: HELD / RELEASE_PENDING payslips with run period and employee resolved. */
        PayslipQueueRow: {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            fiscalYearCode?: string;
            net?: number;
            status?: string;
            heldReason?: string | null;
            /** Format: date-time */
            generatedAt?: string | null;
        };
        /** @description Self-serve payslip row: the upstream payslip plus a short-lived signed PDF URL when generated. */
        MyPayslipRow: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            payrollRunId?: number;
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            fiscalYearCode?: string;
            basic?: number;
            allowances?: number;
            gross?: number;
            deductions?: number;
            tax?: number;
            eobi?: number;
            pessiEmployer?: number;
            net?: number;
            lopDays?: number;
            otHours?: number;
            otAmount?: number;
            /** Format: date-time */
            generatedAt?: string | null;
            status?: string;
            heldReason?: string | null;
            /** @description Short-lived signed MinIO URL — null when no PDF yet. */
            pdfUrl?: string | null;
        };
        MyPayslipsResponse: {
            /** Format: int64 */
            employeeId?: number;
            /** Format: int32 */
            year?: number;
            payslips?: components["schemas"]["PageResponseMyPayslipRow"];
        };
        PageResponseMyPayslipRow: {
            content?: components["schemas"]["MyPayslipRow"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponseSagaResponse: {
            content?: components["schemas"]["SagaResponse"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PerName: {
            name?: string;
            status?: string;
            failureRate24h?: number[];
        };
        SagaHealthSnapshot: {
            /** Format: int32 */
            running?: number;
            /** Format: int32 */
            failed24h?: number;
            perName?: components["schemas"]["PerName"][];
        };
        /** @description Pre-aggregated totals for a payroll run, optionally filtered by status / employeeCode substring. */
        PayrollRunTotalsDto: {
            /**
             * Format: int64
             * @description Run id.
             */
            runId?: number;
            /**
             * Format: int64
             * @description Number of payslips matching the filter.
             */
            employeeCount?: number;
            /** @description Sum of basic. */
            basic?: number;
            /** @description Sum of allowances. */
            allowances?: number;
            /** @description Sum of gross. */
            gross?: number;
            /** @description Sum of non-tax deductions. */
            deductions?: number;
            /** @description Sum of income tax. */
            tax?: number;
            /** @description Sum of EOBI deductions. */
            eobi?: number;
            /** @description Sum of PESSI employer contributions (cost only, not deducted). */
            pessiEmployer?: number;
            /** @description Sum of net. */
            net?: number;
            /**
             * Format: int64
             * @description Count of HELD payslips within the filter.
             */
            heldCount?: number;
            /**
             * Format: int64
             * @description Count of RELEASE_PENDING payslips within the filter.
             */
            releasePendingCount?: number;
        };
        /** @description One-shot polling shape: status + live counts + aggregated payslip totals. */
        PayrollRunSummaryDto: {
            /** Format: int64 */
            id?: number;
            fiscalYearCode?: string;
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            /** @enum {string} */
            status?: "DRAFT" | "LOCKED" | "COMPUTING" | "COMPUTED" | "APPROVED" | "POSTING" | "POSTED" | "FAILED" | "CANCELLED";
            /** Format: date-time */
            computedAt?: string | null;
            /** Format: date-time */
            postedAt?: string | null;
            /**
             * Format: int32
             * @description Total candidates for the currently-running step. Set at saga kickoff.
             */
            totalCount?: number;
            /**
             * Format: int32
             * @description Rows processed so far (succeeded + skipped). Pulsed every ~25 rows.
             */
            progressCount?: number;
            /**
             * Format: int32
             * @description Rows compute could not produce a payslip for so far. Pulsed every ~25 rows.
             */
            skippedCount?: number;
            /**
             * Format: int32
             * @description processed minus skipped. Server-derived so the FE doesn't have to subtract.
             */
            successCount?: number;
            /** @description Pre-aggregated payslip sums (basic, allowances, gross, deductions, tax, eobi, pessiEmployer, net) plus HELD / RELEASE_PENDING counts. Live during compute because payslips commit per-row. */
            totals?: components["schemas"]["PayrollRunTotalsDto"];
        };
        PageResponsePayrollRunSkipDto: {
            content?: components["schemas"]["PayrollRunSkipDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description An employee that compute could not produce a payslip for, with the actionable reason. FE drives the fix-and-retry workflow off these rows. */
        PayrollRunSkipDto: {
            /**
             * Format: int64
             * @description Skip-row id (stable across paginated reads).
             */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            employeeCode?: string;
            employeeFullName?: string;
            /** @description Stable error code; FE maps to a deeplink (e.g. NO_EFFECTIVE_SALARY → salary page). */
            errorCode?: string;
            /** @description Human-readable reason from the compute step. */
            message?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        PageResponsePayslipWithEmployee: {
            content?: components["schemas"]["PayslipWithEmployee"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PayrollRunDetail: {
            run?: components["schemas"]["PayrollRunDto"];
            summary?: components["schemas"]["PayrollRunSummary"];
            payslips?: components["schemas"]["PageResponsePayslipWithEmployee"];
        };
        /** @description Server-side rollup over a run's payslips so the FE doesn't sum 100s of rows client-side. */
        PayrollRunSummary: {
            /** Format: int32 */
            employees?: number;
            basic?: number;
            allowances?: number;
            gross?: number;
            deductions?: number;
            tax?: number;
            eobi?: number;
            pessiEmployer?: number;
            net?: number;
            /** Format: int32 */
            held?: number;
            /** Format: int32 */
            releasePending?: number;
        };
        /** @description Run-detail payslip row enriched with the employee summary so the table can render the name and department without a fan-out. */
        PayslipWithEmployee: {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            gross?: number;
            deductions?: number;
            tax?: number;
            eobi?: number;
            pessiEmployer?: number;
            net?: number;
            status?: string;
            heldReason?: string | null;
        };
        PayrollRunsListItem: {
            run?: components["schemas"]["PayrollRunDto"];
            summary?: components["schemas"]["PayrollRunSummary"];
        };
        PayrollRunsListResponse: {
            fiscalYearCode?: string;
            runs?: components["schemas"]["PayrollRunsListItem"][];
        };
        /** @description One-shot bundle for the per-employee salary tab — replaces 8 fan-out calls. */
        EmployeeSalaryTab: {
            /** Format: int64 */
            employeeId?: number;
            currentBasic?: components["schemas"]["EmployeeSalaryDto"];
            basicHistory?: components["schemas"]["EmployeeSalaryDto"][];
            components?: components["schemas"]["EmployeeSalaryComponentDto"][];
            catalog?: components["schemas"]["SalaryComponentDto"][];
            payrollSettings?: components["schemas"]["EmployeePayrollSettingsDto"];
            bankDetails?: components["schemas"]["EmployeeBankDetailsDto"];
            gratuity?: components["schemas"]["GratuityDto"];
            offCycle12Months?: components["schemas"]["OffCyclePaymentDto"][];
        };
        /** @description Read-only gratuity calculator. Computed on demand from gratuity_start_date (falls back to hire_date) + current effective basic + payroll_config. */
        GratuityDto: {
            /** Format: int64 */
            employeeId?: number;
            /**
             * Format: date
             * @description Gratuity start date — falls back to hireDate when no override is set on employee_payroll_settings.
             */
            gratuityStartDate?: string;
            /** Format: date */
            hireDate?: string;
            /** Format: date */
            asOf?: string;
            /** Format: int32 */
            totalServiceMonths?: number;
            eligible?: boolean;
            /** Format: int32 */
            completedYears?: number;
            lastBasic?: number;
            gratuity?: number;
            /** Format: int64 */
            configId?: number;
        };
        PageResponseOffCyclePaymentDto: {
            content?: components["schemas"]["OffCyclePaymentDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Off-cycle payment row enriched with the employee summary. */
        OffCyclePaymentRowWithEmployee: {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            /** Format: date */
            paidOn?: string;
            amount?: number;
            category?: string;
            reason?: string;
            paymentMode?: string;
            referenceNo?: string | null;
            /** Format: int64 */
            sourceEventId?: number | null;
            /** Format: int64 */
            sourceInstallmentId?: number | null;
        };
        PageResponseOffCyclePaymentRowWithEmployee: {
            content?: components["schemas"]["OffCyclePaymentRowWithEmployee"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponseLeaveEncashmentDto: {
            content?: components["schemas"]["LeaveEncashmentDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        BonusEventAmountWithEmployee: {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            /** Format: date */
            hireDate?: string;
            /** Format: int32 */
            serviceDays?: number;
            basicSnapshot?: number;
            fullBonus?: number;
        };
        BonusEventDetail: {
            /** Format: int64 */
            id?: number;
            fiscalYearCode?: string;
            name?: string;
            eventType?: string;
            /** Format: date */
            referenceDate?: string;
            /** Format: int32 */
            fullServiceDaysThreshold?: number;
            status?: string;
            notes?: string | null;
            /** Format: date-time */
            activatedAt?: string | null;
            /** Format: int64 */
            activatedBy?: number | null;
            installments?: components["schemas"]["BonusInstallmentDto"][];
            amounts?: components["schemas"]["BonusEventAmountWithEmployee"][];
        };
        Percentiles: {
            min?: number;
            p25?: number;
            median?: number;
            p75?: number;
            max?: number;
            /** Format: int32 */
            count?: number;
        };
        SalaryDistributionDto: {
            percentiles?: components["schemas"]["Percentiles"];
            rawBasics?: number[];
        };
        CostMonth: {
            month?: string;
            /** Format: int64 */
            employeeCount?: number;
            gross?: number;
            eobi?: number;
            pessiEmployer?: number;
            tax?: number;
            net?: number;
            totalCost?: number;
            costPerHead?: number;
        };
        CostTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["CostMonth"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    getBankDetails: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeBankDetailsDto"];
                };
            };
        };
    };
    putBankDetails: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpsertBankDetailsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeBankDetailsDto"];
                };
            };
        };
    };
    deleteBankDetails: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    list: {
        parameters: {
            query?: {
                status?: "PENDING" | "HR_APPROVED" | "APPROVED" | "REJECTED" | "DISBURSED" | "CLOSED" | "CANCELLED";
                year?: number;
                month?: number;
                departmentId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateShortTermAdvanceBatchRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdvanceRejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    hrApprove: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["AdvanceDecisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    disburse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    accountsApprove: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["AdvanceDecisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                status?: "DRAFT" | "APPLIED" | "PARTIALLY_APPLIED" | "CANCELLED";
                fiscalYear?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"][];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateIncrementBatchRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"];
                };
            };
        };
    };
    addLine: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateIncrementBatchLineRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchLineDto"];
                };
            };
        };
    };
    cancel_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"];
                };
            };
        };
    };
    apply: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"];
                };
            };
        };
    };
    current: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryDto"];
                };
            };
        };
    };
    revise: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReviseBasicSalaryRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryDto"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query?: {
                type?: "EARNING" | "DEDUCTION";
                active?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalaryComponentDto"][];
                };
            };
        };
    };
    create_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSalaryComponentRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalaryComponentDto"];
                };
            };
        };
    };
    enqueuePayslipsRun: {
        parameters: {
            query: {
                /** @description Payroll run id. */
                payrollRunId: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Job queued. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportJobDto"];
                };
            };
        };
    };
    enqueuePayRegister: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PayRegisterRequest"];
            };
        };
        responses: {
            /** @description Job queued. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportJobDto"];
                };
            };
        };
    };
    enqueueIncrementLetters: {
        parameters: {
            query: {
                /** @description Increment-batch id (POSTED). */
                incrementBatchId: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Job queued. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportJobDto"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query?: {
                employeeId?: number;
                status?: "PENDING" | "HR_APPROVED" | "APPROVED" | "REJECTED" | "DISBURSED" | "CLOSED" | "CANCELLED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePermanentAdvanceDto"];
                };
            };
        };
    };
    create_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreatePermanentAdvanceRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    reject_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdvanceRejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    hrApprove_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["AdvanceDecisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    disburse_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    listDeductions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDeductionDto"][];
                };
            };
        };
    };
    recordDeduction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RecordPermanentAdvanceDeductionRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDeductionDto"];
                };
            };
        };
    };
    cancel_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    accountsApprove_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["AdvanceDecisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    unhold: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HoldPayslipRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayslipDto"];
                };
            };
        };
    };
    release: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HoldPayslipRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayslipDto"];
                };
            };
        };
    };
    hold: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HoldPayslipRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayslipDto"];
                };
            };
        };
    };
    retry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Saga re-armed (or unchanged if not FAILED). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagaResponse"];
                };
            };
        };
    };
    list_4: {
        parameters: {
            query: {
                fiscalYear: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"][];
                };
            };
        };
    };
    create_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreatePayrollRunRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    retrySkips: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RetrySkipsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    reset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    lockAttendance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    compute: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    cancel_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    approve: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    current_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollConfigDto"];
                };
            };
        };
    };
    create_5: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreatePayrollConfigRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollConfigDto"];
                };
            };
        };
    };
    list_5: {
        parameters: {
            query?: {
                employeeId?: number;
                from?: string;
                to?: string;
                category?: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseOffCyclePaymentDto"];
                };
            };
        };
    };
    create_6: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateOffCyclePaymentRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OffCyclePaymentDto"];
                };
            };
        };
    };
    list_6: {
        parameters: {
            query?: {
                employeeId?: number;
                status?: "PENDING" | "PAID" | "CANCELLED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseLeaveEncashmentDto"];
                };
            };
        };
    };
    create_7: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateLeaveEncashmentRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveEncashmentDto"];
                };
            };
        };
    };
    markPaid: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["MarkLeaveEncashmentPaidRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveEncashmentDto"];
                };
            };
        };
    };
    cancel_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveEncashmentDto"];
                };
            };
        };
    };
    list_7: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FiscalYearDto"][];
                };
            };
        };
    };
    create_8: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateFiscalYearRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FiscalYearDto"];
                };
            };
        };
    };
    list_8: {
        parameters: {
            query?: {
                type?: "EARNING" | "DEDUCTION";
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryComponentDto"][];
                };
            };
        };
    };
    add: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddSalaryLineRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryComponentDto"];
                };
            };
        };
    };
    remove: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                lineId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RemoveSalaryLineRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    list_9: {
        parameters: {
            query?: {
                fiscalYear?: string;
                status?: "DRAFT" | "ACTIVE" | "CLOSED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"][];
                };
            };
        };
    };
    create_9: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateBonusEventRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"];
                };
            };
        };
    };
    close: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"];
                };
            };
        };
    };
    activate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"];
                };
            };
        };
    };
    addInstallment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                eventId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateBonusInstallmentRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusInstallmentDto"];
                };
            };
        };
    };
    apply_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                eventId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApplyBonusInstallmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusInstallmentDto"];
                };
            };
        };
    };
    list_10: {
        parameters: {
            query?: {
                includeInactive?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"][];
                };
            };
        };
    };
    create_10: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateBankRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"];
                };
            };
            /** @description BANK_CODE_TAKEN. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateIncrementBatchRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDto"];
                };
            };
        };
    };
    deleteLine: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                lineId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    updateLine: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                lineId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateIncrementBatchLineRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchLineDto"];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateSalaryComponentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalaryComponentDto"];
                };
            };
        };
    };
    reorder: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReorderEntryRequest"][];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalaryComponentDto"][];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OffCyclePaymentDto"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateOffCyclePaymentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OffCyclePaymentDto"];
                };
            };
        };
    };
    setStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                code: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateFiscalYearRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FiscalYearDto"];
                };
            };
        };
    };
    update_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                lineId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateSalaryLineRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryComponentDto"];
                };
            };
        };
    };
    getSettings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeePayrollSettingsDto"];
                };
            };
        };
    };
    updateSettings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmployeePayrollSettingsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeePayrollSettingsDto"];
                };
            };
        };
    };
    get_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"];
                };
            };
        };
    };
    delete_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateBonusEventRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDto"];
                };
            };
        };
    };
    deleteInstallment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                eventId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    updateInstallment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                eventId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateBonusInstallmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusInstallmentDto"];
                };
            };
        };
    };
    get_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"];
                };
            };
        };
    };
    deactivate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"];
                };
            };
        };
    };
    update_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateBankRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BankDto"];
                };
            };
        };
    };
    get_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceBatchDto"];
                };
            };
        };
    };
    listLines: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortTermAdvanceLineDto"][];
                };
            };
        };
    };
    preview: {
        parameters: {
            query?: {
                lastWorkingDay?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SettlementPreviewDto"];
                };
            };
        };
    };
    incrementBatch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IncrementBatchDetail"];
                };
            };
        };
    };
    history: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryDto"][];
                };
            };
        };
    };
    variance: {
        parameters: {
            query: {
                /** @description Current payroll run id. */
                currentRunId: number;
                /** @description Optional explicit prior run id; default = same-FY immediate predecessor. */
                priorRunId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description XLSX stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description Run not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description No prior run, or prior == current. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    pessiReturn: {
        parameters: {
            query: {
                /** @description Period year, e.g. 2026. */
                year: number;
                /** @description Period month 1..12. */
                month: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No payroll run for the requested period. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/pdf": string;
                };
            };
        };
    };
    payslipPdfUrl: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Payslip or PDF not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PdfUrlResponse"];
                };
            };
        };
    };
    offCyclePayments: {
        parameters: {
            query: {
                /** @description Window start (yyyy-mm-dd, inclusive). */
                from: string;
                /** @description Window end (yyyy-mm-dd, inclusive). */
                to: string;
                /** @description Optional category filter. */
                category?: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description XLSX stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description Range invalid (from > to or missing). */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    incomeTaxDeducted: {
        parameters: {
            query: {
                /** @description Period start year. */
                fromYear: number;
                /** @description Period start month 1..12. */
                fromMonth: number;
                /** @description Period end year (inclusive). */
                toYear: number;
                /** @description Period end month 1..12 (inclusive). */
                toMonth: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description XLSX stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
            /** @description Period bounds inverted or cross fiscal years. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    eobiReturn: {
        parameters: {
            query: {
                /** @description Period year, e.g. 2026. */
                year: number;
                /** @description Period month 1..12. */
                month: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No payroll run for the requested period. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/pdf": string;
                };
            };
        };
    };
    advancesOutstanding: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description XLSX stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    get_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Job not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportJobDto"];
                };
            };
        };
    };
    download: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Redirect to signed URL. */
            302: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Job is not READY. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    get_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermanentAdvanceDto"];
                };
            };
        };
    };
    forEmployee: {
        parameters: {
            query?: {
                status?: "PENDING" | "HR_APPROVED" | "APPROVED" | "REJECTED" | "DISBURSED" | "CLOSED" | "CANCELLED";
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePermanentAdvanceDto"];
                };
            };
        };
    };
    filter: {
        parameters: {
            query: {
                status: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePayslipDto"];
                };
            };
        };
    };
    get_7: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Payslip not found (or DRAFT, to a restricted caller). */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayslipDto"];
                };
            };
        };
    };
    pdfUrl: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PdfUrlResponse"];
                };
            };
        };
    };
    team: {
        parameters: {
            query: {
                year: number;
                month?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Caller holds neither payroll.read.team nor payroll.read. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePayslipDto"];
                };
            };
        };
    };
    payslipQueue: {
        parameters: {
            query: {
                /** @description HELD / RELEASE_PENDING / RELEASED / PAID */
                status: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayslipQueueResponse"];
                };
            };
        };
    };
    myPayslips: {
        parameters: {
            query: {
                /** @description Calendar year, e.g. 2026. */
                year: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MyPayslipsResponse"];
                };
            };
        };
    };
    forEmployeeYear: {
        parameters: {
            query: {
                year: number;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePayslipDto"];
                };
            };
        };
    };
    list_11: {
        parameters: {
            query?: {
                /** @description Filter by status (RUNNING / COMPLETED / FAILED). */
                status?: "RUNNING" | "COMPLETED" | "FAILED";
                /** @description Filter by saga name. */
                name?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseSagaResponse"];
                };
            };
        };
    };
    get_8: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Saga row id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Saga snapshot. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagaResponse"];
                };
            };
            /** @description Saga not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagaResponse"];
                };
            };
        };
    };
    health: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Health snapshot. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagaHealthSnapshot"];
                };
            };
        };
    };
    find: {
        parameters: {
            query: {
                /** @description Saga name, e.g. PAYROLL_RUN_POST. */
                name: string;
                /** @description Correlation key, e.g. payroll_run:42. */
                correlationKey: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No saga matches. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagaResponse"];
                };
            };
        };
    };
    get_9: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDto"];
                };
            };
        };
    };
    totals: {
        parameters: {
            query?: {
                status?: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
                q?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunTotalsDto"];
                };
            };
        };
    };
    summary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunSummaryDto"];
                };
            };
        };
    };
    skips: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePayrollRunSkipDto"];
                };
            };
        };
    };
    payslips: {
        parameters: {
            query?: {
                status?: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
                q?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePayslipDto"];
                };
            };
        };
    };
    runDetail: {
        parameters: {
            query?: {
                status?: "DRAFT" | "PAID" | "HELD" | "RELEASE_PENDING" | "RELEASED";
                /** @description Substring on employeeCode. */
                q?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunDetail"];
                };
            };
        };
    };
    runsWithSummary: {
        parameters: {
            query: {
                /** @description Fiscal year code, e.g. FY26. */
                fiscalYear: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollRunsListResponse"];
                };
            };
        };
    };
    salaryTab: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryTab"];
                };
            };
        };
    };
    history_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PayrollConfigDto"][];
                };
            };
        };
    };
    offCyclePaymentsWithEmployee: {
        parameters: {
            query?: {
                employeeId?: number;
                from?: string;
                to?: string;
                category?: "EXTRA" | "SPECIAL_OT" | "BONUS" | "OTHER";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseOffCyclePaymentRowWithEmployee"];
                };
            };
        };
    };
    get_10: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveEncashmentDto"];
                };
            };
        };
    };
    compute_1: {
        parameters: {
            query?: {
                asOf?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GratuityDto"];
                };
            };
        };
    };
    current_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FiscalYearDto"];
                };
            };
        };
    };
    history_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSalaryComponentDto"][];
                };
            };
        };
    };
    list_12: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OffCyclePaymentDto"][];
                };
            };
        };
    };
    bonusEvent: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BonusEventDetail"];
                };
            };
        };
    };
    salaryDistribution: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalaryDistributionDto"];
                };
            };
        };
    };
    costTrend: {
        parameters: {
            query?: {
                months?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CostTrendDto"];
                };
            };
        };
    };
}
