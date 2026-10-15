// "+" button that opens a dropdown of common create-actions. Items here
// are static for now; later they'll dispatch into the appropriate flow
// (apply leave drawer, add employee wizard, etc.).

import { Dropdown, type MenuProps } from 'antd';
import {
  ClipboardList,
  FilePlus2,
  Plane,
  Plus,
  Receipt,
  UserPlus,
  Wallet,
} from 'lucide-react';
import { TopbarIconButton } from './IconButton';

const QUICK_ACTIONS: MenuProps['items'] = [
  {
    key: 'qa-header',
    type: 'group',
    label: 'Create',
    children: [
      {
        key: 'apply-leave',
        label: 'Apply for leave',
        icon: <Plane size={14} strokeWidth={1.75} />,
      },
      {
        key: 'add-employee',
        label: 'Add employee',
        icon: <UserPlus size={14} strokeWidth={1.75} />,
      },
      {
        key: 'submit-timesheet',
        label: 'Submit timesheet',
        icon: <ClipboardList size={14} strokeWidth={1.75} />,
      },
      {
        key: 'add-expense',
        label: 'File an expense',
        icon: <Receipt size={14} strokeWidth={1.75} />,
      },
    ],
  },
  { type: 'divider' },
  {
    key: 'qa-admin',
    type: 'group',
    label: 'Admin',
    children: [
      {
        key: 'run-payroll',
        label: 'Run payroll',
        icon: <Wallet size={14} strokeWidth={1.75} />,
      },
      {
        key: 'new-offer',
        label: 'New offer letter',
        icon: <FilePlus2 size={14} strokeWidth={1.75} />,
      },
    ],
  },
];

export function QuickActions() {
  return (
    <Dropdown menu={{ items: QUICK_ACTIONS }} trigger={['click']} placement="bottomRight">
      <TopbarIconButton label="Quick actions" variant="outline" hideTooltip>
        <Plus size={18} strokeWidth={2} />
      </TopbarIconButton>
    </Dropdown>
  );
}
