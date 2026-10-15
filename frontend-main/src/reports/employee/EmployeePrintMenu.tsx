// "Print" dropdown on the employee detail strip. Order: profile, ID card,
// letters (appointment, confirmation, service / experience, relieving),
// then certificates (character, salary). An item only appears when its
// catalog id is present for the caller — plus a status condition where it
// only makes sense for active or terminated employees; the whole menu
// disappears when none are.
//
// PDFs open in the shared preview drawer. The appointment letter keeps its
// own override drawer; the service / experience letters download directly;
// the salary certificate asks for addressee + purpose first.

import { useState } from 'react';
import { App as AntdApp, Button, Dropdown, Spin, type MenuProps } from 'antd';
import {
  Award,
  BadgeDollarSign,
  ChevronDown,
  FileCheck,
  FileSignature,
  FileText,
  IdCard,
  LogOut,
  Printer,
} from 'lucide-react';

import { getErrorMessage } from '@/lib';
import type { EmployeeDto } from '@/people';

import type { ReportFile } from '../download';
import { PdfPreviewDrawer } from '../PdfPreviewDrawer';
import { AppointmentLetterDrawer } from './AppointmentLetterDrawer';
import { SalaryCertificateModal } from './SalaryCertificateModal';
import { reportProblemAlert, type PerEmployeeReport } from './ReportProblemAlert';
import {
  EMPLOYEE_REPORT_IDS,
  employeeReportsApi,
  useReportCatalogIds,
} from './api';

type DownloadKey = 'service' | 'experience';

type PreviewKey =
  | 'profile'
  | 'id-card'
  | 'confirmation'
  | 'relieving'
  | 'character'
  | 'salary';

type PreviewSpec = {
  title: string;
  icon: typeof FileText;
  width?: number;
  load: (employeeId: number, signal: AbortSignal) => Promise<ReportFile>;
  problems?: PerEmployeeReport;
};

const PREVIEWS: Record<PreviewKey, PreviewSpec> = {
  profile: {
    title: 'Employee Profile',
    icon: FileText,
    load: (id, signal) => employeeReportsApi.profile(id, signal),
  },
  // CR80 card-size page (85.6 × 54 mm) — a narrower drawer fits it.
  'id-card': {
    title: 'ID Card',
    icon: IdCard,
    width: 640,
    load: (id, signal) => employeeReportsApi.idCard(id, signal),
  },
  confirmation: {
    title: 'Confirmation Letter',
    icon: FileCheck,
    load: (id, signal) => employeeReportsApi.confirmationLetter(id, signal),
    problems: 'confirmationLetter',
  },
  relieving: {
    title: 'Relieving Letter',
    icon: LogOut,
    load: (id, signal) => employeeReportsApi.relievingLetter(id, signal),
    problems: 'relievingLetter',
  },
  character: {
    title: 'Character Certificate',
    icon: Award,
    load: (id, signal) => employeeReportsApi.characterCertificate(id, signal),
    problems: 'characterCertificate',
  },
  // Rendered by the modal first; the drawer only shows the result.
  salary: {
    title: 'Salary Certificate',
    icon: BadgeDollarSign,
    load: () => Promise.reject(new Error('Salary certificate is rendered by its modal.')),
    problems: 'salaryCertificate',
  },
};

