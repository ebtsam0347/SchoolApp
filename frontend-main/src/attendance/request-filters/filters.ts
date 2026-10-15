// State + helpers behind RequestFilterBar — the employee / date range /
// status filters on the my / team / department request lists
// (/time/leaves, /time/short-leave).

export type RequestListFilters<S extends string> = {
  /** Undefined = every status. */
  status?: S;
  employeeId?: number;
  /** YYYY-MM-DD, inclusive. */
  from?: string;
  to?: string;
};

export const isFiltered = (f: RequestListFilters<string>) =>
  f.status != null || f.employeeId != null || !!f.from || !!f.to;

export type EmployeeOption = { value: number; label: string };

/** "Name · CODE" options, sorted by label. */
export function toEmployeeOptions(
  people: { id?: number | null; fullName?: string | null; employeeCode?: string | null }[],
): EmployeeOption[] {
  return people
    .filter((e): e is typeof e & { id: number } => e.id != null)
    .map((e) => ({
      value: e.id,
      label: e.employeeCode ? `${e.fullName ?? ''} · ${e.employeeCode}` : (e.fullName ?? `#${e.id}`),
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
}

/** Antd pagination for a 0-based server page; changing page size goes to page 1. */
export function sectionPagination(
  page: number,
  pageSize: number,
  total: number,
  setPage: (p: number) => void,
  setPageSize: (s: number) => void,
) {
  return {
    current: page + 1,
    pageSize,
    total,
    showSizeChanger: true,
    pageSizeOptions: [10, 20, 50, 100],
    showTotal: (t: number) => `${t} request${t === 1 ? '' : 's'}`,
    onChange: (p: number, sz: number) => {
      setPage(p - 1);
      if (sz !== pageSize) {
        setPageSize(sz);
        setPage(0);
      }
    },
  };
}
