// One panel for the employee filter every people list / bulk report
// starts with: status, department, section, designation, card range and
// explicit card numbers. A report gets the panel when it declares
// `status` + `departmentId` (see `hasEmployeeFilter` in employee-filter.ts), so a future list
// report picks it up without any change here; each control only renders
// when the report declares that parameter.
//
// Fields use the catalog parameter names directly, so the values flow
// through `buildReportRequest` untouched — except `cards`, a tag list
// that `encodeEmployeeFilter` comma-joins.

import { Form, Input, Segmented, Select, Space } from 'antd';

import type { ReportCatalogEntry } from '../types';
import type { EmployeeFilterParam } from './employee-filter';
import { LookupSelect } from './LookupSelect';

const STATUS_OPTIONS = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Left', value: 'INACTIVE' },
  { label: 'All', value: 'ALL' },
];

export function EmployeeFilterPanel({ entry }: { entry: ReportCatalogEntry }) {
  const declared = new Set(entry.parameters.map((p) => p.name));
  const has = (n: EmployeeFilterParam) => declared.has(n);

  return (
    <>
      {has('status') ? (
        <Form.Item name="status" label="Employees" initialValue="ACTIVE">
          <Segmented options={STATUS_OPTIONS} />
        </Form.Item>
      ) : null}
      {has('departmentId') ? (
        <Form.Item name="departmentId" label="Department">
          <LookupSelect kind="department" />
        </Form.Item>
      ) : null}
      {has('sectionId') ? (
        <Form.Item name="sectionId" label="Section">
          <LookupSelect kind="section" />
        </Form.Item>
      ) : null}
      {has('designationId') ? (
        <Form.Item name="designationId" label="Designation">
          <LookupSelect kind="designation" />
        </Form.Item>
      ) : null}
      {has('cardFrom') || has('cardTo') ? (
        <Form.Item label="Card range">
          <Space.Compact style={{ width: '100%' }}>
            {has('cardFrom') ? (
              <Form.Item name="cardFrom" noStyle>
                <Input allowClear placeholder="From" />
              </Form.Item>
            ) : null}
            <Input
              disabled
              value="—"
              style={{ width: 40, textAlign: 'center', pointerEvents: 'none' }}
            />
            {has('cardTo') ? (
              <Form.Item name="cardTo" noStyle>
                <Input allowClear placeholder="To" />
              </Form.Item>
            ) : null}
          </Space.Compact>
        </Form.Item>
      ) : null}
      {has('cards') ? (
        <Form.Item name="cards" label="Card numbers">
          <SelectTags />
        </Form.Item>
      ) : null}
    </>
  );
}

/** Free-text tag input: type a card no and press Enter (or comma / space). */
function SelectTags(props: {
  id?: string;
  value?: string[];
  onChange?: (v: string[]) => void;
}) {
  return (
    <Select
      id={props.id}
      mode="tags"
      open={false}
      suffixIcon={null}
      tokenSeparators={[',', ' ']}
      placeholder="Type a card no and press Enter"
      value={props.value}
      onChange={props.onChange}
    />
  );
}
