// /people/leaving — Employee Leaving (resignation / termination) + Full &
// Final Settlement calculator.
//
// DESIGN-ONLY PREVIEW: nothing on this page hits a real API. There is no
// backend for a full-and-final-settlement calculation yet, and the exit
// action (resign/terminate) isn't wired here either — this page exists so
// HR can see and click through the intended design before backend work
// starts. Employee list + every settlement figure below come from
// `mock-data.ts` (deterministic per employee, so the same card no always
// shows the same numbers). "Save Record" only shows a local success
// message; it does not call `employeesApi.resign` / `.terminate`.
//
// When the backend is ready: swap MOCK_EMPLOYEES for a real employee
// picker + `peopleBff.detail`, swap `mockSettlementFor` for the real
// salary/gratuity/advances composites, and wire the submit handler to
// `employeesApi.resign` / `.terminate` (see src/people/action-modals.tsx
// for that exact call shape).

import { useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import {
  App as AntdApp,
  Alert,
  Avatar,
  Button,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { UserMinus, UserRound } from 'lucide-react';

import { PageHeader, Section } from '@/components';
import { MOCK_EMPLOYEES, mockSettlementFor } from '@/people/leaving-mock';

export const Route = createFileRoute('/_app/people/leaving')({
  component: EmployeeLeavingPage,
});

const { Text } = Typography;

type LeavingType = 'RESIGNATION' | 'TERMINATION';
type NotesPay = 'NOTHING' | 'ADD' | 'SUBTRACT';
type BonusMultiplier = 0 | 1 | 1.5 | 2;

type FormValues = {
  // Leaving Description
  leavingType: LeavingType;
  lastWorkingDay?: dayjs.Dayjs;
  reason: string;
  notes?: string;

  // Advances
  pAdvAmount?: number;
  pAdvPayBack?: number;
  tempAdvAmount?: number;
  rfdAdvAmount?: number;

  // Notes pay
  notesPay?: NotesPay;
  notesPayPercentage?: number;

  // Allowance rate/paid pairs
  specialAllowanceRate?: number;
  specialAllowancePaid?: number;
  mealAllowanceRate?: number;
  mealAllowancePaid?: number;
  travelAllowanceRate?: number;
  travelAllowancePaid?: number;
  accodAllowanceRate?: number;
  accodAllowancePaid?: number;
  educationAllowanceRate?: number;
  educationAllowancePaid?: number;
  medicalAllowanceRate?: number;
  medicalAllowancePaid?: number;
  perDayTravelRate?: number;
  perDayTravelPaid?: number;
  perDayMealRate?: number;
  perDayMealPaid?: number;
  extraAllowanceRate?: number;
  extraAllowancePaid?: number;

  // Overtime
  otHours?: number;
  otPayment?: number;

  // FairShop
  fairShopDeduction?: number;

  // Current month attendance & leave record
  fullDays?: number;
  halfDays?: number;
  sundays?: number;
  holidays?: number;
  casual?: number;
  medical?: number;
  special?: number;
  m2?: number;
  totalAbsenties?: number;
  paidLeaves?: number;
  canteenDedDue?: number;
  unionFundDeduction?: number;
  rentDeduction?: number;

  // Bonus calculation
  bonusMultiplier?: BonusMultiplier;
  bonusAmount?: number;
  prevBonus?: number;
  excessiveBonusDed?: number;

  // Excessive leaves record
  dueLeaves?: number;
  availLeaves?: number;
  excessiveLeaves?: number;
  excessiveAnnualLeaves?: number;
  excessiveLeavesAmt?: number;

  // Annual leaves record
  annualLeaves?: boolean;
  annualLeavesAmount?: number;

  // Final calculations
  previousSalDue?: number;
};

const num = (v: number | undefined | null) => v ?? 0;

function EmployeeLeavingPage() {
  const [form] = Form.useForm<FormValues>();
  const { message, modal } = AntdApp.useApp();
  const [cardNo, setCardNo] = useState<string | undefined>();
  const leavingType = Form.useWatch('leavingType', form);
  const watched = (Form.useWatch([], form) as FormValues | undefined) ?? ({} as FormValues);

  const employee = MOCK_EMPLOYEES.find((e) => e.cardNo === cardNo);
  const settlement = useMemo(() => (cardNo ? mockSettlementFor(cardNo) : null), [cardNo]);

  const payableDays = num(watched.fullDays) + num(watched.paidLeaves);
  const basicSalary = settlement?.basicSalary ?? 0;
  const salaryDue = basicSalary > 0 ? (basicSalary / 26) * payableDays : 0;
  const pAdvRemaining = num(watched.pAdvAmount) - num(watched.pAdvPayBack);

  const allowancesPaidTotal =
    num(watched.specialAllowancePaid) +
    num(watched.mealAllowancePaid) +
    num(watched.travelAllowancePaid) +
    num(watched.accodAllowancePaid) +
    num(watched.educationAllowancePaid) +
    num(watched.medicalAllowancePaid) +
    num(watched.perDayTravelPaid) +
    num(watched.perDayMealPaid) +
    num(watched.extraAllowancePaid);

  const grossSalary = basicSalary + allowancesPaidTotal;

  const totalAdds =
    salaryDue +
    num(settlement?.gratuityAmount) +
    num(watched.otPayment) +
    num(watched.bonusAmount) +
    num(watched.prevBonus) +
    (watched.annualLeaves ? num(watched.annualLeavesAmount) : 0) +
    allowancesPaidTotal;

  const totalSubtracts =
    num(watched.pAdvPayBack) +
    num(watched.tempAdvAmount) +
    num(watched.rfdAdvAmount) +
    num(watched.fairShopDeduction) +
    num(watched.canteenDedDue) +
    num(watched.unionFundDeduction) +
    num(watched.rentDeduction) +
    num(watched.excessiveBonusDed) +
    num(watched.excessiveLeavesAmt);

  const notesPayAmount =
    grossSalary * (num(watched.notesPayPercentage) / 100);
  const notesPayAdd = watched.notesPay === 'ADD' ? notesPayAmount : 0;
  const notesPaySubtract = watched.notesPay === 'SUBTRACT' ? notesPayAmount : 0;
  const adjustedTotalAdds = totalAdds + notesPayAdd;
  const adjustedTotalSubtracts = totalSubtracts + notesPaySubtract;
  const netPayable = adjustedTotalAdds - adjustedTotalSubtracts + num(watched.previousSalDue);

  const selectEmployee = (nextCardNo: string | undefined) => {
    setCardNo(nextCardNo);
    form.resetFields();
    if (!nextCardNo) return;
    const s = mockSettlementFor(nextCardNo);
    form.setFieldsValue({
      leavingType: 'RESIGNATION',
      notesPay: 'NOTHING',
      notesPayPercentage: 100,
      bonusMultiplier: s.bonusMultiplier,
      pAdvAmount: s.pAdvAmount,
      pAdvPayBack: s.pAdvPayBack,
      fullDays: s.fullDays,
      sundays: s.sundays,
      totalAbsenties: s.totalAbsenties,
      canteenDedDue: s.canteenDedDue,
      bonusAmount: s.bonusAmount,
      dueLeaves: s.dueLeaves,
    });
  };

  const submit = (values: FormValues) => {
    const finish = () =>
      message.success(
        `[Design preview] ${values.leavingType === 'TERMINATION' ? 'Termination' : 'Resignation'} record would be saved here once the backend endpoint exists — nothing was actually submitted.`,
      );
    if (values.leavingType === 'TERMINATION') {
      modal.confirm({
        title: 'Terminate employment',
        icon: <UserMinus size={18} />,
        content:
          'This is a design preview — no real termination will happen. In the real flow this action is immediate and irreversible.',
        okText: 'Terminate immediately',
        okButtonProps: { danger: true },
        cancelText: 'Cancel',
        onOk: finish,
      });
      return;
    }
    finish();
  };

  const rateAndPaid = (rateName: keyof FormValues, paidName: keyof FormValues, label: string) => (
    <div style={{ display: 'flex', gap: 12 }}>
      <Form.Item label={`${label} Rate`} name={rateName} style={{ flex: 1 }}>
        <InputNumber min={0} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item label={`${label} Paid`} name={paidName} style={{ flex: 1 }}>
        <InputNumber min={0} style={{ width: '100%' }} />
      </Form.Item>
    </div>
  );

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="People · Design preview"
        title="Employee Leaving"
        description="Full & final settlement — design preview with dummy data. No API calls are made from this page yet."
      />

      <Alert
        type="info"
        showIcon
        title="Design preview only"
        description="Employees and every figure on this page are mock data for layout review — nothing here reads or writes real records."
      />

      <Section title="Employee">
        <Select
          value={cardNo}
          onChange={selectEmployee}
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Search by name or card no (mock list)"
          style={{ width: '100%', maxWidth: 420 }}
          options={MOCK_EMPLOYEES.map((e) => ({
            value: e.cardNo,
            label: `${e.cardNo} · ${e.name}`,
          }))}
        />
      </Section>

      {employee && settlement ? (
        <Form<FormValues> form={form} layout="vertical" onFinish={submit}>
          <Section title="Snapshot">
            <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap', alignItems: 'flex-start' }}>
              <Avatar size={72} icon={<UserRound size={36} />} />
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
                  gap: 12,
                  flex: 1,
                }}
              >
                <div>
                  <Text type="secondary">Name</Text>
                  <div style={{ fontWeight: 500 }}>{employee.name}</div>
                </div>
                <div>
                  <Text type="secondary">Father Name</Text>
                  <div style={{ fontWeight: 500 }}>{employee.fatherName}</div>
                </div>
                <div>
                  <Text type="secondary">CNIC No.</Text>
                  <div style={{ fontWeight: 500 }}>{employee.cnic}</div>
                </div>
                <div>
                  <Text type="secondary">Department</Text>
                  <div style={{ fontWeight: 500 }}>{employee.department}</div>
                </div>
                <div>
                  <Text type="secondary">Section</Text>
                  <div style={{ fontWeight: 500 }}>{employee.section}</div>
                </div>
                <div>
                  <Text type="secondary">Designation</Text>
                  <div style={{ fontWeight: 500 }}>{employee.designation}</div>
                </div>
                <div>
                  <Text type="secondary">Job Type</Text>
                  <div style={{ fontWeight: 500 }}>{employee.jobType}</div>
                </div>
                <div>
                  <Text type="secondary">Grade</Text>
                  <div style={{ fontWeight: 500 }}>{employee.grade}</div>
                </div>
              </div>
            </div>
          </Section>

          <Section title="Salary & Dates">
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item label="Basic Salary" style={{ flex: '1 1 180px' }}>
                <Input value={basicSalary.toLocaleString('en-PK')} disabled />
              </Form.Item>
              <Form.Item label="Gross Salary" style={{ flex: '1 1 180px' }}>
                <Input value={grossSalary.toLocaleString('en-PK')} disabled />
              </Form.Item>
              <Form.Item label="Date of Joining" style={{ flex: '1 1 180px' }}>
                <Input value={employee.joiningDate} disabled />
              </Form.Item>
              <Form.Item label="Date of Permanent" style={{ flex: '1 1 180px' }}>
                <Input value={employee.confirmationDate} disabled />
              </Form.Item>
              <Form.Item label="Gratuity Date" style={{ flex: '1 1 180px' }}>
                <Input value={employee.joiningDate} disabled />
              </Form.Item>
            </div>
          </Section>

          <Section title="Advances">
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item label="P Adv. Amount" name="pAdvAmount" style={{ flex: '1 1 160px' }}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="P Adv. PayBack" name="pAdvPayBack" style={{ flex: '1 1 160px' }}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="P Adv. Remaining" style={{ flex: '1 1 160px' }}>
                <Input value={pAdvRemaining.toLocaleString('en-PK')} disabled />
              </Form.Item>
              <Form.Item label="Temp. Adv. Amount" name="tempAdvAmount" style={{ flex: '1 1 160px' }}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="RFD Adv. Amount" name="rfdAdvAmount" style={{ flex: '1 1 160px' }}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </div>
          </Section>

          <Section title="Notes Pay (%)">
            <Form.Item name="notesPay" style={{ marginBottom: 0 }}>
              <Radio.Group
                options={[
                  { value: 'NOTHING', label: 'Nothing' },
                  { value: 'ADD', label: 'Add' },
                  { value: 'SUBTRACT', label: 'Subtract' },
                ]}
              />
            </Form.Item>
            {watched.notesPay !== 'NOTHING' ? (
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 12 }}>
                <Form.Item label="Percentage" name="notesPayPercentage" style={{ flex: '1 1 180px' }}>
                  <Select
                    options={[25, 50, 75, 100].map((value) => ({
                      value,
                      label: `${value}%`,
                    }))}
                  />
                </Form.Item>
                <Form.Item label="Amount" style={{ flex: '1 1 180px' }}>
                  <Input value={notesPayAmount.toLocaleString('en-PK', { maximumFractionDigits: 2 })} disabled />
                </Form.Item>
              </div>
            ) : null}
          </Section>

          <Section title="Allowance Rates and Payable Allowances">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: 16,
              }}
            >
              {rateAndPaid('specialAllowanceRate', 'specialAllowancePaid', 'Special Allowance')}
              {rateAndPaid('mealAllowanceRate', 'mealAllowancePaid', 'Meal Allowance')}
              {rateAndPaid('travelAllowanceRate', 'travelAllowancePaid', 'Travel Allowance')}
              {rateAndPaid('accodAllowanceRate', 'accodAllowancePaid', 'Accod. Allowance')}
              {rateAndPaid('educationAllowanceRate', 'educationAllowancePaid', 'Education Allowance')}
              {rateAndPaid('medicalAllowanceRate', 'medicalAllowancePaid', 'Medical Allowance')}
              {rateAndPaid('perDayTravelRate', 'perDayTravelPaid', 'Per Day Travel')}
              {rateAndPaid('perDayMealRate', 'perDayMealPaid', 'Per Day Meal')}
              {rateAndPaid('extraAllowanceRate', 'extraAllowancePaid', 'Extra Allowance')}
            </div>
          </Section>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
              gap: 16,
            }}
          >
            <Section title="Current OverTime">
              <div style={{ display: 'flex', gap: 12 }}>
                <Form.Item label="OverTime Hours" name="otHours" style={{ flex: 1 }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="OverTime Payment" name="otPayment" style={{ flex: 1 }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </div>
            </Section>

            <Section title="Gratuity">
              <div style={{ display: 'flex', gap: 12 }}>
                <Form.Item label="No. of Gratuity" style={{ flex: 1 }}>
                  <Input value={settlement.gratuityYears} disabled />
                </Form.Item>
                <Form.Item label="Amount" style={{ flex: 1 }}>
                  <Input value={settlement.gratuityAmount.toLocaleString('en-PK')} disabled />
                </Form.Item>
              </div>
            </Section>

            <Section title="FairShop Deduction">
              <Form.Item label="Amount" name="fairShopDeduction">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Section>
          </div>

          <Section title="Current Month Attendance and Leave Record">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                gap: 12,
              }}
            >
              <Form.Item label="Full Days" name="fullDays"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Half Days" name="halfDays"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Sundays" name="sundays"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Holidays" name="holidays"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Casual" name="casual"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Medical" name="medical"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Special" name="special"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="M2" name="m2"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Total Absenties" name="totalAbsenties"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Paid Leaves" name="paidLeaves"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Payable Days"><Input value={payableDays} disabled /></Form.Item>
              <Form.Item label="Salary Due"><Input value={salaryDue.toLocaleString('en-PK', { maximumFractionDigits: 0 })} disabled /></Form.Item>
              <Form.Item label="Canteen Ded. Due" name="canteenDedDue"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Union Fund Deduction" name="unionFundDeduction"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item label="Rent Deduction" name="rentDeduction"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            </div>
          </Section>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
              gap: 16,
            }}
          >
            <Section title="Bonus Calculation">
              <Form.Item name="bonusMultiplier">
                <Radio.Group
                  options={[
                    { value: 0, label: '0' },
                    { value: 1, label: '1' },
                    { value: 1.5, label: '1.5' },
                    { value: 2, label: '2' },
                  ]}
                />
              </Form.Item>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Form.Item label="Amount" name="bonusAmount" style={{ flex: '1 1 140px' }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Prev. Bonus" name="prevBonus" style={{ flex: '1 1 140px' }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Excessive Bonus Ded." name="excessiveBonusDed" style={{ flex: '1 1 140px' }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </div>
            </Section>

            <Section title="Excessive Leaves Record">
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Form.Item label="Due Leaves" name="dueLeaves" style={{ flex: '1 1 130px' }}>
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Avail Leaves" name="availLeaves" style={{ flex: '1 1 130px' }}>
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Excessive Leaves" name="excessiveLeaves" style={{ flex: '1 1 130px' }}>
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Excessive Annual Leaves" name="excessiveAnnualLeaves" style={{ flex: '1 1 130px' }}>
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Excessive Leaves Amt." name="excessiveLeavesAmt" style={{ flex: '1 1 130px' }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </div>
            </Section>

            <Section title="Annual Leaves Record">
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                <Form.Item label="Anual Leaves Amount" name="annualLeavesAmount" style={{ flex: '1 1 180px' }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="annualLeaves" valuePropName="checked">
                  <Checkbox>Anual Leaves</Checkbox>
                </Form.Item>
              </div>
            </Section>
          </div>

          <Section title="Final Calculations">
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item label={notesPayAdd > 0 ? 'Total Adds Notes' : 'Total Adds'} style={{ flex: '1 1 200px' }}>
                <Input value={adjustedTotalAdds.toLocaleString('en-PK', { maximumFractionDigits: 2 })} disabled />
              </Form.Item>
              <Form.Item label={notesPaySubtract > 0 ? 'Total Subtracts Notes' : 'Total Subtracts'} style={{ flex: '1 1 200px' }}>
                <Input value={adjustedTotalSubtracts.toLocaleString('en-PK', { maximumFractionDigits: 2 })} disabled />
              </Form.Item>
              <Form.Item label="Previous Sal Due" name="previousSalDue" style={{ flex: '1 1 200px' }}>
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="Net Payable" style={{ flex: '1 1 200px' }}>
                <Input
                  value={netPayable.toLocaleString('en-PK', { maximumFractionDigits: 0 })}
                  disabled
                  style={{ fontWeight: 600 }}
                />
              </Form.Item>
            </div>
          </Section>

          <Section title="Leaving Description">
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Form.Item
                label="Leaving Type"
                name="leavingType"
                rules={[{ required: true, message: 'Pick a leaving type' }]}
                style={{ flex: '1 1 200px' }}
              >
                <Select
                  options={[
                    { value: 'RESIGNATION', label: 'Resignation' },
                    { value: 'TERMINATION', label: 'Termination' },
                  ]}
                />
              </Form.Item>

              {leavingType === 'TERMINATION' ? null : (
                <Form.Item
                  label="Last Working Day"
                  name="lastWorkingDay"
                  rules={[{ required: true, message: 'Pick the last working day' }]}
                  style={{ flex: '1 1 200px' }}
                >
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              )}
            </div>

            <Form.Item
              label={leavingType === 'TERMINATION' ? 'Reason' : 'Reason (optional)'}
              name="reason"
              rules={
                leavingType === 'TERMINATION'
                  ? [{ required: true, message: 'Reason is required for termination' }]
                  : []
              }
            >
              <Input.TextArea rows={3} placeholder="Personal · Misconduct · Restructuring…" />
            </Form.Item>

            <Form.Item label="Notes (optional)" name="notes">
              <Input.TextArea rows={3} />
            </Form.Item>

            <Button type="primary" danger={leavingType === 'TERMINATION'} htmlType="submit">
              Save Record
            </Button>
          </Section>
        </Form>
      ) : null}
    </div>
  );
}
