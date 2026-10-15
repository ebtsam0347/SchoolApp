// 25 / 50 / 75 / 100 % picker for a bonus run's share of salary.
// Form-control shaped (value / onChange) so it drops into a Form.Item.

import { Segmented } from 'antd';
import { BONUS_PERCENTAGE_OPTIONS, type BonusPercentage } from '@/payroll';

export type BonusPercentagePickerProps = {
  value?: BonusPercentage;
  onChange?: (value: BonusPercentage) => void;
  disabled?: boolean;
};

export function BonusPercentagePicker({
  value,
  onChange,
  disabled,
}: BonusPercentagePickerProps) {
  return (
    <Segmented<BonusPercentage>
      block
      value={value}
      onChange={onChange}
      disabled={disabled}
      options={BONUS_PERCENTAGE_OPTIONS.map((p) => ({
        label: `${p}%`,
        value: p,
      }))}
    />
  );
}
