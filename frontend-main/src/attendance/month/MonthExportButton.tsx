// "Excel" button for any employee month calendar — downloads the month the
// calendar is showing via `downloadMonthXlsx`. Disabled until the month has
// loaded; the data is whatever the calendar already fetched.

import { useState } from 'react';
import { App as AntdApp, Button } from 'antd';
import { Download } from 'lucide-react';

import type { AttendanceMonthResponse } from '@/attendance';
import { downloadMonthXlsx } from './export-month';

export type MonthExportButtonProps = {
  employee: { fullName: string; employeeCode?: string | null };
  /** YYYY-MM */
  month: string;
  data: AttendanceMonthResponse | undefined;
  payableDays: number | null;
};

export function MonthExportButton({ employee, month, data, payableDays }: MonthExportButtonProps) {
  const [exporting, setExporting] = useState(false);
  const { message } = AntdApp.useApp();

  const handleDownload = async () => {
    if (!data) return;
    setExporting(true);
    try {
      await downloadMonthXlsx(employee, month, data, payableDays);
    } catch {
      message.error("Couldn't generate the Excel file");
    } finally {
      setExporting(false);
    }
  };

  return (
    <Button
      icon={<Download size={14} />}
      onClick={handleDownload}
      loading={exporting}
      disabled={!data}
      size="small"
    >
      Excel
    </Button>
  );
}
