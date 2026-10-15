import { Can } from '@/auth';
import { PageHeader } from '@/components';
import { createFileRoute } from '@tanstack/react-router';
import { Button, Space, Tooltip } from 'antd';
import { Plus, RefreshCcw } from 'lucide-react';

export const Route = createFileRoute('/_app/payroll/employee-benefits')({
  component: EmployeeBenefitsPage,
})

function EmployeeBenefitsPage() {
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <PageHeader
            eyebrow="Money"
            title="Employee Benefits"
            description="Monthly Employee benefits like HRA, medical, transport, and any custom allowances or deductions. These are separate from the basic salary and can be revised more frequently."
            actions={
              <Space>
                <Tooltip title="Refresh">
                  <Button
                    icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                    // onClick={() => listQuery.refetch()}
                    // loading={listQuery.isFetching}
                    aria-label="Refresh"
                  />
                </Tooltip>
                <Can I="write" a="payroll">
                  <Button
                    type="primary"
                    icon={<Plus size={14} strokeWidth={1.75} />}
                    // onClick={() => setOpenCreate(true)}
                  >
                    Record payment
                  </Button>
                </Can>
              </Space>
            }
          />
      </div>
}
