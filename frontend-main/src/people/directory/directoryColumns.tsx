import { Tag, Tooltip, Typography, type TableProps } from 'antd';

import { OrgPath, PersonCell, StatusPill } from '@/components';
import { formatDateShort } from '@/lib';
import {
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_STATUS_TONE,
  EMPLOYMENT_TYPE_LABEL,
  type EmployeeDirectoryRow,
} from '../types';

const { Text } = Typography;

/** Stable column keys — mirrored in the route's `defaultHidden` list. */
export const DIRECTORY_COLUMN_KEYS = {
  fullName: 'fullName',
  employeeCode: 'employeeCode',
  designation: 'designation',
  org: 'org',
  grade: 'grade',
  manager: 'manager',
  manages: 'manages',
  status: 'status',
  employmentType: 'employmentType',
  joiningDate: 'joiningDate',
  confirmationDate: 'confirmationDate',
  city: 'city',
  workEmail: 'workEmail',
  phone: 'phone',
  hasEss: 'hasEss',
} as const;

/** Columns hidden by default until the user opts them in via the toggle.
 *  Employment status is shown by default. Joined + Phone + Work email are
 *  kept on because they're the next most-asked-for fields after the org
 *  context. */
export const DIRECTORY_DEFAULT_HIDDEN: ReadonlyArray<string> = [
  DIRECTORY_COLUMN_KEYS.employeeCode,
  DIRECTORY_COLUMN_KEYS.employmentType,
  DIRECTORY_COLUMN_KEYS.confirmationDate,
  DIRECTORY_COLUMN_KEYS.city,
  DIRECTORY_COLUMN_KEYS.hasEss,
];

const dash = <Text type="secondary">—</Text>;

export type DirectoryColumnOptions = {
  /**
   * Link names to `/people/:id`. Off for callers without `employee.read`
   * (Team requests' Profile tab), where that page would 403 — they open
   * their own drawer from the row instead.
   */
  linkProfiles?: boolean;
  /**
   * Server-side sort headers. Off when the rows are a fixed in-memory list
   * with no sort param to round-trip.
   */
  sortable?: boolean;
};

/**
 * `sort` is the route's `field,dir` search param. AntD only paints the sort
 * arrow on a column whose `sortOrder` is set, and server-side sorting means
 * the table never derives that itself — so the caller feeds the active sort
 * back in. Without this the default code-asc ordering would be invisible and
 * the first header click would look like it did nothing.
 */