export function EmployeePrintMenu({ employee }: { employee: EmployeeDto }) {
  const { message } = AntdApp.useApp();
  const allowed = useReportCatalogIds();
  const [pending, setPending] = useState<DownloadKey | null>(null);
  const [appointmentOpen, setAppointmentOpen] = useState(false);
  const [salaryOpen, setSalaryOpen] = useState(false);
  // The drawer stays mounted; `preview` is the last report shown (kept
  // through the close animation), `previewOpen` whether it's visible.
  const [preview, setPreview] = useState<PreviewKey>('profile');
  const [previewOpen, setPreviewOpen] = useState(false);
  const [salaryFile, setSalaryFile] = useState<ReportFile | null>(null);

  const id = employee.id;
  if (id == null) return null;
  const terminated = employee.employmentStatus === 'TERMINATED';

  async function download(key: DownloadKey) {
    if (id == null) return;
    setPending(key);
    try {
      if (key === 'service') await employeeReportsApi.downloadServiceLetter(id);
      else await employeeReportsApi.downloadExperienceLetter(id);
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setPending(null);
    }
  }

  const itemIcon = (key: DownloadKey | null, Icon: typeof FileText) =>
    key && pending === key ? <Spin size="small" /> : <Icon size={14} />;

  const items: NonNullable<MenuProps['items']> = [];
  const add = (
    show: boolean,
    key: string,
    label: string,
    Icon: typeof FileText,
    downloadKey: DownloadKey | null = null,
  ) => {
    if (!show) return;
    items.push({
      key,
      icon: itemIcon(downloadKey, Icon),
      label,
      disabled: downloadKey !== null && pending === downloadKey,
    });
  };

  add(allowed.has(EMPLOYEE_REPORT_IDS.profile), 'profile', 'Employee Profile', FileText);
  add(!terminated && allowed.has(EMPLOYEE_REPORT_IDS.idCard), 'id-card', 'ID Card', IdCard);
  add(
    allowed.has(EMPLOYEE_REPORT_IDS.appointmentLetter),
    'appointment',
    'Appointment Letter…',
    FileSignature,
  );
  add(
    !!employee.confirmationDate && allowed.has(EMPLOYEE_REPORT_IDS.confirmationLetter),
    'confirmation',
    'Confirmation Letter',
    FileCheck,
  );
  add(
    !terminated && allowed.has(EMPLOYEE_REPORT_IDS.serviceLetter),
    'service',
    'Service Letter',
    FileSignature,
    'service',
  );
  add(
    terminated && allowed.has(EMPLOYEE_REPORT_IDS.experienceLetter),
    'experience',
    'Experience Letter',
    FileSignature,
    'experience',
  );
  add(
    terminated && allowed.has(EMPLOYEE_REPORT_IDS.relievingLetter),
    'relieving',
    'Relieving Letter',
    LogOut,
  );
  add(
    allowed.has(EMPLOYEE_REPORT_IDS.characterCertificate),
    'character',
    'Character Certificate',
    Award,
  );
  add(
    !terminated && allowed.has(EMPLOYEE_REPORT_IDS.salaryCertificate),
    'salary',
    'Salary Certificate…',
    BadgeDollarSign,
  );
  if (items.length === 0) return null;

  const suffix = `${employee.fullName ?? 'Employee'}${
    employee.employeeCode ? ` (${employee.employeeCode})` : ''
  }`;

  const spec = PREVIEWS[preview];
  const SpecIcon = spec.icon;
  const openPreview = (key: PreviewKey) => {
    setPreview(key);
    setPreviewOpen(true);
  };

  return (
    <>
      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        menu={{
          items,
          onClick: ({ key }) => {
            if (key === 'appointment') setAppointmentOpen(true);
            else if (key === 'salary') setSalaryOpen(true);
            else if (key === 'service' || key === 'experience') void download(key);
            else openPreview(key as PreviewKey);
          },
        }}
      >
        <Button
          icon={<Printer size={16} strokeWidth={1.75} />}
          loading={pending !== null}
        >
          Print <ChevronDown size={14} strokeWidth={1.75} />
        </Button>
      </Dropdown>
      <AppointmentLetterDrawer
        open={appointmentOpen}
        employee={employee}
        onClose={() => setAppointmentOpen(false)}
      />
      <SalaryCertificateModal
        open={salaryOpen}
        employeeId={id}
        onClose={() => setSalaryOpen(false)}
        onRendered={(file) => {
          setSalaryOpen(false);
          setSalaryFile(file);
          openPreview('salary');
        }}
      />
      <PdfPreviewDrawer
        open={previewOpen}
        title={`${spec.title} — ${suffix}`}
        icon={<SpecIcon size={16} strokeWidth={1.75} />}
        width={spec.width}
        load={(signal) =>
          preview === 'salary' && salaryFile
            ? Promise.resolve(salaryFile)
            : spec.load(id, signal)
        }
        renderError={
          spec.problems
            ? (err, close) => reportProblemAlert(spec.problems!, err, id, close)
            : undefined
        }
        onClose={() => {
          setPreviewOpen(false);
          setSalaryFile(null);
        }}
      />
    </>
  );
}
