// One Form.Item per ReportParameter, rendering the right AntD widget for
// the parameter's `type`. Date pickers emit Dayjs values — the drawer
// converts those to YYYY-MM-DD strings before submitting via
// `normalizeParameterValues`.

import { DatePicker, Form, Input, InputNumber, Segmented, Select } from 'antd';

import type { ReportParameter } from '../types';
import { humanizeEnumValue, humanizeParameterName } from './labels';
import { PARAM_PRESETS } from './presets';

export interface ParameterFieldProps {
  parameter: ReportParameter;
}

export function ParameterField({ parameter }: ParameterFieldProps) {
  const { name, type, required, values, help } = parameter;
  const preset = PARAM_PRESETS[name];
  const label = preset?.label ?? humanizeParameterName(name);
  const rules = required
    ? [{ required: true, message: `${label} is required` }]
    : undefined;
  const extra = help ?? undefined;

  switch (type) {
    case 'string':
      return (
        <Form.Item name={name} label={label} rules={rules} extra={extra}>
          <Input allowClear placeholder={`Enter ${label.toLowerCase()}`} />
        </Form.Item>
      );

    case 'int':
      return (
        <Form.Item name={name} label={label} rules={rules} extra={extra}>
          <InputNumber
            style={{ width: '100%' }}
            placeholder={`Enter ${label.toLowerCase()}`}
          />
        </Form.Item>
      );

    case 'date':
      return (
        <Form.Item name={name} label={label} rules={rules} extra={extra}>
          <DatePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
          />
        </Form.Item>
      );

    case 'enum': {
      const options = (values ?? []).map((v) => ({
        label: preset?.enumLabels?.[v] ?? humanizeEnumValue(v),
        value: v,
      }));
      // Up to three choices read better as a Segmented control. An
      // optional one without a preset default gets a leading "Any"
      // (empty → not sent).
      if (options.length > 0 && options.length <= 3) {
        const segmented = required || preset?.initial !== undefined
          ? options
          : [{ label: preset?.emptyLabel ?? 'Any', value: '' }, ...options];
        return (
          <Form.Item
            name={name}
            label={label}
            rules={rules}
            extra={extra}
            initialValue={preset?.initial ?? segmented[0]!.value}
          >
            <Segmented options={segmented} />
          </Form.Item>
        );
      }
      return (
        <Form.Item
          name={name}
          label={label}
          rules={rules}
          extra={extra}
          initialValue={preset?.initial}
        >
          <Select
            allowClear={!required}
            showSearch
            optionFilterProp="label"
            options={options}
            placeholder={preset?.emptyLabel ?? `Select ${label.toLowerCase()}`}
          />
        </Form.Item>
      );
    }
  }
}
