/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/skills": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List skills
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list"];
        put?: never;
        /**
         * Create a skill
         * @description Requires `employee.write`.
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sections
         * @description Filter by department and / or active flag. Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_1"];
        put?: never;
        /**
         * Create a section
         * @description Requires `employee.write`.
         */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sagas/{id}/retry": {
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
         * @description Resets a {@code FAILED} saga to {@code RUNNING} at its current step with {@code attempt_count=0}. No-op for sagas in any other state. Use after fixing the underlying upstream issue. Requires `employee.write`.
         */
        post: operations["retry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/qualifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List qualifications
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_2"];
        put?: never;
        /**
         * Create a qualification
         * @description Requires `employee.write`.
         */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/provinces": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List provinces
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_3"];
        put?: never;
        /**
         * Create a province
         * @description Requires `employee.write`.
         */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/grades": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List grades
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_4"];
        put?: never;
        /**
         * Create a grade
         * @description Requires `employee.write`.
         */
        post: operations["create_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List employees (paginated)
         * @description Standard Spring `page`, `size`, and `sort` query params apply. Sortable fields: `fullName`, `employeeCode`, `joiningDate`, `confirmationDate`, `departmentId`, `sectionId`, `gradeId`, `managerId`, `employmentStatus`, `employmentType`. **Defaults:** when `status` is omitted, TERMINATED rows are hidden — pass `status=TERMINATED` to list only terminated, or `includeTerminated=true` to see every status. When `sort` is omitted, results are ordered by `employeeCode` ascending. When `probationDueWithin` or `probationOverdue=true` is set, the response is narrowed to employees whose ACTIVE PROBATION contract matches the filter (other filters are ignored for these specialized queries) and each row carries an `activeProbation` summary. Requires `employee.read`.
         */
        get: operations["list_5"];
        put?: never;
        /**
         * Onboard an employee
         * @description Single endpoint for both quick-add (modal) and complete-form (page) flows; quick-add sends only the required identity + assignment fields, complete-form additionally supplies salary, shift, and per-row arrays (qualifications, experience, skills, certifications, emergency contacts, disabilities). The kickoff transaction persists the employee + initial PROBATION contract + history seed + every supplied array, emits `employee.created` via outbox, and registers an ONBOARDING saga whose ordered steps (PROVISION_ESS, SET_BASIC_SALARY, ASSIGN_SALARY_COMPONENTS, SET_BANK_DETAILS, ASSIGN_SHIFT) call auth/payroll/attendance with bounded retry. Steps no-op when their section is absent. Response includes `sagaId` whenever any cross-service work was requested; the FE polls `/sagas/{id}` for per-step RUNNING / COMPLETED / FAILED. Requires `employee.write`.
         */
        post: operations["onboard"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/withdraw-exit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Withdraw a pending resignation
         * @description Allowed only while status=PENDING. Restores employment_status to ACTIVE (unless suspended) and emits employee.resignation_withdrawn. Requires `employee.write`.
         */
        post: operations["withdraw"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/terminate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Terminate immediately
         * @description Runs the termination saga right away: marks TERMINATED, lifts any active suspension, closes the history row, and emits employee.terminated. Requires `employee.write`.
         */
        post: operations["terminate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/suspend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Suspend an employee
         * @description Allowed when employment_status is ACTIVE or ON_NOTICE and the employee has no active suspension. Sets status to SUSPENDED and emits employee.suspended. Requires `employee.write`.
         */
        post: operations["suspend"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/skills": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's skills
         * @description Requires `employee.read`.
         */
        get: operations["list_6"];
        put?: never;
        /**
         * Attach a catalog skill to an employee
         * @description Requires `employee.write`.
         */
        post: operations["add"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/section": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change section
         * @description Section must belong to the employee's current department. Requires `employee.write`.
         */
        post: operations["changeSection"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/revoke-ess": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Revoke ESS
         * @description Emits `employee.ess_revoked`; auth-service disables the user and revokes sessions. Requires `employee.admin`.
         */
        post: operations["revokeEss"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/resign": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Submit a resignation
         * @description Inserts a PENDING exit row, flips employment_status to ON_NOTICE, and emits employee.resignation_submitted. The user keeps full ESS through the notice period; auth disables only after the daily completion job emits employee.terminated on the last working day. Direct reports must be reassigned. Requires `employee.write`.
         */
        post: operations["resign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/qualifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's qualifications
         * @description Requires `employee.read`.
         */
        get: operations["list_7"];
        put?: never;
        /**
         * Attach a catalog qualification to an employee
         * @description Requires `employee.write`.
         */
        post: operations["add_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/profile-picture": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Set the profile picture once the upload completes
         * @description Verifies the object exists, deletes the previous picture (best-effort), and updates `employees.profile_picture_key`. Requires `employee.write`.
         */
        post: operations["setProfilePicture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/profile-picture/upload-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mint a pre-signed PUT URL for a new profile picture
         * @description Requires `employee.write`.
         */
        post: operations["uploadUrl"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/prior-experience": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List prior experience (most recent first)
         * @description Requires `employee.read`.
         */
        get: operations["list_8"];
        put?: never;
        /**
         * Add a prior-experience row
         * @description Requires `employee.write`.
         */
        post: operations["add_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/manager": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change manager (cycle-checked)
         * @description Requires `employee.write`.
         */
        post: operations["changeManager"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/lift-suspension": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Lift the active suspension
         * @description Restores employment_status to ON_NOTICE when there's a PENDING exit row, ACTIVE otherwise. Emits employee.suspension_lifted (auth re-enables the user). Requires `employee.write`.
         */
        post: operations["liftSuspension"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/grant-ess": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Grant ESS to an existing employee
         * @description Registers an ESS_GRANT saga whose PROVISION_ESS step calls auth-service to create the user and patches `employees.user_id`. Returns the saga id alongside the employee row; the FE polls `GET /sagas/{id}` for RUNNING / COMPLETED / FAILED. The previous in-tx HTTP call risked an orphaned auth user on a kickoff-tx failure; the saga's idempotency-key (`ess-grant:emp:{id}`) makes retries safe. Requires `employee.admin`.
         */
        post: operations["grantEss"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/grade": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change grade
         * @description Requires `employee.write`.
         */
        post: operations["changeGrade"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/extend-probation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Extend probation (one-time)
         * @description Bumps the same PROBATION row's end_date to start_date + newProbationDays. Allowed values: 120 or 180; must be greater than current probation_days; and the contract's is_extended flag must still be false. Requires `employee.write`.
         */
        post: operations["extendProbation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/emergency-contacts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List emergency contacts (primary first)
         * @description Requires `employee.read`.
         */
        get: operations["list_9"];
        put?: never;
        /**
         * Add an emergency contact
         * @description Requires `employee.write`.
         */
        post: operations["create_5"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/documents": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's documents
         * @description Paginated. Each row carries a short-lived signed download URL the client can use directly. Default page size is 50; ordered newest-first. Requires `employee.read`.
         */
        get: operations["list_10"];
        put?: never;
        /**
         * Record metadata after the upload completes
         * @description Service stats the object in MinIO; if the upload didn't actually happen the call returns 422. Requires `employee.write`.
         */
        post: operations["record"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/documents/upload-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mint a pre-signed PUT URL the client uploads to directly
         * @description Returned `objectKey` must be passed back when recording metadata. URL TTL is configurable via `hrms.employee.documents.upload-url-ttl`. Requires `employee.write`.
         */
        post: operations["uploadUrl_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/disciplinary-actions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's disciplinary actions plus FY warning counts
         * @description Counts in `warningCounts` are UPHELD-only and bounded to the requested fiscal year. Requires `employee.read`.
         */
        get: operations["listForEmployee"];
        put?: never;
        /**
         * Issue a disciplinary action against an employee
         * @description Requires `employee.write`.
         */
        post: operations["issue"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/disabilities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List employee disabilities
         * @description Requires `employee.read`.
         */
        get: operations["list_11"];
        put?: never;
        /**
         * Link a disability to the employee
         * @description Requires `employee.write`.
         */
        post: operations["add_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/designation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change designation
         * @description Requires `employee.write`.
         */
        post: operations["changeDesignation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/department": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change department
         * @description Clears the section assignment if it doesn't belong to the new department. Requires `employee.write`.
         */
        post: operations["changeDepartment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirm an employee out of probation
         * @description Allowed only while a PROBATION contract is ACTIVE. Marks it EXPIRED, inserts a new PERMANENT contract starting today, and sets confirmation_date. Requires `employee.write`.
         */
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/certifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's certifications
         * @description Requires `employee.read`.
         */
        get: operations["list_12"];
        put?: never;
        /**
         * Add a certification
         * @description Requires `employee.write`.
         */
        post: operations["add_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/profile-picture": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Set my profile picture once the upload completes */
        post: operations["setMyProfilePicture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/profile-picture/upload-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Mint a pre-signed PUT URL for my profile picture */
        post: operations["profilePictureUploadUrl"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/emergency-contacts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List my emergency contacts (primary first) */
        get: operations["myEmergencyContacts"];
        put?: never;
        /** Add an emergency contact for myself */
        post: operations["addMyEmergencyContact"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/disciplinary-actions/{id}/decide": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Decide an issued disciplinary action
         * @description UPHELD rows count toward warning totals; WITHDRAWN rows do not. Requires `employee.write`.
         */
        post: operations["decide"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/disabilities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List disabilities
         * @description Active only by default. Pass `includeInactive=true` for the HR-admin catalog UI. Requires `employee.read`.
         */
        get: operations["list_13"];
        put?: never;
        /**
         * Create a disability category
         * @description Requires `employee.admin`.
         */
        post: operations["create_6"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/designations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List designations
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_14"];
        put?: never;
        /**
         * Create a designation
         * @description Requires `employee.write`.
         */
        post: operations["create_7"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/departments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List departments
         * @description Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_15"];
        put?: never;
        /**
         * Create a department
         * @description Requires `employee.write`.
         */
        post: operations["create_8"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/cities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List cities
         * @description Filter by province and / or active flag. Open to any authenticated user — labels feed the ESS profile screen.
         */
        get: operations["list_16"];
        put?: never;
        /**
         * Create a city
         * @description Requires `employee.write`.
         */
        post: operations["create_9"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/skills/{id}": {
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
         * Patch a skill
         * @description Requires `employee.write`.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/sections/{id}": {
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
         * Patch a section
         * @description Pass `headId=0` to clear the head pointer. Requires `employee.write`.
         */
        patch: operations["update_1"];
        trace?: never;
    };
    "/qualifications/{id}": {
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
         * Patch a qualification
         * @description Requires `employee.write`.
         */
        patch: operations["update_2"];
        trace?: never;
    };
    "/qualifications/reorder": {
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
         * Bulk-reorder qualifications
         * @description Applies the supplied `displayOrder` to each listed qualification in a single transaction. Returns the full unfiltered catalog. Requires `employee.write`.
         */
        patch: operations["reorder"];
        trace?: never;
    };
    "/provinces/{id}": {
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
         * Patch a province
         * @description Null fields are left unchanged. Use `active=false` to soft-deactivate. Requires `employee.write`.
         */
        patch: operations["update_3"];
        trace?: never;
    };
    "/grades/{id}": {
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
         * Patch a grade
         * @description Requires `employee.write`.
         */
        patch: operations["update_4"];
        trace?: never;
    };
    "/grades/reorder": {
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
         * Bulk-reorder grades
         * @description Applies the supplied `displayOrder` to each listed grade in a single transaction. Rows not present in the payload keep their current order. Returns the full catalog (post-reorder). Requires `employee.write`.
         */
        patch: operations["reorder_1"];
        trace?: never;
    };
    "/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get an employee by id
         * @description Requires `employee.read`.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Patch an employee
         * @description HR-managed fields. Null fields are left unchanged. Requires `employee.write`.
         */
        patch: operations["update_5"];
        trace?: never;
    };
    "/employees/{id}/skills/{employeeSkillId}": {
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
         * Detach a skill from an employee
         * @description Requires `employee.write`.
         */
        delete: operations["delete"];
        options?: never;
        head?: never;
        /**
         * Patch an employee skill row
         * @description Requires `employee.write`.
         */
        patch: operations["update_6"];
        trace?: never;
    };
    "/employees/{id}/qualifications/{empQualId}": {
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
         * Detach a qualification from an employee
         * @description Requires `employee.write`.
         */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        /**
         * Patch an employee qualification row
         * @description Requires `employee.write`.
         */
        patch: operations["update_7"];
        trace?: never;
    };
    "/employees/{id}/prior-experience/{expId}": {
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
         * Delete a prior-experience row
         * @description Requires `employee.write`.
         */
        delete: operations["delete_2"];
        options?: never;
        head?: never;
        /**
         * Patch a prior-experience row
         * @description Requires `employee.write`.
         */
        patch: operations["update_8"];
        trace?: never;
    };
    "/employees/{id}/emergency-contacts/{contactId}": {
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
         * Delete an emergency contact
         * @description Requires `employee.write`.
         */
        delete: operations["delete_3"];
        options?: never;
        head?: never;
        /**
         * Patch an emergency contact
         * @description Requires `employee.write`.
         */
        patch: operations["update_9"];
        trace?: never;
    };
    "/employees/{id}/disabilities/{linkId}": {
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
         * Remove a disability link
         * @description Requires `employee.write`.
         */
        delete: operations["delete_4"];
        options?: never;
        head?: never;
        /**
         * Patch description / severity / document
         * @description The disability id is immutable on this row — delete and re-add to change it. Requires `employee.write`.
         */
        patch: operations["update_10"];
        trace?: never;
    };
    "/employees/{id}/certifications/{certId}": {
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
         * Delete a certification
         * @description Requires `employee.write`.
         */
        delete: operations["delete_5"];
        options?: never;
        head?: never;
        /**
         * Patch a certification
         * @description Requires `employee.write`.
         */
        patch: operations["update_11"];
        trace?: never;
    };
    "/employees/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get my employee record
         * @description Resolves the employee linked to the caller's user id.
         */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Patch a small set of contact fields on my record
         * @description Phone, personal email, addresses, city. Name / CNIC / identity numbers stay HR-only.
         */
        patch: operations["updateMe"];
        trace?: never;
    };
    "/employees/me/emergency-contacts/{contactId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Delete one of my emergency contacts */
        delete: operations["deleteMyEmergencyContact"];
        options?: never;
        head?: never;
        /** Patch one of my emergency contacts */
        patch: operations["updateMyEmergencyContact"];
        trace?: never;
    };
    "/disabilities/{id}": {
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
         * Deactivate a disability
         * @description Soft-delete — flips `active=false`. Existing employee_disabilities rows are preserved. Re-enable via PATCH. Requires `employee.admin`.
         */
        delete: operations["deactivate"];
        options?: never;
        head?: never;
        /**
         * Patch a disability
         * @description Null fields stay unchanged. `code` is immutable. Requires `employee.admin`.
         */
        patch: operations["update_12"];
        trace?: never;
    };
    "/designations/{id}": {
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
         * Patch a designation
         * @description Requires `employee.write`.
         */
        patch: operations["update_13"];
        trace?: never;
    };
    "/departments/{id}": {
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
         * Patch a department
         * @description Pass `headId=0` to clear the head pointer. Requires `employee.write`.
         */
        patch: operations["update_14"];
        trace?: never;
    };
    "/cities/{id}": {
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
         * Patch a city
         * @description Requires `employee.write`.
         */
        patch: operations["update_15"];
        trace?: never;
    };
    "/cities/reorder": {
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
         * Bulk-reorder cities
         * @description Applies the supplied `displayOrder` to each listed city in a single transaction. Returns the full unfiltered catalog. Requires `employee.write`.
         */
        patch: operations["reorder_2"];
        trace?: never;
    };
    "/skills/{id}/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Reverse lookup: who holds this skill
         * @description All employees with the given catalog skill, with their proficiency. Requires `employee.read`.
         */
        get: operations["employeesWithSkill"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sagas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sagas (paginated)
         * @description Filtered listing for operator views and the admin dashboard's failed-sagas slice. Both filters are optional. Default sort is `updatedAt,desc` so the freshest failures surface first. Requires `employee.read`.
         */
        get: operations["list_17"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sagas/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a saga by id
         * @description Requires `employee.read`.
         */
        get: operations["get_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sagas/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Saga health rollup
         * @description Aggregate RUNNING + last-24h FAILED counts plus a per-name 24-hour failure-rate series. Drives the admin dashboard's saga summary card without forcing the BFF to scan the saga list. Requires `employee.read`.
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
    "/sagas/by-key": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Look up a saga by (name, correlationKey)
         * @description Returns the unique saga for the pair (the substrate enforces `(saga_name, correlation_key)` uniqueness). Useful for FE to find the saga linked to an entity, e.g. `/sagas/by-key?name=ONBOARDING&correlationKey=employee:1042`. Requires `employee.read`.
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
    "/reports/employees/{id}/service-letter": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Service letter — PDF
         * @description Letter of employment for a current employee. Requires `employee.reports.run`.
         */
        get: operations["serviceLetter"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/{id}/experience-letter": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Experience letter — PDF
         * @description Letter for a terminated employee. Requires `employee.reports.run`.
         */
        get: operations["experienceLetter"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/leavers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leavers — Excel
         * @description Completed exits whose last working day falls in the range. Requires `employee.reports.run`.
         */
        get: operations["leavers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/joiners": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Joiners — Excel
         * @description Employees whose joining_date falls in the date range. Requires `employee.reports.run`.
         */
        get: operations["joiners"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/headcount-snapshot": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Headcount snapshot — PDF
         * @description Active headcount as of a date, broken down by department and grade. Requires `employee.reports.run`.
         */
        get: operations["headcountSnapshot"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/document-expiries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Document expiries — Excel
         * @description CNIC and certification expiries within the next N days. Requires `employee.reports.run`.
         */
        get: operations["documentExpiries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/directory": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Active-employee directory (CSV)
         * @description One row per non-terminated employee with identity, contact, and current-assignment columns. Streams as UTF-8 CSV with a BOM so Excel reads non-ASCII names correctly. HR / ADMIN callers get every employee; a MANAGER sees only their direct reports.
         */
        get: operations["directoryCsv"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/contract-expiries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Contract expiries — Excel
         * @description Active contracts with end_date in the next N days. Requires `employee.reports.run`.
         */
        get: operations["contractExpiries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/employees/confirmations-due": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Confirmations due — Excel
         * @description Active probation contracts ending in the next N days. Requires `employee.reports.run`.
         */
        get: operations["confirmationsDue"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/qualifications/{id}/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Reverse lookup: who holds this qualification
         * @description Requires `employee.read`.
         */
        get: operations["employeesWithQualification"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/team": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List an employee's direct reports
         * @description Admin analogue of `/employees/me/team`. Paginated; default 25 rows. Optional `q` matches the same fields as `/employees` (fullName, employeeCode, workEmail, phone, cnic — case-insensitive substring + pg_trgm fuzzy on name/code). Each row carries the standard read enrichments (profilePictureUrl, directReportsCount). Requires `employee.read`.
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
    "/employees/{id}/suspensions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List suspension history (current + historical)
         * @description Requires `employee.read`.
         */
        get: operations["listSuspensions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-assignment history (paginated)
         * @description Most recent first. Each row is `effective_from`..`effective_to` (null = current). Requires `employee.read`.
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
    "/employees/{id}/history/resolved": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Paginated employment history with resolved labels
         * @description Sibling to `/employees/{id}/history` — same rows, with catalog labels and manager summaries denormalized server-side. Newest first. Requires `employee.read`.
         */
        get: operations["historyResolved"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/exit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current exit row for this employee
         * @description Requires `employee.read`.
         */
        get: operations["exit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/contracts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the employee's contract history
         * @description Most-recent first. The current ACTIVE contract sits at the top. Requires `employee.read`.
         */
        get: operations["listContracts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Headline KPIs for the People-overview screen
         * @description Counts by status / employmentType, probation windows (active / due-in-14d / overdue), certification windows (expiring-in-30d / expired), and ESS coverage. Requires `employee.read`.
         */
        get: operations["stats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/picker": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Employee picker (service-neutral)
         * @description ACTIVE employees, name + code fuzzy match with relevance ordering, capped at 100. The single picker every typeahead surface should use (payroll off-cycle / increment / bonus, attendance operator punch / shift override, notification recipient picker, …). Auth-only.
         */
        get: operations["picker"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/org-tree": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Recursive org chart
         * @description Manager → reports tree from the given root (or top-of-org when `root` is omitted). `depth` defaults to 3, hard-capped to 6. Requires `employee.read`.
         */
        get: operations["orgTree"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/onboarding-lookups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Bundle of every catalog the onboarding wizard needs on first paint
         * @description Departments, sections, designations, grades, provinces, cities, qualifications, skills. The FE fetches `/admin/roles` from auth-service separately when the caller has `auth.users.write`.
         */
        get: operations["onboardingLookups"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/team": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my direct reports
         * @description Paginated; default 25 rows. Optional `q` matches the same fields as `/employees` (fullName, employeeCode, workEmail, phone, cnic — case-insensitive substring + pg_trgm fuzzy on name/code). Each row carries the standard read enrichments (profilePictureUrl, directReportsCount).
         */
        get: operations["myTeam"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/suspensions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My suspension history (read-only)
         * @description Includes lifted rows for full transparency.
         */
        get: operations["mySuspensions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/skills": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List my skills (read-only) */
        get: operations["mySkills"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/qualifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List my qualifications (read-only) */
        get: operations["myQualifications"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/prior-experience": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List my prior employment (read-only) */
        get: operations["myPriorExperience"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/manager": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get my manager
         * @description Returns the caller's manager. 404 if the caller has no manager assigned. Open to any authenticated employee — the ESS profile screen needs it to render the manager card without granting `employee.read`.
         */
        get: operations["myManager"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My org-assignment history (paginated, read-only)
         * @description Most-recent first. Each row is `effective_from`..`effective_to` (null = current).
         */
        get: operations["myHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/exit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My current or historical exit row (read-only)
         * @description Returns 404 when the employee has never had an exit row.
         */
        get: operations["myExit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/documents": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my own documents (read-only)
         * @description Paginated. Each row carries a short-lived signed download URL.
         */
        get: operations["myDocuments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/disciplinary-actions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My disciplinary-action history (read-only)
         * @description Includes WITHDRAWN rows for full transparency. WARNING counts in the response are UPHELD-only and bounded to the requested fiscal year.
         */
        get: operations["myDisciplinaryActions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/contracts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My contract history (read-only)
         * @description Most-recent first. The current ACTIVE contract sits at the top.
         */
        get: operations["myContracts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/me/certifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List my certifications (read-only) */
        get: operations["myCertifications"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/headcount-trend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Joiners / leavers / end-of-month headcount over a trailing window
         * @description Three SQL reads (joiner counts by month, leaver counts by month, one anchor headcount-as-of) walked forward to produce the per-month series. Window is 1–24 months; defaults to 12. Requires `employee.read`.
         */
        get: operations["headcountTrend"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/directory": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Directory list with denormalized labels
         * @description Same filters as `/employees`. Each row carries catalog labels (department / section / designation / grade / city) and a manager summary so the FE doesn't have to fan out. Requires `employee.read`.
         */
        get: operations["directory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/delegation-candidates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Delegate-picker for the approval-delegation drawer
         * @description ACTIVE employees with ESS, excluding the caller. Optional `q` substring-matches name/employee code. `limit` defaults to 20 (max 100).
         */
        get: operations["delegationCandidates"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/by-user-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resolve userIds to employee identity
         * @description Returns one row per linked employee, keyed by user_id. Users without a linked employee row (admin-only accounts) are silently dropped — caller falls back to the auth resolver's fullName. Cap: 200 ids per call.
         */
        get: operations["byUserIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/by-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Batch lookup by ids
         * @description Returns the rows for {@code ids} in the requested order; missing ids are silently dropped. Caps the batch at 200 to keep the response bounded — callers needing more should page through `/employees`. Authenticated; no specific permission required so the commons-cache miss path works for every service.
         */
        get: operations["byIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/aggregate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Headcount grouped by a single dimension
         * @description Returns one bucket per group key with optional human-readable label. All non-status groupings exclude TERMINATED rows; the `status` grouping includes them. Requires `employee.read`.
         */
        get: operations["aggregate"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/disciplinary-actions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Global disciplinary list for HR review (paginated)
         * @description Filters can be combined freely. `employeeId` scopes to a single employee (use `/employees/{id}/disciplinary-actions` instead if you also want UPHELD warning counts). `q` matches the owning employee's full name or employee code as a case-insensitive substring. Requires `employee.read`.
         */
        get: operations["globalList"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/certifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Global expiring-cert queue
         * @description Certs with `expiry_date <= today + days`, oldest first. By default already-expired rows are included so HR sees what's overdue; pass `expired=false` to scope to future-dated only, or `expired=true` to scope to already-expired only. `expiringWithin` is required unless `expired=true` (in that mode the upper bound is fixed at today and the days window is meaningless). `departmentId` / `sectionId` narrow the queue to an org slice via the cert's owning employee. Requires `employee.read`.
         */
        get: operations["expiringWithin"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/employees/tenure-bands": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Tenure-band snapshot
         * @description Active employees bucketed into <1y / 1–3y / 3–5y / 5–10y / 10y+. Optional {@code splitBy=dept|grade} returns a per-bucket breakdown.
         */
        get: operations["tenureBands"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/employees/headcount-trend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Headcount + joiners/leavers trend
         * @description One row per month in the trailing window with joiners, leavers, and end-of-month headcount. Window 1..24 months; defaults to 12.
         */
        get: operations["headcountTrend_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/employees/attrition": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly attrition + rolling-12m rate
         * @description Per-month leavers, start/end headcount, and rate% (= leavers / avg-hc). Plus a rolling-12m rate aggregating the last 12 months.
         */
        get: operations["attrition"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/employees/{id}/documents/{docId}": {
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
         * Delete a document (storage object + metadata row)
         * @description Requires `employee.write`.
         */
        delete: operations["delete_6"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        CreateSkillRequest: {
            /**
             * @description Catalog code.
             * @example TECH_JAVA
             */
            code: string;
            /**
             * @description Display name.
             * @example Java
             */
            name: string;
            /**
             * @description TECH | LANGUAGE | TRADE | SPORT | OTHER.
             * @example TECH
             */
            category?: string;
        };
        /** @description Skill catalog. Per-employee proficiency lives in employee_skills. */
        SkillDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 11
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example TECH_JAVA
             */
            code?: string;
            /**
             * @description Display name.
             * @example Java
             */
            name?: string;
            /**
             * @description Loose category — TECH | LANGUAGE | TRADE | SPORT | OTHER.
             * @example TECH
             */
            category?: string | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        CreateSectionRequest: {
            /**
             * @description Catalog code.
             * @example QC_FB
             */
            code: string;
            /**
             * @description Display name.
             * @example Quality Control - Footballs
             */
            name: string;
            /**
             * Format: int64
             * @description Parent department id.
             * @example 3
             */
            departmentId: number;
            /**
             * Format: int64
             * @description Optional initial head (employee id).
             * @example 42
             */
            headId?: number;
        };
        /** @description Section within a department. */
        SectionDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 12
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example QC_1
             */
            code?: string;
            /**
             * @description Display name.
             * @example Quality Control - Footballs
             */
            name?: string;
            /**
             * Format: int64
             * @description Parent department id.
             * @example 3
             */
            departmentId?: number;
            /**
             * @description Parent department code.
             * @example PROD
             */
            departmentCode?: string;
            /**
             * Format: int64
             * @description Section head's employee id, if assigned.
             */
            headId?: number | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
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
        CreateQualificationRequest: {
            /**
             * @description Catalog code.
             * @example BSC_CS
             */
            code: string;
            /**
             * @description Display name.
             * @example BSc Computer Science
             */
            name: string;
            /**
             * @description MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS | MASTERS | MPHIL | PHD | OTHER.
             * @example BACHELORS
             */
            level: string;
        };
        /** @description Academic qualification catalog. Per-employee instances live in employee_qualifications. */
        QualificationDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 8
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example BSC_CS
             */
            code?: string;
            /**
             * @description Display name.
             * @example BSc Computer Science
             */
            name?: string;
            /**
             * @description MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS | MASTERS | MPHIL | PHD | OTHER.
             * @example BACHELORS
             */
            level?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 40
             */
            displayOrder?: number;
        };
        CreateProvinceRequest: {
            /**
             * @description ISO 3166-2:PK style code (uppercase).
             * @example PB
             */
            code: string;
            /**
             * @description Display name.
             * @example Punjab
             */
            name: string;
        };
        /** @description Province / federal area in Pakistan. */
        ProvinceDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 1
             */
            id?: number;
            /**
             * @description ISO 3166-2:PK style code.
             * @example PB
             */
            code?: string;
            /**
             * @description Display name.
             * @example Punjab
             */
            name?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        CreateGradeRequest: {
            /**
             * @description Catalog code.
             * @example G3
             */
            code: string;
            /**
             * @description Display name.
             * @example Grade 3
             */
            name: string;
            /**
             * Format: int32
             * @description Higher = more senior. Used to sort grades.
             * @example 3
             */
            level: number;
            /** @description Optional description. */
            description?: string;
            /**
             * @description Role bucket. Defaults to LABOUR when omitted; flip to MANAGEMENT or SUPERVISOR for grades that need the management-style canteen / policy rules.
             * @enum {string|null}
             */
            gradeType?: "MANAGEMENT" | "SUPERVISOR" | "LABOUR" | null;
        };
        /** @description Seniority grade catalog. Higher level = more senior. */
        GradeDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 4
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example G3
             */
            code?: string;
            /**
             * @description Display name.
             * @example Grade 3
             */
            name?: string;
            /**
             * Format: int32
             * @description Ordering hint; higher is more senior.
             * @example 3
             */
            level?: number;
            /** @description Optional description. */
            description?: string | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 30
             */
            displayOrder?: number;
            /**
             * @description Role bucket. Drives payroll's canteen rule (management → weekly, supervisor/labour → per-day) and other grade-type policy switches.
             * @enum {string}
             */
            gradeType?: "MANAGEMENT" | "SUPERVISOR" | "LABOUR";
        };
        /** @description Pakistan IBAN + bank catalog reference for salary disbursement. */
        OnboardBankDetails: {
            /**
             * Format: int64
             * @description FK into banks catalog.
             */
            bankId: number;
            branch?: string | null;
            accountTitle: string;
            /** @description Pakistan IBAN, 24 chars (PK + 2 digits + 4 letters + 16 alphanumerics). */
            iban: string;
            accountNumber?: string | null;
        };
        OnboardCertificationLine: {
            name: string;
            issuingBody?: string | null;
            /** Format: date */
            issueDate?: string | null;
            /**
             * Format: date
             * @description Null = no expiry.
             */
            expiryDate?: string | null;
            credentialId?: string | null;
            documentKey?: string | null;
        };
        /** @description One row in employee_salary_components. */
        OnboardComponentLine: {
            /**
             * Format: int64
             * @description FK into salary_components catalog.
             */
            componentId: number;
            /**
             * Format: int64
             * @description Override the catalog default amount (paisa). Null = use default.
             * @example 1500000
             */
            amount?: number | null;
            notes?: string | null;
        };
        OnboardDisabilityLine: {
            /**
             * Format: int64
             * @description FK into disabilities catalog.
             */
            disabilityId: number;
            description?: string | null;
            /** @enum {string|null} */
            severity?: "MILD" | "MODERATE" | "SEVERE" | null;
            documentKey?: string | null;
        };
        OnboardEmergencyContactLine: {
            name: string;
            relation?: string | null;
            phone: string;
            /** @description Mark as the primary contact. The first row with primary=true wins; the rest are stored as non-primary regardless of flag value. */
            primary?: boolean;
        };
        /** @description HR payload to onboard a new employee. Quick-add modals send only the required identity + assignment fields; the complete-form page additionally supplies salary, shift, and per-row arrays (qualifications, experience, skills, certifications, emergency contacts, disabilities). Sub-resources owned by employee-service are persisted in the kickoff transaction; salary, salary-components, bank, and shift sit in the saga payload and are applied by ONBOARDING saga steps with bounded retry. ESS provisioning runs as the first saga step when `provisionEss=true`. */
        OnboardEmployeeRequest: {
            /**
             * @description Display name.
             * @example Ahmed Raza
             */
            fullName: string;
            /**
             * @description 13-digit CNIC, dashes optional.
             * @example 35202-1234567-1
             */
            cnic?: string;
            /** Format: date */
            cnicIssueDate?: string | null;
            /** Format: date */
            cnicExpiryDate?: string | null;
            /** @description Pakistan NTN (7-9 digits). */
            ntn?: string | null;
            insuranceNumber?: string | null;
            sscNumber?: string | null;
            eobiNumber?: string | null;
            /** Format: date */
            dateOfBirth?: string | null;
            gender?: string | null;
            maritalStatus?: string | null;
            fatherName?: string | null;
            motherName?: string | null;
            spouseName?: string | null;
            religion?: string | null;
            /** @example B+ */
            bloodGroup?: string | null;
            /** Format: email */
            personalEmail?: string | null;
            /** Format: email */
            workEmail?: string | null;
            phone?: string | null;
            permanentAddress?: string | null;
            currentAddress?: string | null;
            /** Format: int64 */
            cityId?: number | null;
            /**
             * Format: date
             * @description First day on the job.
             */
            joiningDate: string;
            /** @enum {string|null} */
            employmentType?: "PERMANENT" | "CONTRACT" | "INTERN" | null;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int64 */
            designationId?: number | null;
            /** Format: int64 */
            gradeId?: number | null;
            /**
             * Format: int64
             * @description Initial direct manager.
             */
            managerId?: number | null;
            /**
             * Format: int32
             * @description Probation length in days. Allowed: 30, 60, 90 (default 90), 120, 180.
             * @example 90
             */
            probationDays?: number;
            /** @description If true, employee-service calls auth-service to create an ESS user. */
            provisionEss?: boolean;
            /** @description Login name for the ESS user. Required when `provisionEss=true`. */
            username?: string;
            /** @description Email for the ESS user. Optional; uses workEmail when omitted. */
            email?: string;
            /** @description Temporary password for the ESS user. Required when `provisionEss=true`. */
            temporaryPassword?: string;
            /** @description Per-employee qualifications. Persisted in the kickoff tx. */
            qualifications?: components["schemas"]["OnboardQualificationLine"][] | null;
            /** @description Prior employment history. Persisted in the kickoff tx. */
            experiences?: components["schemas"]["OnboardExperienceLine"][] | null;
            /** @description Skills + proficiency. Persisted in the kickoff tx. */
            skills?: components["schemas"]["OnboardSkillLine"][] | null;
            /** @description Professional certifications. Persisted in the kickoff tx. */
            certifications?: components["schemas"]["OnboardCertificationLine"][] | null;
            /** @description Emergency contacts. Persisted in the kickoff tx. Recommend at least one for compliance. */
            emergencyContacts?: components["schemas"]["OnboardEmergencyContactLine"][] | null;
            /** @description Registered disabilities. Persisted in the kickoff tx. */
            disabilities?: components["schemas"]["OnboardDisabilityLine"][] | null;
            /** @description Salary, allowance/deduction lines, and bank. Applied by SET_BASIC_SALARY / ASSIGN_SALARY_COMPONENTS / SET_BANK_DETAILS saga steps. Omit to skip — payroll-service does not auto-seed; HR sets basic later via POST /salary/employees/{id}. Until then the employee surfaces as a NO_BASIC_SALARY skip row on each compute attempt. */
            salary?: components["schemas"]["OnboardSalarySetup"];
            /** @description Initial shift assignment. Applied by the ASSIGN_SHIFT saga step. Omit to skip — HR can assign later via /shifts/employees/{id}. */
            shift?: components["schemas"]["OnboardShiftAssignment"];
        };
        OnboardExperienceLine: {
            employerName: string;
            designation?: string | null;
            /** Format: date */
            startDate: string;
            /** Format: date */
            endDate: string;
            responsibilities?: string | null;
            reasonForLeaving?: string | null;
        };
        OnboardQualificationLine: {
            /** Format: int64 */
            qualificationId: number;
            institution: string;
            boardOrUniversity?: string | null;
            /** Format: int32 */
            yearOfCompletion?: number | null;
            grade?: string | null;
            documentKey?: string | null;
        };
        /** @description Basic salary + selected allowance/deduction lines + bank for disbursement. All amounts are paisa (1 PKR = 100 paisa). */
        OnboardSalarySetup: {
            /**
             * Format: int64
             * @description Monthly basic in paisa. Replaces the auto-seeded placeholder.
             * @example 5000000
             */
            basic: number;
            /** @description Salary components from the catalog (allowances + deductions). Income tax should be entered here as a deduction line with code INCOME_TAX. */
            components?: components["schemas"]["OnboardComponentLine"][] | null;
            /** @description Bank details for salary disbursement. Optional — can be added later. */
            bank?: components["schemas"]["OnboardBankDetails"];
        };
        /** @description Initial shift the employee follows from joiningDate. */
        OnboardShiftAssignment: {
            /**
             * Format: int64
             * @description FK into shifts catalog (attendance-service).
             */
            shiftId: number;
        };
        OnboardSkillLine: {
            /** Format: int64 */
            skillId: number;
            /** @enum {string} */
            proficiency: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
            years?: number | null;
            notes?: string | null;
        };
        /** @description Per-row active-probation summary for HR's probation queue. */
        ActiveProbationDto: {
            /**
             * Format: int64
             * @description Underlying employment_contracts row id.
             * @example 734
             */
            contractId?: number;
            /**
             * Format: date
             * @description Probation end date.
             * @example 2026-05-15
             */
            endDate?: string;
            /**
             * Format: int32
             * @description Days remaining (negative when overdue).
             * @example 12
             */
            daysRemaining?: number;
            /** @description True iff the probation has already been extended once. */
            isExtended?: boolean;
        };
        /** @description Employee record — admin lists, get-by-id, post-mutation responses. */
        EmployeeDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 42
             */
            id?: number;
            /**
             * Format: int64
             * @description Linked auth user id (null when ESS not granted).
             */
            userId?: number | null;
            /**
             * @description Employee code.
             * @example FS-2026-0042
             */
            employeeCode?: string;
            /**
             * @description Display name.
             * @example Ahmed Raza
             */
            fullName?: string;
            /** @description 13-digit CNIC. */
            cnic?: string | null;
            /** Format: date */
            cnicIssueDate?: string | null;
            /** Format: date */
            cnicExpiryDate?: string | null;
            ntn?: string | null;
            insuranceNumber?: string | null;
            sscNumber?: string | null;
            eobiNumber?: string | null;
            /** Format: date */
            dateOfBirth?: string | null;
            gender?: string | null;
            maritalStatus?: string | null;
            fatherName?: string | null;
            motherName?: string | null;
            spouseName?: string | null;
            religion?: string | null;
            /** @example B+ */
            bloodGroup?: string | null;
            personalEmail?: string | null;
            workEmail?: string | null;
            phone?: string | null;
            permanentAddress?: string | null;
            currentAddress?: string | null;
            /** Format: int64 */
            cityId?: number | null;
            /** @example PK */
            country?: string;
            /** Format: date */
            joiningDate?: string;
            /** Format: date */
            confirmationDate?: string | null;
            /** @enum {string} */
            employmentStatus?: "ACTIVE" | "ON_NOTICE" | "ON_LEAVE" | "SUSPENDED" | "TERMINATED";
            /** @enum {string|null} */
            employmentType?: "PERMANENT" | "CONTRACT" | "INTERN" | null;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int64 */
            designationId?: number | null;
            /** Format: int64 */
            gradeId?: number | null;
            /** Format: int64 */
            managerId?: number | null;
            /**
             * Format: int64
             * @description Linked auth user id of the manager. Null when the manager has no ESS user, or when the source path didn't resolve it (mutation responses leave it null).
             */
            managerUserId?: number | null;
            profilePictureKey?: string | null;
            /** @description Short-lived signed GET URL for the profile picture. Null when no picture is set or the storage call failed. */
            profilePictureUrl?: string | null;
            /** @description True iff ESS is provisioned. */
            hasEss?: boolean;
            /**
             * @description ESS provisioning state. On mutation responses reflects only the linked-user check (COMPLETED / NOT_REQUESTED). On read paths the assembler looks up the onboarding saga row to surface RUNNING / FAILED.
             * @enum {string}
             */
            essProvisioningStatus?: "NOT_REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";
            /**
             * Format: int32
             * @description Number of non-terminated employees who report to this employee. Populated on read endpoints; null on post-mutation responses.
             */
            directReportsCount?: number | null;
            /** @description Active-probation summary. Populated only on probation queries (probationDueWithin / probationOverdue) and detail reads. */
            activeProbation?: components["schemas"]["ActiveProbationDto"];
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        /** @description Response from POST /employees. Wraps the freshly-created employee plus, when any cross-service work was requested (ESS provisioning, basic salary, salary components, bank details, shift assignment), the id of the onboarding saga the FE can poll via GET /sagas/{id} for live per-step status. */
        OnboardEmployeeResponse: {
            /** @description The created employee. Cross-service work runs out-of-band — the saga's outcome is reflected on subsequent reads (e.g. essProvisioningStatus, payroll, attendance lookups). */
            employee?: components["schemas"]["EmployeeDto"];
            /**
             * Format: int64
             * @description Saga row id, present when at least one cross-service section was requested (provisionEss=true, salary, or shift). Use it on GET /sagas/{id} to surface RUNNING / COMPLETED / FAILED per step in the UI.
             */
            sagaId?: number | null;
        };
        WithdrawExitRequest: {
            /** @description Why the resignation is being withdrawn. */
            reason: string;
        };
        /** @description Current or historical exit row for an employee. */
        ExitDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            exitType?: "RESIGNATION" | "TERMINATION" | "RETIREMENT";
            /** Format: date */
            noticeStartDate?: string | null;
            /** Format: date */
            lastWorkingDay?: string;
            reason?: string | null;
            notes?: string | null;
            /** @enum {string} */
            status?: "PENDING" | "COMPLETED" | "WITHDRAWN";
            /** Format: date-time */
            completedAt?: string | null;
            /** Format: date-time */
            withdrawnAt?: string | null;
            withdrawnReason?: string | null;
        };
        /** @description Immediate termination. Last working day = today; emits employee.terminated immediately. */
        TerminateRequest: {
            /** @description Short categorisation. */
            reason: string;
            /** @description Free-text notes for HR. */
            notes?: string;
            /**
             * Format: int64
             * @description If the employee has direct reports, the id of the employee they should be reassigned to. Required when reports exist.
             */
            reassignReportsTo?: number | null;
        };
        SuspendRequest: {
            /**
             * Format: date
             * @description Day the suspension begins.
             */
            startDate: string;
            /**
             * Format: date
             * @description Planned end. Omit for open-ended.
             */
            endDate?: string | null;
            /** @description Why the employee is being suspended. */
            reason: string;
            /** @description When false, payroll counts these days as LOP. */
            withPay: boolean;
        };
        /** @description One row in an employee's suspension history. */
        SuspensionDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            startDate?: string;
            /**
             * Format: date
             * @description Planned end. Null on open-ended suspensions.
             */
            endDate?: string | null;
            reason?: string;
            /** @description When false, suspended days count as LOP for payroll. */
            withPay?: boolean;
            /** @enum {string} */
            status?: "ACTIVE" | "LIFTED";
            /** Format: date */
            liftedOn?: string | null;
            liftedReason?: string | null;
        };
        AddEmployeeSkillRequest: {
            /** Format: int64 */
            skillId: number;
            /** @enum {string} */
            proficiency: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
            years?: number | null;
            notes?: string | null;
        };
        /** @description Per-employee proficiency on a catalog skill. */
        EmployeeSkillDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            skillId?: number;
            /**
             * @description Catalog code, denormalized.
             * @example TECH_JAVA
             */
            skillCode?: string;
            /**
             * @description Catalog name, denormalized.
             * @example Java
             */
            skillName?: string;
            /** @enum {string} */
            proficiency?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
            years?: number | null;
            notes?: string | null;
        };
        /** @description Body shared by /employees/{id}/manager, /department, /section, /designation, /grade. Pass `targetId=0` to clear the assignment (where allowed). */
        AssignmentChangeRequest: {
            /**
             * Format: int64
             * @description Catalog row id to assign.
             * @example 3
             */
            targetId?: number;
            /** @description Optional notes recorded on employee_history. */
            notes?: string;
        };
        ResignRequest: {
            /**
             * Format: date
             * @description Last working day; must be in the future.
             */
            lastWorkingDay: string;
            /** @description Short categorisation. */
            reason?: string;
            /** @description Free-text notes for HR. */
            notes?: string;
            /**
             * Format: int64
             * @description If the employee has direct reports, the id of the employee they should be reassigned to. Required when reports exist.
             */
            reassignReportsTo?: number | null;
        };
        AddEmployeeQualificationRequest: {
            /** Format: int64 */
            qualificationId: number;
            institution: string;
            boardOrUniversity?: string | null;
            /** Format: int32 */
            yearOfCompletion?: number | null;
            grade?: string | null;
            /** @description MinIO key for an uploaded scan. */
            documentKey?: string | null;
        };
        /** @description An employee's instance of a catalog qualification (degree). */
        EmployeeQualificationDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            qualificationId?: number;
            /**
             * @description Catalog code, denormalized.
             * @example BSC_CS
             */
            qualificationCode?: string;
            /**
             * @description Catalog name, denormalized.
             * @example BSc Computer Science
             */
            qualificationName?: string;
            institution?: string;
            boardOrUniversity?: string | null;
            /** Format: int32 */
            yearOfCompletion?: number | null;
            grade?: string | null;
            /** @description MinIO key for an uploaded transcript / degree. */
            documentKey?: string | null;
            /** @description Short-lived signed GET URL for the transcript. Null when no document is attached or the storage call failed. */
            documentUrl?: string | null;
        };
        /** @description Posted after the client finishes uploading to the pre-signed profile-picture URL. */
        SetProfilePictureRequest: {
            /** @description Object key returned from /profile-picture/upload-url. */
            objectKey: string;
        };
        ProfilePictureFilenameRequest: {
            /**
             * @description Original filename (used to compute the object key suffix).
             * @example avatar.jpg
             */
            filename: string;
            /**
             * Format: int64
             * @description Declared object size in bytes. Rejected at pre-sign if above `hrms.employee.documents.avatar-max-size-bytes` (default 2 MB).
             */
            bytes?: number;
        };
        /** @description Pre-signed PUT URL the client uploads the file directly to. After the upload completes, post the metadata to /documents (or /profile-picture) referencing this objectKey. */
        UploadUrlResponse: {
            /**
             * @description Bucket the URL targets.
             * @example employee-docs
             */
            bucket?: string;
            /** @description Object key the URL is signed for. Pass this back when recording metadata. */
            objectKey?: string;
            /** @description Pre-signed PUT URL. */
            uploadUrl?: string;
            /**
             * Format: date-time
             * @description When the URL stops working (UTC).
             */
            expiresAt?: string;
        };
        AddPriorExperienceRequest: {
            employerName: string;
            designation?: string;
            /** Format: date */
            startDate: string;
            /**
             * Format: date
             * @description Required — every prior employer has been left.
             */
            endDate: string;
            responsibilities?: string;
            reasonForLeaving?: string;
        };
        /** @description Pre-Forward-Sports employment history. */
        PriorExperienceDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            employerName?: string;
            designation?: string | null;
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string;
            responsibilities?: string | null;
            reasonForLeaving?: string | null;
        };
        LiftSuspensionRequest: {
            /**
             * Format: date
             * @description Actual lift date. Defaults to today when omitted.
             */
            liftedOn?: string | null;
            /** @description Why the suspension is being lifted. */
            reason: string;
        };
        /** @description Grant ESS to an existing employee. */
        GrantEssRequest: {
            /** @description Login name. Falls back to a slug of the employee's full name when omitted. */
            username?: string;
            /**
             * Format: email
             * @description Email for the ESS user. Falls back to workEmail.
             */
            email?: string;
            /** @description Temporary password — user is forced to change at first login. */
            temporaryPassword: string;
        };
        /** @description Response from POST /employees/{id}/grant-ess. Wraps the employee row and the id of the saga that drives the auth-service call out-of-band. The FE polls GET /sagas/{id} for live status (RUNNING / COMPLETED / FAILED). */
        GrantEssResponse: {
            /** @description The employee row at kickoff time. `userId` is patched onto the row by the saga's PROVISION_ESS step; subsequent reads will reflect it. */
            employee?: components["schemas"]["EmployeeDto"];
            /**
             * Format: int64
             * @description Saga row id. Always present; the auth call always runs through the saga.
             */
            sagaId: number;
        };
        /** @description One-time extension of an active PROBATION contract. newProbationDays must be 120 or 180, and greater than the current probation_days. */
        ExtendProbationRequest: {
            /**
             * Format: int32
             * @description New total probation length in days. Allowed: 120 or 180.
             * @example 120
             */
            newProbationDays: number;
            /** @description Reason for the extension. */
            reason: string;
        };
        CreateEmergencyContactRequest: {
            name: string;
            /** @description Relationship to employee. */
            relation?: string;
            phone: string;
            /** @description Mark as the primary contact. Demotes any other primary on this employee. */
            primary?: boolean;
        };
        EmergencyContactDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            name?: string;
            relation?: string | null;
            phone?: string;
            primary?: boolean;
        };
        /** @description Posted after the client finishes uploading to the pre-signed URL. Service verifies the object exists in MinIO before persisting. */
        RecordDocumentRequest: {
            /** @enum {string} */
            docType: "CNIC" | "DEGREE" | "OFFER_LETTER" | "CONTRACT" | "EXPERIENCE_LETTER" | "OTHER";
            title: string;
            /** @description Object key returned from /upload-url. */
            objectKey: string;
            contentType?: string;
        };
        /** @description Stored document metadata + a short-lived signed download URL. */
        EmployeeDocumentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            docType?: "CNIC" | "DEGREE" | "OFFER_LETTER" | "CONTRACT" | "EXPERIENCE_LETTER" | "OTHER";
            title?: string;
            objectKey?: string;
            contentType?: string | null;
            /** Format: int64 */
            sizeBytes?: number | null;
            /** @description Short-lived signed download URL. */
            downloadUrl?: string | null;
        };
        /** @description Ask for a pre-signed PUT URL. The service crafts the object key from employeeId / docType / a UUID; the client uploads to the URL and then POSTs the metadata back. */
        UploadUrlRequest: {
            /** @enum {string} */
            docType: "CNIC" | "DEGREE" | "OFFER_LETTER" | "CONTRACT" | "EXPERIENCE_LETTER" | "OTHER";
            /** @description Original filename — used to compute the suffix on the object key. */
            filename: string;
            /** @description Optional content-type hint stored alongside the metadata. */
            contentType?: string;
            /**
             * Format: int64
             * @description Declared object size in bytes. The server rejects pre-sign requests above `hrms.employee.documents.max-size-bytes` (default 10 MB) and re-checks the actual size at record-time.
             */
            bytes?: number;
        };
        IssueDisciplinaryRequest: {
            /** @enum {string} */
            level: "VERBAL_WARNING" | "FIRST_WARNING" | "SECOND_WARNING" | "FINAL_WARNING";
            /** @enum {string} */
            category: "ATTENDANCE" | "MISCONDUCT" | "PERFORMANCE" | "INSUBORDINATION" | "POLICY_VIOLATION" | "OTHER";
            /** Format: date */
            incidentDate?: string | null;
            /** Format: date */
            issuedOn: string;
            /** @description Short narrative — what the employee did. */
            reason: string;
        };
        /** @description One disciplinary-action row. */
        DisciplinaryActionDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            level?: "VERBAL_WARNING" | "FIRST_WARNING" | "SECOND_WARNING" | "FINAL_WARNING";
            /** @enum {string} */
            category?: "ATTENDANCE" | "MISCONDUCT" | "PERFORMANCE" | "INSUBORDINATION" | "POLICY_VIOLATION" | "OTHER";
            /** Format: date */
            incidentDate?: string | null;
            /** Format: date */
            issuedOn?: string;
            reason?: string;
            /** @enum {string|null} */
            decision?: "UPHELD" | "WITHDRAWN" | null;
            decisionNotes?: string | null;
            /** Format: date */
            closedOn?: string | null;
            /** @enum {string} */
            status?: "ISSUED" | "CLOSED";
        };
        /** @description Link an employee to a disability category. (employeeId, disabilityId) is unique. */
        AddEmployeeDisabilityRequest: {
            /**
             * Format: int64
             * @description FK into disabilities.id.
             */
            disabilityId: number;
            /**
             * @description Free-text description for compliance reporting. Use this for the specific impairment phrase (legacy WebHRMS stored 78 distinct values).
             * @example Right arm polio
             */
            description?: string | null;
            /** @enum {string|null} */
            severity?: "MILD" | "MODERATE" | "SEVERE" | null;
            /** @description Object key in the employee-docs bucket for the supporting medical certificate. */
            documentKey?: string | null;
        };
        EmployeeDisabilityDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            disabilityId?: number;
            /** @description Catalog code (denormalized for the read.) */
            disabilityCode?: string;
            /** @description Catalog name. */
            disabilityName?: string;
            description?: string | null;
            /** @enum {string|null} */
            severity?: "MILD" | "MODERATE" | "SEVERE" | null;
            documentKey?: string | null;
        };
        /** @description Confirm an employee out of probation. */
        ConfirmEmployeeRequest: {
            /** @description Optional confirmation notes recorded on the new permanent contract. */
            notes?: string;
        };
        AddCertificationRequest: {
            name: string;
            issuingBody?: string;
            /** Format: date */
            issueDate?: string;
            /**
             * Format: date
             * @description Null = no expiry.
             */
            expiryDate?: string;
            credentialId?: string;
            documentKey?: string;
        };
        /** @description Free-form per-employee certification (no global catalog). */
        EmployeeCertificationDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @description Denormalized employee code; only filled on the global expiring queue. */
            employeeCode?: string | null;
            /** @description Denormalized employee name; only filled on the global expiring queue. */
            fullName?: string | null;
            name?: string;
            issuingBody?: string | null;
            /** Format: date */
            issueDate?: string | null;
            /**
             * Format: date
             * @description Null = no expiry.
             */
            expiryDate?: string | null;
            credentialId?: string | null;
            documentKey?: string | null;
            /** @description Short-lived signed GET URL for the certificate scan. Null when no document is attached or the storage call failed. */
            documentUrl?: string | null;
        };
        /** @description Close out an ISSUED disciplinary action. UPHELD rows count toward warning totals; WITHDRAWN rows do not. */
        DecideDisciplinaryRequest: {
            /** @enum {string} */
            decision: "UPHELD" | "WITHDRAWN";
            decisionNotes?: string | null;
            /**
             * Format: date
             * @description Defaults to today when omitted.
             */
            closedOn?: string | null;
        };
        /** @description HR-admin create payload for the disability catalog. `code` is unique and immutable post-create — referenced by employee_disabilities. */
        CreateDisabilityRequest: {
            /** @example PHYSICAL */
            code: string;
            /** @example Physical / orthopedic impairment */
            name: string;
        };
        /** @description Disability category catalog. Used for compliance reporting under the Persons with Disabilities (Employment & Rehabilitation) ordinance. */
        DisabilityDto: {
            /** Format: int64 */
            id?: number;
            code?: string;
            name?: string;
            active?: boolean;
        };
        CreateDesignationRequest: {
            /**
             * @description Catalog code.
             * @example SR_ENG
             */
            code: string;
            /**
             * @description Display title.
             * @example Senior Engineer
             */
            title: string;
            /**
             * @description DIRECT (production-line / labour) vs INDIRECT (office / admin). Optional — null when the distinction does not apply.
             * @enum {string|null}
             */
            category?: "DIRECT" | "INDIRECT" | null;
        };
        /** @description Job title catalog. Department-agnostic. */
        DesignationDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 7
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example SR_ENG
             */
            code?: string;
            /**
             * @description Display title.
             * @example Senior Engineer
             */
            title?: string;
            /**
             * @description DIRECT (production-line) vs INDIRECT (office / admin). Null when the distinction does not apply.
             * @enum {string|null}
             */
            category?: "DIRECT" | "INDIRECT" | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        CreateDepartmentRequest: {
            /**
             * @description Catalog code.
             * @example PROD
             */
            code: string;
            /**
             * @description Display name.
             * @example Production
             */
            name: string;
            /**
             * Format: int64
             * @description Optional initial head (employee id).
             * @example 42
             */
            headId?: number;
            /**
             * @description Marks the department as a production unit (factory floor). Defaults to false.
             * @example false
             */
            isProduction?: boolean;
        };
        /** @description Top-level org unit. Departments are flat — no nesting. */
        DepartmentDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 3
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example PROD
             */
            code?: string;
            /**
             * @description Display name.
             * @example Production
             */
            name?: string;
            /**
             * Format: int64
             * @description Department head's employee id, if assigned.
             */
            headId?: number | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /** @description Marks the department as a production unit (factory floor). */
            isProduction?: boolean;
        };
        CreateCityRequest: {
            /**
             * @description Catalog code (uppercase).
             * @example SKT
             */
            code: string;
            /**
             * @description Display name.
             * @example Sialkot
             */
            name: string;
            /**
             * Format: int64
             * @description Province id.
             * @example 1
             */
            provinceId: number;
        };
        /** @description City in Pakistan, scoped to a province. */
        CityDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 5
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example SKT
             */
            code?: string;
            /**
             * @description Display name.
             * @example Sialkot
             */
            name?: string;
            /**
             * Format: int64
             * @description Province id.
             * @example 1
             */
            provinceId?: number;
            /**
             * @description Province code (denormalized for filters).
             * @example PB
             */
            provinceCode?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 10
             */
            displayOrder?: number;
        };
        /** @description Patch a skill. Null fields are left unchanged. */
        UpdateSkillRequest: {
            /** @description Display name. */
            name?: string;
            /** @description Loose category. */
            category?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Patch a section. Null fields are left unchanged. */
        UpdateSectionRequest: {
            /** @description Display name. */
            name?: string;
            /**
             * Format: int64
             * @description Move to a different department.
             */
            departmentId?: number;
            /**
             * Format: int64
             * @description Section head's employee id. Pass `0` to clear.
             */
            headId?: number;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Patch a qualification. Null fields are left unchanged. */
        UpdateQualificationRequest: {
            /** @description Display name. */
            name?: string;
            /** @description Level enum value. */
            level?: string;
            /** @description Soft-deactivation flag. */
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
        /** @description Patch a province. Null fields are left unchanged. */
        UpdateProvinceRequest: {
            /**
             * @description Display name.
             * @example Punjab
             */
            name?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Patch a grade. Null fields are left unchanged. */
        UpdateGradeRequest: {
            /** @description Display name. */
            name?: string;
            /**
             * Format: int32
             * @description Higher = more senior.
             */
            level?: number;
            /** @description Optional description. */
            description?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * @description Role bucket. MANAGEMENT / SUPERVISOR / LABOUR.
             * @enum {string}
             */
            gradeType?: "MANAGEMENT" | "SUPERVISOR" | "LABOUR";
        };
        /** @description HR patch on the employee record. Null fields are left unchanged. */
        UpdateEmployeeRequest: {
            fullName?: string;
            cnic?: string;
            /** Format: date */
            cnicIssueDate?: string;
            /** Format: date */
            cnicExpiryDate?: string;
            ntn?: string;
            insuranceNumber?: string;
            sscNumber?: string;
            eobiNumber?: string;
            /** Format: date */
            dateOfBirth?: string;
            gender?: string;
            maritalStatus?: string;
            fatherName?: string;
            motherName?: string;
            spouseName?: string;
            religion?: string;
            bloodGroup?: string;
            /** Format: email */
            personalEmail?: string;
            /** Format: email */
            workEmail?: string;
            phone?: string;
            permanentAddress?: string;
            currentAddress?: string;
            /** Format: int64 */
            cityId?: number;
            /** @enum {string} */
            employmentType?: "PERMANENT" | "CONTRACT" | "INTERN";
            /** @description Avatar object key in MinIO. */
            profilePictureKey?: string;
        };
        /** @description Patch. Null fields are left unchanged. */
        UpdateEmployeeSkillRequest: {
            /** @enum {string} */
            proficiency?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
            years?: number;
            notes?: string;
        };
        /** @description Patch. Null fields are left unchanged. */
        UpdateEmployeeQualificationRequest: {
            institution?: string;
            boardOrUniversity?: string;
            /** Format: int32 */
            yearOfCompletion?: number;
            grade?: string;
            documentKey?: string;
        };
        /** @description Patch. Null fields are left unchanged. */
        UpdatePriorExperienceRequest: {
            employerName?: string;
            designation?: string;
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string;
            responsibilities?: string;
            reasonForLeaving?: string;
        };
        /** @description Patch. Null fields are left unchanged. */
        UpdateEmergencyContactRequest: {
            name?: string;
            relation?: string;
            phone?: string;
            primary?: boolean;
        };
        /** @description Patch the description / severity / document key. The disability id itself is immutable — to change it, delete the row and add a new one. */
        UpdateEmployeeDisabilityRequest: {
            description?: string | null;
            /** @enum {string|null} */
            severity?: "MILD" | "MODERATE" | "SEVERE" | null;
            documentKey?: string | null;
        };
        /** @description Patch. Null fields are left unchanged. */
        UpdateCertificationRequest: {
            name?: string;
            issuingBody?: string;
            /** Format: date */
            issueDate?: string;
            /** Format: date */
            expiryDate?: string;
            credentialId?: string;
            documentKey?: string;
        };
        /** @description Self-serve patch under /employees/me. Limited to contact + address fields. */
        UpdateMeRequest: {
            /** @description Mobile number. */
            phone?: string;
            /**
             * Format: email
             * @description Personal email (work email stays HR-managed).
             */
            personalEmail?: string;
            /** @description Permanent (home) address — street + area. */
            permanentAddress?: string;
            /** @description Current (residential) address. */
            currentAddress?: string;
            /**
             * Format: int64
             * @description City id (provinces resolved via the city join).
             */
            cityId?: number;
        };
        /** @description HR-admin partial update. Null fields are unchanged. `code` is immutable. */
        UpdateDisabilityRequest: {
            name?: string | null;
            active?: boolean | null;
        };
        /** @description Patch a designation. Null fields are left unchanged. */
        UpdateDesignationRequest: {
            /** @description Display title. */
            title?: string;
            /**
             * @description DIRECT vs INDIRECT.
             * @enum {string|null}
             */
            category?: "DIRECT" | "INDIRECT" | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Patch a department. Null fields are left unchanged. */
        UpdateDepartmentRequest: {
            /** @description Display name. */
            name?: string;
            /**
             * Format: int64
             * @description Department head's employee id. Pass `0` to clear.
             */
            headId?: number;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /** @description Marks the department as a production unit (factory floor). */
            isProduction?: boolean;
        };
        /** @description Patch a city. Null fields are left unchanged. */
        UpdateCityRequest: {
            /**
             * @description Display name.
             * @example Sialkot
             */
            name?: string;
            /**
             * Format: int64
             * @description Move to a different province.
             * @example 1
             */
            provinceId?: number;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Reverse-lookup row: an employee who holds a given skill. */
        SkillEmployeeDto: {
            /** Format: int64 */
            employeeSkillId?: number;
            /** Format: int64 */
            employeeId?: number;
            /**
             * @description Denormalized employee code.
             * @example FS-2026-0042
             */
            employeeCode?: string;
            /** @description Denormalized employee name. */
            fullName?: string;
            /** @enum {string} */
            proficiency?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
            years?: number | null;
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
        StreamingResponseBody: unknown;
        /** @description Reverse-lookup row: an employee who holds a given qualification. */
        QualificationEmployeeDto: {
            /** Format: int64 */
            employeeQualificationId?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @example FS-2026-0042 */
            employeeCode?: string;
            fullName?: string;
            institution?: string;
            /** Format: int32 */
            yearOfCompletion?: number | null;
        };
        PageResponseEmployeeDto: {
            content?: components["schemas"]["EmployeeDto"][];
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
        /** @description One row of the employee's org-assignment history. */
        EmployeeHistoryDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            effectiveFrom?: string;
            /**
             * Format: date
             * @description Null on the current row.
             */
            effectiveTo?: string | null;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int64 */
            designationId?: number | null;
            /** Format: int64 */
            gradeId?: number | null;
            /** Format: int64 */
            managerId?: number | null;
            /** @enum {string|null} */
            changeReason?: "ONBOARD" | "TRANSFER" | "PROMOTION" | "DEMOTION" | "DEPARTMENT_CHANGE" | "MANAGER_CHANGE" | "SECTION_CHANGE" | "EXIT" | null;
            notes?: string | null;
        };
        PageResponseEmployeeHistoryDto: {
            content?: components["schemas"]["EmployeeHistoryDto"][];
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
        /** @description Point-in-time assignment row + resolved catalog labels and manager summary. */
        EmployeeHistoryRow: {
            /**
             * Format: int64
             * @description history row id
             */
            id?: number;
            /**
             * Format: date
             * @description Inclusive start of this assignment.
             */
            effectiveFrom?: string;
            /**
             * Format: date
             * @description Inclusive end. Null when this is the current row.
             */
            effectiveTo?: string | null;
            /** @description ONBOARD / TRANSFER / PROMOTION / DEMOTION / DEPARTMENT_CHANGE / MANAGER_CHANGE / SECTION_CHANGE / EXIT. */
            changeReason?: string | null;
            /** @description Free-text note captured at the time of change. */
            notes?: string | null;
            department?: components["schemas"]["DepartmentDto"];
            section?: components["schemas"]["SectionDto"];
            designation?: components["schemas"]["DesignationDto"];
            grade?: components["schemas"]["GradeDto"];
            manager?: components["schemas"]["ManagerSummary"];
            /** @description Raw upstream row, in case the FE wants the unresolved ids. */
            raw?: components["schemas"]["EmployeeHistoryDto"];
        };
        /** @description Lean view of an employee's direct manager — enough to render the manager strip. */
        ManagerSummary: {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeCode?: string;
            profilePictureUrl?: string | null;
        };
        PageResponseEmployeeHistoryRow: {
            content?: components["schemas"]["EmployeeHistoryRow"][];
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
        PageResponseEmployeeDocumentDto: {
            content?: components["schemas"]["EmployeeDocumentDto"][];
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
        /** @description Per-employee disciplinary list plus computed UPHELD-only warning counts per level for the requested fiscal year. */
        DisciplinaryActionListResponse: {
            /** @description Disciplinary rows, newest first. */
            actions?: components["schemas"]["DisciplinaryActionDto"][];
            /**
             * Format: int32
             * @description Fiscal year code (the calendar year the FY ends in — e.g. 2026 for FY 2025-26).
             * @example 2026
             */
            fyCode?: number;
            /** @description UPHELD-only count per level inside the requested FY window. */
            warningCounts?: {
                [key: string]: number;
            };
        };
        /** @description One row of the employee's contract history. */
        EmploymentContractDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            contractType?: "PROBATION" | "PERMANENT" | "FIXED_TERM";
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string | null;
            /** @enum {string} */
            status?: "ACTIVE" | "EXPIRED" | "TERMINATED";
            /**
             * Format: int32
             * @description Only present on PROBATION contracts.
             */
            probationDays?: number | null;
            extended?: boolean;
            documentKey?: string | null;
            /** @description Short-lived signed GET URL for the contract document. Null when no document is attached or the storage call failed. */
            documentUrl?: string | null;
        };
        /** @description Headline KPIs for the People-overview screen — counts by status / type / probation windows / cert windows / ESS coverage. */
        EmployeeStats: {
            /**
             * Format: int64
             * @description Total non-terminated employees.
             */
            total?: number;
            /** @description Counts keyed by EmploymentStatus. */
            byStatus?: {
                [key: string]: number;
            };
            /** @description Counts keyed by EmploymentType (excluding TERMINATED rows). */
            byEmploymentType?: {
                [key: string]: number;
            };
            probation?: components["schemas"]["EmployeeStats.Probation"];
            certifications?: components["schemas"]["EmployeeStats.Certifications"];
            ess?: components["schemas"]["EmployeeStats.Ess"];
        };
        "EmployeeStats.Certifications": {
            /** Format: int64 */
            expiringWithin30?: number;
            /** Format: int64 */
            expired?: number;
        };
        "EmployeeStats.Ess": {
            /** Format: int64 */
            granted?: number;
            /** Format: int64 */
            ungranted?: number;
            /** Format: double */
            coveragePct?: number;
        };
        "EmployeeStats.Probation": {
            /** Format: int64 */
            active?: number;
            /** Format: int64 */
            dueWithin14?: number;
            /** Format: int64 */
            overdue?: number;
        };
        /** @description Compact picker row: id, name, code, department, designation. */
        EmployeePickerCandidate: {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeCode?: string;
            departmentName?: string | null;
            designationName?: string | null;
        };
        /** @description Response envelope for `/employees/picker` — name + code typeahead. */
        EmployeePickerResponse: {
            items?: components["schemas"]["EmployeePickerCandidate"][];
        };
        /** @description Recursive manager → reports tree, depth-bounded to keep responses sane. */
        OrgTree: {
            root?: components["schemas"]["OrgTree.Node"];
            /** Format: int32 */
            depth?: number;
        };
        "OrgTree.Node": {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeCode?: string;
            designationTitle?: string | null;
            profilePictureUrl?: string | null;
            /** Format: int32 */
            directReportsCount?: number;
            children?: components["schemas"]["OrgTree.Node"][];
        };
        /** @description Bundle of every catalog the onboarding wizard reads on its first paint. Pulls 8 employee-side endpoints in one round-trip. The wizard fetches `/admin/roles` from auth-service separately when the caller has `auth.users.write`. */
        OnboardingLookups: {
            departments?: components["schemas"]["DepartmentDto"][];
            sections?: components["schemas"]["SectionDto"][];
            designations?: components["schemas"]["DesignationDto"][];
            grades?: components["schemas"]["GradeDto"][];
            provinces?: components["schemas"]["ProvinceDto"][];
            cities?: components["schemas"]["CityDto"][];
            qualifications?: components["schemas"]["QualificationDto"][];
            skills?: components["schemas"]["SkillDto"][];
        };
        /** @description Joiners / leavers / end-of-month headcount over a trailing window. Computed once on the server so the HR dashboard chart can render with one round-trip rather than reducing 12+ queries client-side. */
        HeadcountTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["HeadcountTrendDto.Month"][];
        };
        "HeadcountTrendDto.Month": {
            /** @example 2026-04 */
            month?: string;
            /** Format: int64 */
            joiners?: number;
            /** Format: int64 */
            leavers?: number;
            /** Format: int64 */
            endHeadcount?: number;
        };
        /** @description One row in the directory list — employee record + the catalog labels HR's table renders. */
        EmployeeDirectoryRow: {
            employee?: components["schemas"]["EmployeeDto"];
            /** @description Resolved id→label for department, section, designation, grade, city + manager. */
            resolved?: components["schemas"]["EmployeeResolvedLabels"];
        };
        /** @description Resolved id→label rows for the catalogs the page renders. */
        EmployeeResolvedLabels: {
            department?: components["schemas"]["DepartmentDto"];
            section?: components["schemas"]["SectionDto"];
            designation?: components["schemas"]["DesignationDto"];
            grade?: components["schemas"]["GradeDto"];
            city?: components["schemas"]["CityDto"];
            manager?: components["schemas"]["ManagerSummary"];
        };
        PageResponseEmployeeDirectoryRow: {
            content?: components["schemas"]["EmployeeDirectoryRow"][];
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
        /** @description A user the caller is allowed to delegate approval authority to. Scoped server-side — the caller cannot delegate to themselves, and only employees with active ESS access (a linked auth user) appear. `role` is left null for v1; the FE can fall back to displaying the employee code. */
        DelegationCandidate: {
            /** Format: int64 */
            userId?: number;
            fullName?: string;
            employeeCode?: string;
            /** @description Reserved for v2 — auth role of the candidate. */
            role?: string | null;
        };
        /** @description List wrapper for the delegation picker. Wrapped (not bare) so future fields (scope, hint counts) can be added without a breaking change. */
        DelegationCandidatesResponse: {
            users?: components["schemas"]["DelegationCandidate"][];
        };
        /** @description Identity-only employee row keyed by userId. Returned by the by-user-ids resolver to surface profile-picture URL + display name without requiring `employee.read`. */
        EmployeeIdentitySummary: {
            /**
             * Format: int64
             * @description User id this employee is linked to.
             * @example 42
             */
            userId: number;
            /**
             * Format: int64
             * @description Employee id.
             * @example 108
             */
            employeeId: number;
            /**
             * @description Display name (denormalized from auth.user.fullName at onboarding, kept in sync).
             * @example Ahmed Khan
             */
            fullName: string;
            /**
             * @description Pre-signed GET URL for the avatar; null if no picture set.
             * @example https://minio.local/employee-docs/employees/108/avatar.jpg?…
             */
            profilePictureUrl?: string;
            /**
             * @description Raw object key (callers can re-sign / cache if needed).
             * @example employees/108/avatar.jpg
             */
            profilePictureKey?: string;
        };
        AggregateBucket: {
            /**
             * @description Group key as a string. For id-shaped groupings (department, section, grade, designation) this is the id; for enum-shaped groupings (status, employmentType, gender) this is the enum/value name.
             * @example 3
             */
            key?: string | null;
            /** @description Optional human-readable label — populated for id-shaped groupings via the catalog row, null otherwise. */
            label?: string | null;
            /** Format: int64 */
            count?: number;
        };
        /** @description Grouped headcount used by overview-page charts. `buckets[]` is one row per group key; `total` is the sum across buckets. */
        AggregateDto: {
            /**
             * @description What field the rows are grouped by.
             * @example department
             */
            groupBy?: string;
            /**
             * Format: int64
             * @description Sum of all bucket counts.
             * @example 287
             */
            total?: number;
            buckets?: components["schemas"]["AggregateBucket"][];
        };
        PageResponseDisciplinaryActionDto: {
            content?: components["schemas"]["DisciplinaryActionDto"][];
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
        PageResponseEmployeeCertificationDto: {
            content?: components["schemas"]["EmployeeCertificationDto"][];
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
        TenureBandCounts: {
            /** Format: int64 */
            under1y?: number;
            /** Format: int64 */
            y1to3?: number;
            /** Format: int64 */
            y3to5?: number;
            /** Format: int64 */
            y5to10?: number;
            /** Format: int64 */
            y10plus?: number;
        };
        TenureBandSplit: {
            key?: string;
            counts?: components["schemas"]["TenureBandCounts"];
        };
        TenureBandsDto: {
            /** Format: int64 */
            total?: number;
            overall?: components["schemas"]["TenureBandCounts"];
            splitBy?: string;
            splits?: components["schemas"]["TenureBandSplit"][];
        };
        AttritionMonth: {
            month?: string;
            /** Format: int64 */
            leavers?: number;
            /** Format: int64 */
            startHeadcount?: number;
            /** Format: int64 */
            endHeadcount?: number;
            ratePct?: number;
        };
        AttritionTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["AttritionMonth"][];
            rollingTwelveMonthPct?: number;
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
    list: {
        parameters: {
            query?: {
                /** @description Filter by category — TECH | LANGUAGE | TRADE | SPORT | OTHER. */
                category?: string;
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["SkillDto"][];
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
                "application/json": components["schemas"]["CreateSkillRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SkillDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SkillDto"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                /** @description Restrict to a single department. */
                departmentId?: number;
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["SectionDto"][];
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
                "application/json": components["schemas"]["CreateSectionRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SectionDto"];
                };
            };
            /** @description Parent department not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SectionDto"];
                };
            };
            /** @description Code already taken or department inactive. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SectionDto"];
                };
            };
        };
    };
    retry: {
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
            /** @description Saga re-armed (or unchanged if not FAILED). */
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
    list_2: {
        parameters: {
            query?: {
                /** @description Filter by level enum value. */
                level?: string;
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["QualificationDto"][];
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
                "application/json": components["schemas"]["CreateQualificationRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["QualificationDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["QualificationDto"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query?: {
                /** @description Filter by active flag. Omit to return all. */
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
                    "application/json": components["schemas"]["ProvinceDto"][];
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
                "application/json": components["schemas"]["CreateProvinceRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProvinceDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProvinceDto"];
                };
            };
        };
    };
    list_4: {
        parameters: {
            query?: {
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["GradeDto"][];
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
                "application/json": components["schemas"]["CreateGradeRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GradeDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GradeDto"];
                };
            };
        };
    };
    list_5: {
        parameters: {
            query?: {
                /** @description Filter by employment status. Omit to list every non-terminated row. */
                status?: "ACTIVE" | "ON_NOTICE" | "ON_LEAVE" | "SUSPENDED" | "TERMINATED";
                /** @description Filter by employment type. */
                employmentType?: "PERMANENT" | "CONTRACT" | "INTERN";
                /** @description Filter by department id. */
                departmentId?: number;
                /** @description Filter by section id. */
                sectionId?: number;
                /** @description Filter by direct manager id. */
                managerId?: number;
                /** @description Filter by grade id. */
                gradeId?: number;
                /** @description Filter by designation id. */
                designationId?: number;
                /** @description True = only ESS-linked, false = only non-ESS. */
                hasEss?: boolean;
                /** @description Case-insensitive substring search across fullName, employeeCode, workEmail, phone and cnic. */
                q?: string;
                /** @description Days from today; overdue rows (≤0 days remaining) are included. */
                probationDueWithin?: number;
                /** @description Set to true to list only overdue probations (end_date < today). */
                probationOverdue?: boolean;
                /** @description Set to true to include TERMINATED rows when no explicit `status` filter is set. */
                includeTerminated?: boolean;
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
                    "application/json": components["schemas"]["PageResponseEmployeeDto"];
                };
            };
        };
    };
    onboard: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OnboardEmployeeRequest"];
            };
        };
        responses: {
            /** @description Employee created. `sagaId` set when any cross-service work was requested. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OnboardEmployeeResponse"];
                };
            };
            /** @description Manager not found / catalog inactive / duplicate qualification/skill/disability in payload / section-department mismatch. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OnboardEmployeeResponse"];
                };
            };
            /** @description Validation failure. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OnboardEmployeeResponse"];
                };
            };
        };
    };
    withdraw: {
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
                "application/json": components["schemas"]["WithdrawExitRequest"];
            };
        };
        responses: {
            /** @description No exit row. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
            /** @description Exit not in PENDING state. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
        };
    };
    terminate: {
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
                "application/json": components["schemas"]["TerminateRequest"];
            };
        };
        responses: {
            /** @description Already terminated / direct reports unaccounted for. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
        };
    };
    suspend: {
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
                "application/json": components["schemas"]["SuspendRequest"];
            };
        };
        responses: {
            /** @description Already suspended / not in a suspendable state. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SuspensionDto"];
                };
            };
        };
    };
    list_6: {
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
                    "application/json": components["schemas"]["EmployeeSkillDto"][];
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
                "application/json": components["schemas"]["AddEmployeeSkillRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSkillDto"];
                };
            };
            /** @description Skill already attached, or skill is inactive. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSkillDto"];
                };
            };
        };
    };
    changeSection: {
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
                "application/json": components["schemas"]["AssignmentChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    revokeEss: {
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
            /** @description Employee has no ESS. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    resign: {
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
                "application/json": components["schemas"]["ResignRequest"];
            };
        };
        responses: {
            /** @description Pending exit already exists / direct reports unaccounted for. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
            /** @description lastWorkingDay must be in the future. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
        };
    };
    list_7: {
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
                    "application/json": components["schemas"]["EmployeeQualificationDto"][];
                };
            };
        };
    };
    add_1: {
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
                "application/json": components["schemas"]["AddEmployeeQualificationRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeQualificationDto"];
                };
            };
        };
    };
    setProfilePicture: {
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
                "application/json": components["schemas"]["SetProfilePictureRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    uploadUrl: {
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
                "application/json": components["schemas"]["ProfilePictureFilenameRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UploadUrlResponse"];
                };
            };
        };
    };
    list_8: {
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
                    "application/json": components["schemas"]["PriorExperienceDto"][];
                };
            };
        };
    };
    add_2: {
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
                "application/json": components["schemas"]["AddPriorExperienceRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriorExperienceDto"];
                };
            };
        };
    };
    changeManager: {
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
                "application/json": components["schemas"]["AssignmentChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    liftSuspension: {
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
                "application/json": components["schemas"]["LiftSuspensionRequest"];
            };
        };
        responses: {
            /** @description No active suspension. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SuspensionDto"];
                };
            };
        };
    };
    grantEss: {
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
                "application/json": components["schemas"]["GrantEssRequest"];
            };
        };
        responses: {
            /** @description Employee already has ESS. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GrantEssResponse"];
                };
            };
        };
    };
    changeGrade: {
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
                "application/json": components["schemas"]["AssignmentChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    extendProbation: {
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
                "application/json": components["schemas"]["ExtendProbationRequest"];
            };
        };
        responses: {
            /** @description Probation already extended. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
            /** @description Invalid days. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    list_9: {
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
                    "application/json": components["schemas"]["EmergencyContactDto"][];
                };
            };
        };
    };
    create_5: {
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
                "application/json": components["schemas"]["CreateEmergencyContactRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmergencyContactDto"];
                };
            };
        };
    };
    list_10: {
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
                    "application/json": components["schemas"]["PageResponseEmployeeDocumentDto"];
                };
            };
        };
    };
    record: {
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
                "application/json": components["schemas"]["RecordDocumentRequest"];
            };
        };
        responses: {
            /** @description Recorded. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDocumentDto"];
                };
            };
            /** @description Object not in storage. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDocumentDto"];
                };
            };
        };
    };
    uploadUrl_1: {
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
                "application/json": components["schemas"]["UploadUrlRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UploadUrlResponse"];
                };
            };
        };
    };
    listForEmployee: {
        parameters: {
            query?: {
                /** @description Filter rows by level. */
                level?: "VERBAL_WARNING" | "FIRST_WARNING" | "SECOND_WARNING" | "FINAL_WARNING";
                /** @description Filter rows by status. */
                status?: "ISSUED" | "CLOSED";
                /** @description FY ending year. Defaults to the current FY. */
                fyCode?: number;
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
                    "application/json": components["schemas"]["DisciplinaryActionListResponse"];
                };
            };
        };
    };
    issue: {
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
                "application/json": components["schemas"]["IssueDisciplinaryRequest"];
            };
        };
        responses: {
            /** @description Action recorded. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisciplinaryActionDto"];
                };
            };
            /** @description Employee not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisciplinaryActionDto"];
                };
            };
        };
    };
    list_11: {
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
                    "application/json": components["schemas"]["EmployeeDisabilityDto"][];
                };
            };
        };
    };
    add_3: {
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
                "application/json": components["schemas"]["AddEmployeeDisabilityRequest"];
            };
        };
        responses: {
            /** @description Linked. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDisabilityDto"];
                };
            };
            /** @description EMPLOYEE_DISABILITY_DUPLICATE. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDisabilityDto"];
                };
            };
        };
    };
    changeDesignation: {
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
                "application/json": components["schemas"]["AssignmentChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    changeDepartment: {
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
                "application/json": components["schemas"]["AssignmentChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    confirm: {
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
                "application/json": components["schemas"]["ConfirmEmployeeRequest"];
            };
        };
        responses: {
            /** @description No active probation contract. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    list_12: {
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
                    "application/json": components["schemas"]["EmployeeCertificationDto"][];
                };
            };
        };
    };
    add_4: {
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
                "application/json": components["schemas"]["AddCertificationRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeCertificationDto"];
                };
            };
        };
    };
    setMyProfilePicture: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetProfilePictureRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    profilePictureUploadUrl: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfilePictureFilenameRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UploadUrlResponse"];
                };
            };
        };
    };
    myEmergencyContacts: {
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
                    "application/json": components["schemas"]["EmergencyContactDto"][];
                };
            };
        };
    };
    addMyEmergencyContact: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateEmergencyContactRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmergencyContactDto"];
                };
            };
        };
    };
    decide: {
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
                "application/json": components["schemas"]["DecideDisciplinaryRequest"];
            };
        };
        responses: {
            /** @description Action not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisciplinaryActionDto"];
                };
            };
            /** @description Already decided. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisciplinaryActionDto"];
                };
            };
        };
    };
    list_13: {
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
                    "application/json": components["schemas"]["DisabilityDto"][];
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
                "application/json": components["schemas"]["CreateDisabilityRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisabilityDto"];
                };
            };
            /** @description DISABILITY_CODE_TAKEN. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisabilityDto"];
                };
            };
        };
    };
    list_14: {
        parameters: {
            query?: {
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["DesignationDto"][];
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
                "application/json": components["schemas"]["CreateDesignationRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DesignationDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DesignationDto"];
                };
            };
        };
    };
    list_15: {
        parameters: {
            query?: {
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["DepartmentDto"][];
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
                "application/json": components["schemas"]["CreateDepartmentRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartmentDto"];
                };
            };
            /** @description Code already taken. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartmentDto"];
                };
            };
        };
    };
    list_16: {
        parameters: {
            query?: {
                /** @description Restrict to a single province. */
                provinceId?: number;
                /** @description Filter by active flag. */
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
                    "application/json": components["schemas"]["CityDto"][];
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
                "application/json": components["schemas"]["CreateCityRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CityDto"];
                };
            };
            /** @description Province not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CityDto"];
                };
            };
            /** @description Code already taken or referenced province inactive. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CityDto"];
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
                "application/json": components["schemas"]["UpdateSkillRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SkillDto"];
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
                "application/json": components["schemas"]["UpdateSectionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SectionDto"];
                };
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
                "application/json": components["schemas"]["UpdateQualificationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["QualificationDto"];
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
            /** @description One or more qualification ids not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["QualificationDto"][];
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
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateProvinceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProvinceDto"];
                };
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
                "application/json": components["schemas"]["UpdateGradeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GradeDto"];
                };
            };
        };
    };
    reorder_1: {
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
            /** @description One or more grade ids not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GradeDto"][];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Employee id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Employee not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
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
                "application/json": components["schemas"]["UpdateEmployeeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
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
                employeeSkillId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Removed. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                employeeSkillId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmployeeSkillRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeSkillDto"];
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
                empQualId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Removed. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_7: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                empQualId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmployeeQualificationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeQualificationDto"];
                };
            };
        };
    };
    delete_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                expId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_8: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                expId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdatePriorExperienceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriorExperienceDto"];
                };
            };
        };
    };
    delete_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                contactId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_9: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                contactId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmergencyContactRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmergencyContactDto"];
                };
            };
        };
    };
    delete_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                linkId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_10: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                linkId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmployeeDisabilityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDisabilityDto"];
                };
            };
        };
    };
    delete_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                certId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_11: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                certId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateCertificationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeCertificationDto"];
                };
            };
        };
    };
    me: {
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
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    updateMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateMeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    deleteMyEmergencyContact: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                contactId: number;
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
                content?: never;
            };
        };
    };
    updateMyEmergencyContact: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                contactId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmergencyContactRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmergencyContactDto"];
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
                    "application/json": components["schemas"]["DisabilityDto"];
                };
            };
        };
    };
    update_12: {
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
                "application/json": components["schemas"]["UpdateDisabilityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisabilityDto"];
                };
            };
        };
    };
    update_13: {
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
                "application/json": components["schemas"]["UpdateDesignationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DesignationDto"];
                };
            };
        };
    };
    update_14: {
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
                "application/json": components["schemas"]["UpdateDepartmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartmentDto"];
                };
            };
        };
    };
    update_15: {
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
                "application/json": components["schemas"]["UpdateCityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CityDto"];
                };
            };
        };
    };
    reorder_2: {
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
            /** @description One or more city ids not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CityDto"][];
                };
            };
        };
    };
    employeesWithSkill: {
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
                    "application/json": components["schemas"]["SkillEmployeeDto"][];
                };
            };
        };
    };
    list_17: {
        parameters: {
            query?: {
                /** @description Filter by status (RUNNING / COMPLETED / FAILED). */
                status?: "RUNNING" | "COMPLETED" | "FAILED";
                /** @description Filter by saga name (e.g. ONBOARDING). */
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
    get_1: {
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
                /** @description Saga name, e.g. ONBOARDING. */
                name: string;
                /** @description Correlation key, e.g. employee:1042. */
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
    serviceLetter: {
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
                    "application/pdf": string;
                };
            };
        };
    };
    experienceLetter: {
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
                    "application/pdf": string;
                };
            };
        };
    };
    leavers: {
        parameters: {
            query: {
                from: string;
                to: string;
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
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    joiners: {
        parameters: {
            query: {
                from: string;
                to: string;
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
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    headcountSnapshot: {
        parameters: {
            query?: {
                /** @description As-of date; defaults to today. */
                asOf?: string;
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
                    "application/pdf": string;
                };
            };
        };
    };
    documentExpiries: {
        parameters: {
            query: {
                /** @description Window in days; common values are 30 / 60 / 90 / 180. */
                days: number;
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
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    directoryCsv: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description CSV stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/csv;charset=UTF-8": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    contractExpiries: {
        parameters: {
            query: {
                /** @description Window in days; common values are 30 / 60 / 90 / 180. */
                days: number;
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
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    confirmationsDue: {
        parameters: {
            query: {
                days: number;
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
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    employeesWithQualification: {
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
                    "application/json": components["schemas"]["QualificationEmployeeDto"][];
                };
            };
        };
    };
    team: {
        parameters: {
            query?: {
                /** @description Case-insensitive substring search across fullName, employeeCode, workEmail, phone and cnic. */
                q?: string;
            };
            header?: never;
            path: {
                /** @description Manager employee id. */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Employee not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseEmployeeDto"];
                };
            };
        };
    };
    listSuspensions: {
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
                    "application/json": components["schemas"]["SuspensionDto"][];
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
                    "application/json": components["schemas"]["PageResponseEmployeeHistoryDto"];
                };
            };
        };
    };
    historyResolved: {
        parameters: {
            query?: {
                /** @description Page size (default 20, max 200). */
                size?: number;
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
                    "application/json": components["schemas"]["PageResponseEmployeeHistoryRow"];
                };
            };
        };
    };
    exit: {
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
            /** @description No exit row. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
        };
    };
    listContracts: {
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
                    "application/json": components["schemas"]["EmploymentContractDto"][];
                };
            };
        };
    };
    stats: {
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
                    "application/json": components["schemas"]["EmployeeStats"];
                };
            };
        };
    };
    picker: {
        parameters: {
            query?: {
                /** @description Substring search across fullName / employeeCode (also workEmail / phone / cnic). Empty returns first page by code. */
                q?: string;
                /** @description Max rows. Default 20, capped at 100. */
                limit?: number;
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
                    "application/json": components["schemas"]["EmployeePickerResponse"];
                };
            };
        };
    };
    orgTree: {
        parameters: {
            query?: {
                /** @description Root employee id. Omit to start at top-of-org. */
                root?: number;
                /** @description Tree depth (default 3, max 6). */
                depth?: number;
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
                    "application/json": components["schemas"]["OrgTree"];
                };
            };
        };
    };
    onboardingLookups: {
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
                    "application/json": components["schemas"]["OnboardingLookups"];
                };
            };
        };
    };
    myTeam: {
        parameters: {
            query?: {
                /** @description Case-insensitive substring search across fullName, employeeCode, workEmail, phone and cnic. */
                q?: string;
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
                    "application/json": components["schemas"]["PageResponseEmployeeDto"];
                };
            };
        };
    };
    mySuspensions: {
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
                    "application/json": components["schemas"]["SuspensionDto"][];
                };
            };
        };
    };
    mySkills: {
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
                    "application/json": components["schemas"]["EmployeeSkillDto"][];
                };
            };
        };
    };
    myQualifications: {
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
                    "application/json": components["schemas"]["EmployeeQualificationDto"][];
                };
            };
        };
    };
    myPriorExperience: {
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
                    "application/json": components["schemas"]["PriorExperienceDto"][];
                };
            };
        };
    };
    myManager: {
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
                    "application/json": components["schemas"]["EmployeeDto"];
                };
            };
        };
    };
    myHistory: {
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
                    "application/json": components["schemas"]["PageResponseEmployeeHistoryDto"];
                };
            };
        };
    };
    myExit: {
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
                    "application/json": components["schemas"]["ExitDto"];
                };
            };
        };
    };
    myDocuments: {
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
                    "application/json": components["schemas"]["PageResponseEmployeeDocumentDto"];
                };
            };
        };
    };
    myDisciplinaryActions: {
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
                    "application/json": components["schemas"]["DisciplinaryActionListResponse"];
                };
            };
        };
    };
    myContracts: {
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
                    "application/json": components["schemas"]["EmploymentContractDto"][];
                };
            };
        };
    };
    myCertifications: {
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
                    "application/json": components["schemas"]["EmployeeCertificationDto"][];
                };
            };
        };
    };
    headcountTrend: {
        parameters: {
            query?: {
                /** @description Number of months in the trailing window. Clamped to [1, 24]. */
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
                    "application/json": components["schemas"]["HeadcountTrendDto"];
                };
            };
        };
    };
    directory: {
        parameters: {
            query?: {
                status?: "ACTIVE" | "ON_NOTICE" | "ON_LEAVE" | "SUSPENDED" | "TERMINATED";
                employmentType?: "PERMANENT" | "CONTRACT" | "INTERN";
                departmentId?: number;
                sectionId?: number;
                managerId?: number;
                gradeId?: number;
                designationId?: number;
                hasEss?: boolean;
                q?: string;
                probationDueWithin?: number;
                probationOverdue?: boolean;
                includeTerminated?: boolean;
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
                    "application/json": components["schemas"]["PageResponseEmployeeDirectoryRow"];
                };
            };
        };
    };
    delegationCandidates: {
        parameters: {
            query?: {
                /** @description Substring search across fullName / employeeCode. */
                q?: string;
                limit?: number;
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
                    "application/json": components["schemas"]["DelegationCandidatesResponse"];
                };
            };
        };
    };
    byUserIds: {
        parameters: {
            query: {
                /** @description Comma-separated user ids; max 200 per call. */
                userIds: number[];
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
                    "application/json": components["schemas"]["EmployeeIdentitySummary"][];
                };
            };
            /** @description Too many ids in a single request. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeIdentitySummary"][];
                };
            };
        };
    };
    byIds: {
        parameters: {
            query: {
                /** @description Employee ids; comma-separated or repeated query param. */
                ids: number[];
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
                    "application/json": components["schemas"]["EmployeeDto"][];
                };
            };
        };
    };
    aggregate: {
        parameters: {
            query: {
                /** @description One of: status, employmentType, gender, department, section, grade, designation. */
                groupBy: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Unsupported groupBy. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AggregateDto"];
                };
            };
        };
    };
    globalList: {
        parameters: {
            query?: {
                level?: "VERBAL_WARNING" | "FIRST_WARNING" | "SECOND_WARNING" | "FINAL_WARNING";
                category?: "ATTENDANCE" | "MISCONDUCT" | "PERFORMANCE" | "INSUBORDINATION" | "POLICY_VIOLATION" | "OTHER";
                status?: "ISSUED" | "CLOSED";
                from?: string;
                to?: string;
                /** @description FY ending year — narrows issuedOn to the FY window when from/to are absent. */
                fyCode?: number;
                /** @description Scope to a single employee. */
                employeeId?: number;
                /** @description Substring match across owning employee's name and code. */
                q?: string;
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
                    "application/json": components["schemas"]["PageResponseDisciplinaryActionDto"];
                };
            };
        };
    };
    expiringWithin: {
        parameters: {
            query?: {
                /** @description Days from today. Required unless `expired=true`. */
                expiringWithin?: number;
                /** @description Narrow to certs whose owning employee is in this department. */
                departmentId?: number;
                /** @description Narrow to certs whose owning employee is in this section. */
                sectionId?: number;
                /** @description true = already-expired only; false = future only; null = both. */
                expired?: boolean;
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
                    "application/json": components["schemas"]["PageResponseEmployeeCertificationDto"];
                };
            };
        };
    };
    tenureBands: {
        parameters: {
            query?: {
                /** @description Optional grouping: dept | grade. */
                splitBy?: string;
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
                    "application/json": components["schemas"]["TenureBandsDto"];
                };
            };
        };
    };
    headcountTrend_1: {
        parameters: {
            query?: {
                /** @description Trailing window in months. Clamped to [1, 24]. */
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
                    "application/json": components["schemas"]["HeadcountTrendDto"];
                };
            };
        };
    };
    attrition: {
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
                    "application/json": components["schemas"]["AttritionTrendDto"];
                };
            };
        };
    };
    delete_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                docId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
