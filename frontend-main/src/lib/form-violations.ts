// Convert RFC7807 `violations[]` from an ApiError into the shape AntD's
// Form.setFields() expects: { name: NamePath, errors: string[] }.
//
// Server `field` is dot-pathed (`address.city`), so we split and AntD treats
// it as a NamePath. Multiple violations on the same field are merged.
//
// Server fields don't always match the form's field names. Examples:
//   - auth-service password policy always reports the offending field as
//     `newPassword`, even when called from /admin/users (DTO field is
//     `temporaryPassword`) or /admin/users/{id}/password/reset.
//   - Some endpoints flatten nested DTOs in error responses.
// Pass `fieldMap` to translate server names → form names before applying.
// Anything left unmapped is returned as `unmapped` so the caller can surface
// a top-level alert; otherwise the error vanishes silently because AntD's
// setFields ignores unknown field names.

import type { FormInstance } from 'antd';
import { isApiError } from './api-error';
import { getErrorMessage } from './errors';
import type { Violation } from './problem-detail';

type FieldUpdate = { name: (string | number)[]; errors: string[] };

function toNamePath(field: string): (string | number)[] {
  return field
    .split('.')
    .map((seg) => (/^\d+$/.test(seg) ? Number(seg) : seg));
}

export function violationsToFields(violations: readonly Violation[]): FieldUpdate[] {
  const grouped = new Map<string, string[]>();
  for (const v of violations) {
    const list = grouped.get(v.field) ?? [];
    list.push(v.message);
    grouped.set(v.field, list);
  }
  return [...grouped].map(([field, errors]) => ({
    name: toNamePath(field),
    errors,
  }));
}

export type ApplyApiErrorOptions = {
  /**
   * Translate server field names (RFC7807 `violations[].field`) to the form's
   * field names. Values can be a single name or an array (apply to multiple
   * fields). Use this when the backend's DTO field name differs from the
   * form's — e.g. `{ newPassword: 'temporaryPassword' }`.
   */
  fieldMap?: Record<string, string | string[]>;
};

export type ApplyApiErrorResult = {
  /** Violations that were actually applied to a registered form field. */
  applied: readonly Violation[];
  /**
   * Violations the form couldn't display — either the error wasn't an
   * ApiError, the field name didn't match anything in the form (after
   * `fieldMap`), or there were no violations. The caller should surface
   * these as a top-level alert (`message.error` / `<Alert>`).
   */
  unmapped: readonly Violation[];
};

function remapField(field: string, fieldMap?: ApplyApiErrorOptions['fieldMap']): string[] {
  if (!fieldMap) return [field];
  const m = fieldMap[field];
  if (m === undefined) return [field];
  return Array.isArray(m) ? m : [m];
}

/**
 * Apply a thrown error to an AntD Form: per-field violations land as inline
 * errors. Returns `{ applied, unmapped }` so the caller can decide whether
 * to also surface a top-level alert. Violations whose field name doesn't
 * match a registered form field (after `fieldMap` translation) come back as
 * `unmapped` — without that the error would silently vanish.
 */
export function applyApiErrorToForm(
  form: FormInstance,
  e: unknown,
  options: ApplyApiErrorOptions = {},
): ApplyApiErrorResult {
  if (!isApiError(e) || e.violations.length === 0) {
    return { applied: [], unmapped: [] };
  }

  // Discover which field paths the form has registered so we can detect
  // unmapped violations. `getFieldsValue(true)` returns the full store
  // including untouched fields and nested values.
  const registered = collectFieldPaths(form.getFieldsValue(true));

  const applied: Violation[] = [];
  const unmapped: Violation[] = [];
  const updates: FieldUpdate[] = [];

  for (const v of e.violations) {
    const candidates = remapField(v.field, options.fieldMap);
    const hits = candidates.filter((c) => registered.has(c));
    if (hits.length === 0) {
      unmapped.push(v);
      continue;
    }
    applied.push(v);
    for (const name of hits) {
      updates.push({ name: toNamePath(name), errors: [v.message] });
    }
  }

  // Merge updates per field path so multi-violation fields show every message.
  const grouped = new Map<string, string[]>();
  for (const u of updates) {
    const key = u.name.join('.');
    const list = grouped.get(key) ?? [];
    list.push(...u.errors);
    grouped.set(key, list);
  }
  if (grouped.size > 0) {
    form.setFields(
      [...grouped].map(([key, errors]) => ({
        name: toNamePath(key),
        errors,
      })),
    );

    // Scroll + focus the first failing field. Without this, sighted users on
    // wide drawers don't notice inline errors and screen-reader users get no
    // signal that submit failed.
    const firstKey = [...grouped.keys()][0];
    if (firstKey) {
      const path = toNamePath(firstKey);
      // queueMicrotask so AntD has flushed setFields before we scroll/focus.
      queueMicrotask(() => {
        try {
          form.scrollToField(path, { behavior: 'smooth', block: 'center' });
        } catch {
          // ignore — field may not be mounted (conditional render)
        }
        const instance = form.getFieldInstance?.(path) as
          | { focus?: () => void; input?: { focus?: () => void } }
          | undefined;
        if (typeof instance?.focus === 'function') {
          instance.focus();
        } else if (typeof instance?.input?.focus === 'function') {
          instance.input.focus();
        }
      });
    }
  }

  return { applied, unmapped };
}

/**
 * The standard form-error onError handler. Wires every drawer / modal
 * mutation:
 *   - per-field violations land inline,
 *   - if no violation landed inline (network failure, unknown server
 *     error), a top-level toast surfaces `getErrorMessage(err)`,
 *   - if some violations landed inline but others didn't map to a field
 *     in the form, the first unmapped message surfaces as a toast so it
 *     doesn't vanish silently.
 */
export function handleFormError(
  form: FormInstance,
  err: unknown,
  message: { error: (msg: string) => void },
  options?: ApplyApiErrorOptions,
): ApplyApiErrorResult {
  const result = applyApiErrorToForm(form, err, options);
  if (result.applied.length === 0) {
    message.error(getErrorMessage(err));
  } else if (result.unmapped.length > 0) {
    message.error(result.unmapped[0]!.message);
  }
  return result;
}

function collectFieldPaths(store: unknown, prefix = ''): Set<string> {
  const paths = new Set<string>();
  if (store === null || typeof store !== 'object' || Array.isArray(store)) {
    return paths;
  }
  for (const [key, value] of Object.entries(store as Record<string, unknown>)) {
    const path = prefix ? `${prefix}.${key}` : key;
    paths.add(path);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const child of collectFieldPaths(value, path)) {
        paths.add(child);
      }
    }
  }
  return paths;
}