export function buildDirectoryColumns(
  sort?: string,
  { linkProfiles = true, sortable = true }: DirectoryColumnOptions = {},
): NonNullable<TableProps<EmployeeDirectoryRow>['columns']> {
  const [sortKey, sortDir] = (sort ?? '').split(',');
  const orderFor = (key: string): 'ascend' | 'descend' | null =>
    sortKey === key ? (sortDir === 'desc' ? 'descend' : 'ascend') : null;

  const columns: NonNullable<TableProps<EmployeeDirectoryRow>['columns']> = [
    {
      key: DIRECTORY_COLUMN_KEYS.fullName,
      title: 'Employee',
      dataIndex: ['employee', 'fullName'],
      sorter: true,
      sortOrder: orderFor(DIRECTORY_COLUMN_KEYS.fullName),
      width: 280,
      render: (_v, row) => {
        const e = row.employee;
        if (!e?.id) return null;
        return (
          <PersonCell
            person={{
              fullName: e.fullName ?? '—',
              employeeCode: e.employeeCode,
              designation: row.resolved?.designation?.title ?? null,
              profilePictureUrl: e.profilePictureUrl,
            }}
            size="default"
            to={linkProfiles ? `/people/${e.id}` : undefined}
          />
        );
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.employeeCode,
      title: 'Card No',
      dataIndex: ['employee', 'employeeCode'],
      sorter: true,
      sortOrder: orderFor(DIRECTORY_COLUMN_KEYS.employeeCode),
      width: 110,
      render: (_v, row) =>
        row.employee?.employeeCode ? (
          <Text style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
            {row.employee.employeeCode}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.designation,
      title: 'Designation',
      width: 200,
      render: (_v, row) =>
        row.resolved?.designation?.title ? (
          <Text>{row.resolved.designation.title}</Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.org,
      title: 'Department / Section',
      width: 280,
      render: (_v, row) => {
        const deptName = row.resolved?.department?.name;
        const secName = row.resolved?.section?.name;
        // Dedupe when section name == department name (e.g. "Marketing" /
        // "Marketing"), otherwise the cell visually reads as a typo.
        const showSection = secName && secName !== deptName;
        return (
          <OrgPath
            compact
            segments={[
              deptName ? { label: deptName } : null,
              showSection ? { label: secName! } : null,
            ]}
          />
        );
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.grade,
      title: (
        <Tooltip title="Pay-band code (E = executive, S = staff, OG = officer-grade, A–D = worker bands).">
          <span>Grade</span>
        </Tooltip>
      ),
      width: 100,
      render: (_v, row) =>
        row.resolved?.grade?.name ? (
          <Text style={{ fontVariantNumeric: 'tabular-nums' }}>
            {row.resolved.grade.name}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.manager,
      title: 'Manager',
      width: 220,
      render: (_v, row) => {
        const m = row.resolved?.manager;
        if (!m?.id) return dash;
        return (
          <PersonCell
            size="compact"
            person={{
              fullName: m.fullName ?? '—',
              employeeCode: m.employeeCode,
              profilePictureUrl: m.profilePictureUrl,
            }}
            to={linkProfiles ? `/people/${m.id}` : undefined}
          />
        );
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.manages,
      title: 'Manages',
      width: 90,
      align: 'center',
      render: (_v, row) => {
        const n = row.employee?.directReportsCount ?? 0;
        if (n === 0) return dash;
        return (
          <Tooltip title="Direct reports">
            <Tag
              color="default"
              style={{ fontVariantNumeric: 'tabular-nums', margin: 0 }}
            >
              {n}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.status,
      title: 'Status',
      width: 130,
      render: (_v, row) => {
        const status = row.employee?.employmentStatus;
        if (!status) return null;
        return (
          <StatusPill tone={EMPLOYMENT_STATUS_TONE[status]} size="sm">
            {EMPLOYMENT_STATUS_LABEL[status]}
          </StatusPill>
        );
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.employmentType,
      title: 'Type',
      width: 120,
      render: (_v, row) => {
        const t = row.employee?.employmentType;
        return t ? <Text>{EMPLOYMENT_TYPE_LABEL[t]}</Text> : dash;
      },
    },
    {
      key: DIRECTORY_COLUMN_KEYS.joiningDate,
      title: 'Joined',
      width: 130,
      sorter: true,
      sortOrder: orderFor(DIRECTORY_COLUMN_KEYS.joiningDate),
      render: (_v, row) =>
        row.employee?.joiningDate ? (
          <Text style={{ fontSize: 12 }}>
            {formatDateShort(row.employee.joiningDate)}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.confirmationDate,
      title: 'Confirmed',
      width: 130,
      render: (_v, row) =>
        row.employee?.confirmationDate ? (
          <Text style={{ fontSize: 12 }}>
            {formatDateShort(row.employee.confirmationDate)}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.city,
      title: 'City',
      width: 140,
      render: (_v, row) =>
        row.resolved?.city?.name ? (
          <Text>{row.resolved.city.name}</Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.workEmail,
      title: 'Work email',
      width: 220,
      render: (_v, row) =>
        row.employee?.workEmail ? (
          <Text style={{ fontSize: 12 }} copyable={{ tooltips: false }}>
            {row.employee.workEmail}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.phone,
      title: 'Phone',
      width: 150,
      render: (_v, row) =>
        row.employee?.phone ? (
          <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            {row.employee.phone}
          </Text>
        ) : (
          dash
        ),
    },
    {
      key: DIRECTORY_COLUMN_KEYS.hasEss,
      title: 'ESS',
      width: 90,
      align: 'center',
      render: (_v, row) =>
        row.employee?.hasEss ? (
          <Tag color="cyan" style={{ margin: 0 }}>
            Yes
          </Tag>
        ) : (
          <Tag style={{ margin: 0 }}>No</Tag>
        ),
    },
  ];

  return sortable
    ? columns
    : columns.map((c) => ({ ...c, sorter: undefined, sortOrder: undefined }));
}
