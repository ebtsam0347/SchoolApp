// Identity header for the employee picked on the Set allowances / History
// tabs: photo, then name, designation · card no, and department › section
// stacked beside it, like a contact card.

import { Typography, theme as antdTheme } from 'antd';

import { OrgPath, PersonCell } from '@/components';

import type { AllowanceEmployee } from './allowances-types';

const { Text } = Typography;
const { useToken } = antdTheme;

export type AllowanceEmployeeCardProps = {
  employee: AllowanceEmployee;
};

export function AllowanceEmployeeCard({ employee }: AllowanceEmployeeCardProps) {
  const { token } = useToken();
  const role = [employee.designationName, employee.employeeCode && `Card no ${employee.employeeCode}`]
    .filter(Boolean)
    .join(' · ');
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: 16,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        background: token.colorFillQuaternary,
        minWidth: 0,
      }}
    >
      <PersonCell
        avatarOnly
        size="large"
        person={{ fullName: employee.fullName, profilePictureUrl: employee.profilePictureUrl }}
      />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <Text strong style={{ fontSize: 18, lineHeight: 1.3 }}>
          {employee.fullName}
        </Text>
        {role && <Text type="secondary">{role}</Text>}
        <OrgPath
          segments={[
            employee.departmentName ? { label: employee.departmentName } : null,
            employee.sectionName ? { label: employee.sectionName } : null,
          ]}
        />
      </div>
    </div>
  );
}
