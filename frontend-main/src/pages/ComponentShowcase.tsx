import {
  Alert,
  Button,
  Card,
  Divider,
  Progress,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import {
  AlertTriangle,
  Briefcase,
  CheckCircle2,
  CheckCircle,
  Clock,
  CreditCard,
  FileSignature,
  Inbox,
  Plane,
  Receipt,
  Settings,
  Stamp,
  TrendingUp,
  UserPlus,
  Users,
  Wallet,
} from 'lucide-react';
import { useState } from 'react';

import {
  ApproveRejectActions,
  AvatarStack,
  BarSpark,
  AnomalyScatter,
  AttendanceHeatmap,
  ChartCard,
  ChartEmpty,
  HorizontalBars,
  SagaStatusGrid,
  DateRangeBadge,
  DrawerForm,
  EmptyState,
  FilterBar,
  JsonViewer,
  KbdHint,
  KpiGrid,
  KvList,
  LeaveCalendarStrip,
  LineSpark,
  MetricRing,
  NeedsYouList,
  MoneyCell,
  PageHeader,
  RejectReasonModal,
  RequestState,
  Section,
  SegmentLabel,
  StackedBars,
  StatCard,
  StatusPill,
  TimelineItem,
  TrendLine,
  dateRangeColumn,
  moneyColumn,
  personColumn,
  statusColumn,
} from '@/components';
import { Form, Input } from 'antd';

const { Title, Text, Paragraph } = Typography;
const { useToken } = antdTheme;

/**
 * Live showcase for foundation components. When a new component lands,
 * add a section here so we can audit it across all 5 themes in real
 * layout. Mounted at `/admin/_dev/components` in dev — not part of the
 * shipped nav, just a working harness.
 */
export function ComponentShowcase() {
  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Tuesday · April 28"
        title="Good evening, Saqib."
        description="Here's what's happening across the org today."
        actions={
          <>
            <Button>Export</Button>
            <Button type="primary">Run report</Button>
          </>
        }
      />

      <Section
        title="Headline KPI"
        subtitle="Hero card — one per page. Surface uses each theme's accent gradient, so the headline stat re-skins automatically across all 5 themes."
      >
        <KpiGrid minColumnWidth={300}>
          <StatCard
          variant="hero"
          label="Total compensation FY26"
          value={<MoneyCell amount={84_600_000} compact />}
          delta="+18% YoY · Rs 13.2M ahead of plan"
          icon={Wallet}
          trend="up"
        />
        <StatCard label="Headcount" value="1,248" delta="+12 this month" icon={Users} trend="up" />
          <StatCard label="Present today" value="1,089" delta="87.3%" icon={Clock} trend="up" />
          <StatCard label="On leave" value="42" delta="-3 vs yesterday" icon={Plane} trend="down" />
          </KpiGrid>
      </Section>

      <Section title="Headline KPIs" subtitle="Default plain variant — neutral utility cards.">
        <KpiGrid>
          <StatCard label="Headcount" value="1,248" delta="+12 this month" icon={Users} trend="up" />
          <StatCard label="Present today" value="1,089" delta="87.3%" icon={Clock} trend="up" />
          <StatCard label="On leave" value="42" delta="-3 vs yesterday" icon={Plane} trend="down" />
          <StatCard
            label="Payroll cycle"
            value={<MoneyCell amount={14_200_000} compact />}
            delta="Apr · 6 days left"
            icon={Wallet}
            trend="neutral"
          />
        </KpiGrid>
      </Section>

      <Section
        title="Accent border"
        subtitle="3px tone-tinted bar on any side — left / right / top / bottom. Premium without being loud."
      >
        <KpiGrid>
          <StatCard
            label="New hires (left)"
            value="28"
            delta="+5 vs last quarter"
            icon={Users}
            trend="up"
            tone="primary"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Approved leaves (top)"
            value="184"
            delta="92% turnaround < 24h"
            icon={CheckCircle2}
            trend="up"
            tone="success"
            variant="accent-border"
            accentSide="top"
          />
          <StatCard
            label="Pending approvals (right)"
            value="12"
            delta="3 overdue"
            icon={AlertTriangle}
            trend="down"
            tone="warning"
            variant="accent-border"
            accentSide="right"
          />
          <StatCard
            label="Failed payslips (bottom)"
            value="2"
            delta="Apr cycle"
            icon={Receipt}
            trend="down"
            tone="error"
            variant="accent-border"
            accentSide="bottom"
          />
        </KpiGrid>
      </Section>

      <Section
        title="Accent corner"
        subtitle="L-shaped bracket border that follows the card's rounded corner. Architectural mark for the headline number."
      >
        <KpiGrid>
          <StatCard
            label="Net payroll"
            value={<MoneyCell amount={14_200_000} compact />}
            delta="this month"
            icon={Wallet}
            tone="primary"
            variant="accent-corner"
            accentCorner="tl"
          />
          <StatCard
            label="Tax liability"
            value={<MoneyCell amount={3_100_000} compact />}
            delta="due May 7"
            icon={CreditCard}
            tone="warning"
            variant="accent-corner"
            accentCorner="tr"
          />
          <StatCard
            label="Avg tenure"
            value="3.4 yrs"
            delta="+0.2 YoY"
            icon={Briefcase}
            trend="up"
            tone="info"
            variant="accent-corner"
            accentCorner="bl"
          />
          <StatCard
            label="Engagement"
            value="78"
            delta="+4 vs Q1"
            icon={TrendingUp}
            trend="up"
            tone="success"
            variant="accent-corner"
            accentCorner="br"
          />
        </KpiGrid>
      </Section>

      <Section
        title="Accent radial"
        subtitle="Premium look — top tone line + soft contained circle on the corner side + icon-in-badge layout."
      >
        <KpiGrid>
          <StatCard
            label="Active headcount"
            value="4,980"
            delta="236 mgmt · 450 sup · 4,294 labor"
            icon={Users}
            tone="primary"
            variant="accent-radial"
            accentCorner="tr"
          />
          <StatCard
            label="Joined in last 30D"
            value="10"
            delta="56 net"
            icon={TrendingUp}
            trend="down"
            tone="success"
            variant="accent-radial"
            accentCorner="tr"
          />
          <StatCard
            label="Separated in last 30D"
            value="66"
            delta="No SLA breaches"
            icon={AlertTriangle}
            tone="error"
            variant="accent-radial"
            accentCorner="tr"
          />
          <StatCard
            label="Probation pipeline"
            value="0"
            delta="0 ending 7d"
            icon={Clock}
            tone="warning"
            variant="accent-radial"
            accentCorner="tr"
          />
        </KpiGrid>
      </Section>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
          gap: 16,
        }}
      >
        <Card title="Hiring funnel" extra={<Tag color="processing">Q2 2026</Tag>}>
          <FunnelStage label="Applied" count={342} pct={100} />
          <FunnelStage label="Screened" count={184} pct={54} />
          <FunnelStage label="Interviewed" count={67} pct={20} />
          <FunnelStage label="Offered" count={28} pct={8} />
          <FunnelStage label="Hired" count={12} pct={4} />
        </Card>

        <Card title="Approvals waiting on you">
          <Space orientation="vertical" size={12} style={{ width: '100%' }}>
            <ApprovalRow name="Aisha Khan" type="Leave" detail="3 days · May 5 – 7" />
            <ApprovalRow name="Marco Silva" type="Expense" detail="Rs 18,200 · client travel" />
            <ApprovalRow name="Priya Verma" type="Timesheet" detail="42h · week 17" />
            <Button type="primary" block>
              Open inbox (12)
            </Button>
          </Space>
        </Card>
      </div>

      <Section
        title="Avatars · date range"
        subtitle="AvatarStack with overflow chip; DateRangeBadge with auto-formatted range and duration."
      >
        <Card>
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Space size={16} wrap>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Reviewers
              </Text>
              <AvatarStack
                people={[
                  { name: 'Aisha Khan' },
                  { name: 'Marco Silva' },
                  { name: 'Priya Verma' },
                  { name: 'Liam Chen' },
                  { name: 'Noura Said' },
                  { name: 'Tomás Reyes' },
                  { name: 'Yuki Tanaka' },
                ]}
                max={4}
              />
            </Space>
            <Space size={12} wrap>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Period
              </Text>
              <DateRangeBadge start={new Date(2026, 3, 1)} end={new Date(2026, 3, 30)} />
              <DateRangeBadge
                start={new Date(2026, 4, 5)}
                end={new Date(2026, 4, 7)}
                format="medium"
              />
            </Space>
          </Space>
        </Card>
      </Section>

      <Section
        title="Audit timeline"
        subtitle="TimelineItem stacks vertically; the connector line draws automatically except on the last item."
      >
        <Card>
          <TimelineItem
            icon={UserPlus}
            tone="primary"
            title="Aisha Khan onboarded"
            timestamp={new Date(2026, 3, 28, 9, 15)}
            author={{ name: 'Saqib Mushtaq' }}
            description="Set up profile, assigned to Engineering / Platform team."
          />
          <TimelineItem
            icon={FileSignature}
            tone="success"
            title="Offer signed · Marco Silva"
            timestamp={new Date(2026, 3, 27, 17, 42)}
            author={{ name: 'HR Bot' }}
            description="Counter-offer accepted at Rs 28 lakh base + 15% variable."
          />
          <TimelineItem
            icon={Settings}
            tone="warning"
            title="Leave policy updated"
            timestamp={new Date(2026, 3, 26, 11, 0)}
            author={{ name: 'Priya Verma' }}
            description="Sandwich-leave clause clarified for PK entity. Effective May 1."
          />
          <TimelineItem
            icon={CheckCircle}
            tone="info"
            title="Q2 payroll preview generated"
            timestamp={new Date(2026, 3, 25, 8, 30)}
            description="Net Rs 14.2M across 1,248 employees · 0 anomalies flagged."
            isLast
          />
        </Card>
      </Section>

      <Section
        title="Empty state"
        subtitle="Centered illustration / icon, title, description, primary + secondary action."
      >
        <Card>
          <EmptyState
            icon={Inbox}
            title="Nothing in the inbox yet"
            description="Approvals, mentions, and assignments land here. Try connecting a channel or assigning yourself a task to see how it looks."
            action={<Button type="primary">Connect a channel</Button>}
            secondaryAction={<Button type="text">Take the tour</Button>}
          />
        </Card>
      </Section>

      <Section
        title="Keyboard hint"
        subtitle="Inline kbd chips for shortcut hints — composes with the topbar ⌘K trigger and any other shortcut affordance."
      >
        <Card>
          <Space orientation="vertical" size={8}>
            <Space size={8}>
              <Text>Open command palette</Text>
              <KbdHint keys={['⌘', 'K']} />
            </Space>
            <Space size={8}>
              <Text>Save draft</Text>
              <KbdHint keys={['⌘', 'S']} />
            </Space>
            <Space size={8}>
              <Text>Cycle workspaces</Text>
              <KbdHint keys={['⌘', 'Shift', 'P']} size="md" />
            </Space>
          </Space>
        </Card>
      </Section>

      <Section
        title="Wave 1 — new shared primitives"
        subtitle="RequestState, FilterBar, ApproveRejectActions, RejectReasonModal, KvList, SegmentLabel, JsonViewer, DrawerForm, and table column factories. Each replaces a pattern that was duplicated 4-8 times across routes."
      >
        <NewPrimitivesShowcase />
      </Section>

      <Card title="Theme audit zone">
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          Use this strip to compare typography, buttons, tags, dividers, and links across themes.
        </Paragraph>
        <Space wrap size={[8, 8]}>
          <Button type="primary">Primary</Button>
          <Button>Default</Button>
          <Button type="dashed">Dashed</Button>
          <Button type="text">Text</Button>
          <Button type="link">Link</Button>
        </Space>
        <Divider />
        <Space wrap size={[8, 8]}>
          <Tag color="success">Success</Tag>
          <Tag color="warning">Warning</Tag>
          <Tag color="error">Error</Tag>
          <Tag color="processing">Info</Tag>
          <Tag color="default">Default</Tag>
          <Tag variant="filled">Borderless</Tag>
        </Space>
        <Divider />
        <Space orientation="vertical" size={4}>
          <Title level={2} style={{ margin: 0 }}>
            Heading 2 — at scale
          </Title>
          <Title level={4} style={{ margin: 0 }}>
            Heading 4 — section
          </Title>
          <Text>Body text reads against the surface and should never strain at any scale.</Text>
          <Text type="secondary">Secondary copy carries less weight but stays legible.</Text>
          <Text type="success">Success copy.</Text>
          <Text type="warning">Warning copy.</Text>
          <Text type="danger">Danger copy.</Text>
          <a href="#">A link, themed.</a>
        </Space>
      </Card>
    </Space>
  );
}

function FunnelStage({ label, count, pct }: { label: string; count: number; pct: number }) {
  const { token } = useToken();
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <Text>{label}</Text>
        <Text type="secondary" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {count} · {pct}%
        </Text>
      </div>
      <Progress percent={pct} showInfo={false} strokeColor={token.colorPrimary} />
    </div>
  );
}

function ApprovalRow({ name, type, detail }: { name: string; type: string; detail: string }) {
  const { token } = useToken();
  const initials = name
    .split(' ')
    .map((n) => n[0])
    .join('');
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: token.colorPrimary,
          color: token.colorBgContainer,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 13,
          fontWeight: 600,
          flexShrink: 0,
        }}
        aria-hidden="true"
      >
        {initials}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <Text strong style={{ display: 'block', fontSize: 13 }}>
          {name}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {type} · {detail}
        </Text>
      </div>
      <Button size="small">Review</Button>
    </div>
  );
}

/* ----------------------------------------------------------------------
 * Wave 1 showcase — every new shared primitive in real layout so we can
 * audit them across all 5 themes from the dashboard.
 * -------------------------------------------------------------------- */

type ShowcaseRow = {
  id: number;
  employee: { fullName: string; employeeCode: string };
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  amount: number;
  fromDate: string;
  toDate: string;
  actions: string[];
};

const SHOWCASE_ROWS: ShowcaseRow[] = [
  {
    id: 1,
    employee: { fullName: 'Ayesha Khan', employeeCode: 'EMP-1042' },
    status: 'PENDING',
    amount: 24500,
    fromDate: '2026-05-04',
    toDate: '2026-05-06',
    actions: ['APPROVE', 'REJECT'],
  },
  {
    id: 2,
    employee: { fullName: 'Bilal Ahmed', employeeCode: 'EMP-0913' },
    status: 'APPROVED',
    amount: 18000,
    fromDate: '2026-04-21',
    toDate: '2026-04-21',
    actions: [],
  },
  {
    id: 3,
    employee: { fullName: 'Hira Siddiqui', employeeCode: 'EMP-2204' },
    status: 'REJECTED',
    amount: -3200,
    fromDate: '2026-04-12',
    toDate: '2026-04-15',
    actions: [],
  },
];

const SHOWCASE_TONE = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
} as const;

const SHOWCASE_LABEL = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
} as const;

function NewPrimitivesShowcase() {
  const [segment, setSegment] = useState<'LEAVES' | 'OVERTIME' | 'REGULARIZATION'>(
    'LEAVES',
  );
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED'>('ALL');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [demoState, setDemoState] = useState<'content' | 'loading' | 'error' | 'empty'>(
    'content',
  );

  const filteredRows = SHOWCASE_ROWS.filter((r) => {
    if (filter !== 'ALL' && r.status !== filter) return false;
    if (search && !r.employee.fullName.toLowerCase().includes(search.toLowerCase()))
      return false;
    return true;
  });

  const sampleJson = {
    eventId: 4831,
    occurredAt: '2026-04-30T11:22:08Z',
    actor: { userId: 17, username: 'saqi' },
    entity: { type: 'LEAVE_REQUEST', id: 1042 },
    action: 'APPROVE',
    sourceService: 'attendance',
    payload: { stage: 1, reason: null, decidedAt: '2026-04-30T11:22:08Z' },
  };

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <Card
        title="SegmentLabel + FilterBar"
        size="small"
        styles={{ body: { padding: 16 } }}
      >
        <Segmented
          value={segment}
          onChange={(v) => setSegment(v as typeof segment)}
          options={[
            {
              label: <SegmentLabel icon={Plane} text="Leaves" count={2} />,
              value: 'LEAVES',
            },
            {
              label: <SegmentLabel icon={Clock} text="Overtime" count={5} />,
              value: 'OVERTIME',
            },
            {
              label: (
                <SegmentLabel icon={Stamp} text="Regularization" tone="primary" count={1} />
              ),
              value: 'REGULARIZATION',
            },
          ]}
        />
        <Divider style={{ margin: '16px 0' }} />
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search employee…',
          }}
          filters={
            <Select
              value={filter}
              onChange={setFilter}
              style={{ width: 160 }}
              options={[
                { label: 'All statuses', value: 'ALL' },
                { label: 'Pending only', value: 'PENDING' },
                { label: 'Approved only', value: 'APPROVED' },
              ]}
            />
          }
          actions={<Button type="primary">New request</Button>}
          onReset={
            search || filter !== 'ALL'
              ? () => {
                  setSearch('');
                  setFilter('ALL');
                }
              : undefined
          }
        />
      </Card>

      <Card
        title="Table column factories — statusColumn, moneyColumn, dateRangeColumn, personColumn"
        size="small"
        styles={{ body: { padding: 16 } }}
      >
        <Table<ShowcaseRow>
          rowKey="id"
          size="middle"
          pagination={false}
          dataSource={filteredRows}
          columns={[
            personColumn<ShowcaseRow>({
              title: 'Employee',
              dataIndex: 'employee',
              to: (row) => `/people/${row.id}`,
            }),
            statusColumn<ShowcaseRow, ShowcaseRow['status']>({
              title: 'Status',
              dataIndex: 'status',
              toneMap: SHOWCASE_TONE,
              labelMap: SHOWCASE_LABEL,
              width: 130,
            }),
            dateRangeColumn<ShowcaseRow>({
              title: 'Range',
              from: 'fromDate',
              to: 'toDate',
              width: 200,
            }),
            moneyColumn<ShowcaseRow>({
              title: 'Amount',
              dataIndex: 'amount',
              coloredSign: true,
              showSign: true,
              width: 160,
            }),
            {
              title: '',
              key: 'actions',
              align: 'right',
              render: (_, row) => (
                <ApproveRejectActions
                  actions={row.actions}
                  onApprove={async () =>
                    new Promise((resolve) => setTimeout(resolve, 600))
                  }
                  onReject={async (_reason) =>
                    new Promise((resolve) => setTimeout(resolve, 400))
                  }
                />
              ),
            },
          ]}
        />
      </Card>

      <Card
        title="RequestState"
        size="small"
        extra={
          <Segmented
            value={demoState}
            onChange={(v) => setDemoState(v as typeof demoState)}
            options={[
              { label: 'Content', value: 'content' },
              { label: 'Loading', value: 'loading' },
              { label: 'Empty', value: 'empty' },
              { label: 'Error', value: 'error' },
            ]}
          />
        }
        styles={{ body: { padding: 16, minHeight: 180 } }}
      >
        <RequestState
          loading={demoState === 'loading'}
          empty={demoState === 'empty'}
          error={demoState === 'error' ? new Error('Mock failure for demo') : undefined}
          emptyTitle="No items match"
          emptyDescription="Adjust filters or come back later."
          emptyIcon={Inbox}
          errorTitle="Couldn't load demo data"
        >
          <Alert
            type="success"
            showIcon
            title="Live content rendered."
            description="When loading/empty/error, the wrapper short-circuits and this child never renders."
          />
        </RequestState>
      </Card>

      <Card
        title="KvList"
        size="small"
        styles={{ body: { padding: 16 } }}
      >
        <KvList
          items={[
            { label: 'Employee code', value: 'EMP-1042' },
            { label: 'Department', value: 'Engineering' },
            { label: 'Manager', value: 'Hira Siddiqui' },
            { label: 'Status', value: <StatusPill tone="success">Active</StatusPill> },
            { label: 'Joined', value: '14 Mar 2023', hint: '3 yrs 1 mo' },
            { label: 'Grade', value: 'L4' },
          ]}
        />
      </Card>

      <Space wrap size={16} style={{ width: '100%' }}>
        <Button onClick={() => setDrawerOpen(true)}>Open DrawerForm demo</Button>
        <Button danger onClick={() => setRejectOpen(true)}>
          Open RejectReasonModal demo
        </Button>
      </Space>

      <Card
        title="JsonViewer"
        size="small"
        styles={{ body: { padding: 16 } }}
      >
        <JsonViewer value={sampleJson} maxHeight={240} />
      </Card>

      <DashboardPrimitivesShowcase />

      <AttendanceHeatmapShowcase />

      <HorizontalBarsShowcase />

      <AnomalyScatterShowcase />

      <SagaStatusGridShowcase />

      <ChartsShowcase />

      <DrawerForm
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title="Demo drawer form"
        size={420}
        onFinish={(values) => {
          console.log('demo submit', values);
          setDrawerOpen(false);
        }}
      >
        <Form.Item label="Title" name="title" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item label="Notes" name="notes">
          <Input.TextArea rows={4} />
        </Form.Item>
      </DrawerForm>

      <RejectReasonModal
        open={rejectOpen}
        title="Reject this leave?"
        description="The whole request is rejected at your stage. The submitter is notified."
        onCancel={() => setRejectOpen(false)}
        onConfirm={async (reason) => {
          console.log('rejected with reason:', reason);
          setRejectOpen(false);
        }}
      />
    </Space>
  );
}

// — Dashboard primitives showcase ————————————————————————————————————————

const sampleApprovals = [
  {
    id: 'lv-1',
    title: 'Annual leave · Areeba Khan',
    description: '04 May → 08 May · Family wedding in Lahore',
    tone: 'primary' as const,
  },
  {
    id: 'ot-1',
    title: 'Overtime · Nadia Saleem',
    description: '28 Apr · 4h post-shift release support',
    tone: 'warning' as const,
  },
  {
    id: 'lv-2',
    title: 'Sick leave · Sana Akhtar',
    description: '29 Apr · Migraine',
    tone: 'error' as const,
  },
  {
    id: 'so-1',
    title: 'Shift override · Omar Tariq',
    description: '01 May · Late shift swap with Rabia',
    tone: 'info' as const,
  },
];

const sampleLeaveItems = [
  {
    id: 'cal-1',
    personId: 1,
    personName: 'Areeba Khan',
    start: '2026-05-04',
    end: '2026-05-08',
    label: 'Annual',
    tone: 'primary' as const,
  },
  {
    id: 'cal-2',
    personId: 2,
    personName: 'Hassan Iqbal',
    start: '2026-04-28',
    end: '2026-05-02',
    label: 'Sick',
    tone: 'error' as const,
  },
  {
    id: 'cal-3',
    personId: 3,
    personName: 'Sana Akhtar',
    start: '2026-05-18',
    end: '2026-05-22',
    label: 'Annual',
    tone: 'primary' as const,
  },
  {
    id: 'cal-4',
    personId: 4,
    personName: 'Bilal Ahmed',
    start: '2026-05-25',
    end: '2026-05-26',
    label: 'Casual',
    tone: 'info' as const,
  },
];

function DashboardPrimitivesShowcase() {
  return (
    <Section
      title="Dashboard primitives"
      subtitle="NeedsYouList for actionable rows; LeaveCalendarStrip for gantt-style range visualisations. Both wired through the same token system as the rest of the foundation layer."
    >
      <Space orientation="vertical" size={20} style={{ width: '100%' }}>
        <KpiGrid minColumnWidth={380} gap={16}>
          <Card title="NeedsYouList — with action slots" styles={{ body: { padding: 8 } }}>
            <NeedsYouList
              items={sampleApprovals.map((row) => ({
                id: row.id,
                title: row.title,
                description: row.description,
                tone: row.tone,
                meta: <StatusPill tone={row.tone === 'primary' ? 'info' : row.tone}>{row.title.split(' ·')[0]}</StatusPill>,
                actions: (
                  <Space size={4}>
                    <Button size="small" danger icon={<AlertTriangle size={12} />}>
                      Reject
                    </Button>
                    <Button size="small" type="primary" icon={<CheckCircle size={12} />}>
                      Approve
                    </Button>
                  </Space>
                ),
              }))}
              viewAllTo="/inbox"
              viewAllLabel="Open full inbox"
            />
          </Card>

          <Card title="NeedsYouList — link rows + empty state" styles={{ body: { padding: 8 } }}>
            <Space orientation="vertical" size={12} style={{ width: '100%' }}>
              <NeedsYouList
                items={[
                  {
                    id: 'doc-1',
                    title: 'Acknowledge updated leave policy',
                    description: 'Effective 01 May · 2 min read',
                    tone: 'info',
                    to: '/account',
                  },
                  {
                    id: 'punch-1',
                    title: 'Missing punch · Apr 27',
                    description: 'You haven\'t checked out for last Sunday\'s shift',
                    tone: 'warning',
                    to: '/time/today',
                  },
                ]}
                viewAllTo="/account"
              />
              <Divider style={{ margin: 0 }} />
              <NeedsYouList
                items={[]}
                emptyTitle="Inbox zero"
                emptyDescription="Nothing waiting on you."
                emptyIcon={Inbox}
              />
            </Space>
          </Card>
        </KpiGrid>

        <ChartCard
          title="LeaveCalendarStrip · 30-day window"
          subtitle="Rows = people, columns = days. Bars span the leave range; today is highlighted, weekends shaded."
          height={260}
        >
          <LeaveCalendarStrip
            from="2026-04-25"
            to="2026-05-26"
            items={sampleLeaveItems}
          />
        </ChartCard>
      </Space>
    </Section>
  );
}

// — Attendance heatmap showcase ————————————————————————————————————————

const heatmapDays = (() => {
  const today = new Date();
  today.setHours(12, 0, 0, 0);
  const year = today.getFullYear();
  const monthIdx = today.getMonth();
  const daysInMonth = new Date(year, monthIdx + 1, 0).getDate();
  const days: Array<{
    date: string;
    status: 'PRESENT' | 'LATE' | 'ABSENT' | 'ON_LEAVE' | 'OFF';
    varianceMin: number | null;
    checkInAt?: string;
  }> = [];
  for (let d = 1; d <= daysInMonth; d += 1) {
    const date = new Date(year, monthIdx, d, 12, 0, 0);
    if (d > today.getDate()) continue;
    const dow = date.getDay();
    const iso = `${year}-${String(monthIdx + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    if (dow === 0 || dow === 6) {
      days.push({ date: iso, status: 'OFF', varianceMin: null });
      continue;
    }
    const baseLate = Math.max(-10, Math.min(35, d - 12 + ((d * 5) % 6) - 2));
    if (d === 9) {
      days.push({ date: iso, status: 'ON_LEAVE', varianceMin: null });
      continue;
    }
    if (d === 17) {
      days.push({ date: iso, status: 'ABSENT', varianceMin: null });
      continue;
    }
    days.push({
      date: iso,
      status: baseLate >= 5 ? 'LATE' : 'PRESENT',
      varianceMin: baseLate,
    });
  }
  return days;
})();

const heatmapMonth = (() => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
})();

function AttendanceHeatmapShowcase() {
  return (
    <Section
      title="AttendanceHeatmap"
      subtitle="Calendar-month grid coloured by punch-in variance. Three scales: diverging (default — early/on-time/late), sequential (single-tone intensity), and status (categorical)."
    >
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard title="Diverging (default)" subtitle="Cool for early, warm for late, neutral on time." height={380}>
          <AttendanceHeatmap month={heatmapMonth} data={heatmapDays} scale="diverging" />
        </ChartCard>
        <ChartCard title="Sequential" subtitle="Single warning/error gradient — intensity = lateness." height={380}>
          <AttendanceHeatmap month={heatmapMonth} data={heatmapDays} scale="sequential" />
        </ChartCard>
        <ChartCard title="Status" subtitle="Categorical — present/late/absent/leave/holiday." height={380}>
          <AttendanceHeatmap month={heatmapMonth} data={heatmapDays} scale="status" />
        </ChartCard>
      </KpiGrid>
    </Section>
  );
}

// — HorizontalBars showcase ————————————————————————————————————————————

const sampleDeptLeave = [
  { id: 1, label: 'Engineering', value: 28, caption: '0.33 per head · 84 people', tone: 'warning' as const },
  { id: 2, label: 'Operations', value: 21, caption: '0.34 per head · 62 people', tone: 'warning' as const },
  { id: 3, label: 'Sales', value: 18, caption: '0.38 per head · 48 people', tone: 'primary' as const },
  { id: 4, label: 'Finance', value: 12, caption: '0.55 per head · 22 people', tone: 'primary' as const },
  { id: 5, label: 'HR', value: 9, caption: '0.64 per head · 14 people', tone: 'info' as const },
  { id: 6, label: 'Design', value: 7, caption: '0.39 per head · 18 people', tone: 'info' as const },
  { id: 7, label: 'Customer success', value: 5, caption: '0.18 per head · 28 people', tone: 'neutral' as const },
  { id: 8, label: 'Legal', value: 3, caption: '0.50 per head · 6 people', tone: 'neutral' as const },
];

function HorizontalBarsShowcase() {
  return (
    <Section
      title="HorizontalBars"
      subtitle="Sortable rankings with optional row tones, captions, and a built-in 'show more' toggle past topN."
    >
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard
          title="Leave usage by department"
          subtitle="Tone tiers — warning at top, primary mid, info / neutral trailing. Click 'show 2 more' to expand."
          height={420}
        >
          <HorizontalBars
            rows={sampleDeptLeave}
            valueFormatter={(v) => `${v} d`}
            topN={6}
          />
        </ChartCard>

        <ChartCard
          title="All same tone"
          subtitle="Single-tone, no row caption — the simplest form."
          height={420}
        >
          <HorizontalBars
            rows={sampleDeptLeave.map((r) => ({ id: r.id, label: r.label, value: r.value }))}
            tone="primary"
            valueFormatter={(v) => `${v} d`}
            topN={Infinity}
          />
        </ChartCard>
      </KpiGrid>
    </Section>
  );
}

// — AnomalyScatter showcase ————————————————————————————————————————————

const sampleScatter = Array.from({ length: 80 }, (_, i) => {
  let v = ((((i * 17) % 13) - 6) / 100) * 0.6;
  if (i === 5) v = 0.34;
  if (i === 12) v = -0.28;
  if (i === 33) v = 0.42;
  if (i === 56) v = -0.21;
  const baseline = 80_000 + ((i * 1973) % 90_000);
  return {
    id: i,
    label: `Employee ${i + 1}`,
    caption: `prior Rs ${(baseline / 1000).toFixed(0)}k → current Rs ${((baseline * (1 + v)) / 1000).toFixed(0)}k`,
    x: baseline,
    y: v,
  };
});

function AnomalyScatterShowcase() {
  return (
    <Section
      title="AnomalyScatter"
      subtitle="Variance visualisation — better than 'N anomalies' because outliers cluster visually and the threshold band makes 'how far' obvious."
    >
      <ChartCard
        title="Variance scatter · this run vs prior"
        subtitle="±15% threshold band. Hover any point for identity + values."
        height={420}
      >
        <AnomalyScatter
          data={sampleScatter}
          threshold={0.15}
          xLabel="Prior net pay (Rs)"
          yLabel="Variance vs prior"
          xTickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
          yTickFormatter={(v) => `${Math.round(v * 100)}%`}
          formatX={(v) => `Rs ${(v / 1000).toFixed(0)}k`}
          formatY={(v) => `${v > 0 ? '+' : ''}${(v * 100).toFixed(1)}%`}
        />
      </ChartCard>
    </Section>
  );
}

// — SagaStatusGrid showcase ————————————————————————————————————————————

const sampleSagas = [
  {
    id: 'salary.revise',
    name: 'Salary revision',
    status: 'OK' as const,
    failureRate24h: Array.from({ length: 24 }, () => 0),
  },
  {
    id: 'payslip.release',
    name: 'Payslip release',
    status: 'DEGRADED' as const,
    failureRate24h: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1, 0, 0, 0, 0],
  },
  {
    id: 'leave.approval',
    name: 'Leave approval',
    status: 'OK' as const,
    failureRate24h: Array.from({ length: 24 }, () => 0),
  },
  {
    id: 'offcycle.payment',
    name: 'Off-cycle payment',
    status: 'INCIDENT' as const,
    failureRate24h: [0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  },
  {
    id: 'attendance.regularize',
    name: 'Attendance regularization',
    status: 'OK' as const,
    failureRate24h: Array.from({ length: 24 }, () => 0),
  },
  {
    id: 'employee.onboard',
    name: 'Employee onboarding',
    status: 'OK' as const,
    failureRate24h: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0],
  },
];

function SagaStatusGridShowcase() {
  return (
    <Section
      title="SagaStatusGrid"
      subtitle="Operator view of distributed sagas. Tone-coded card per saga, status pill, and a 24h failure-rate sparkline. Cards reskin across themes via tokens."
    >
      <SagaStatusGrid rows={sampleSagas} />
    </Section>
  );
}

// — Charts showcase ————————————————————————————————————————————————————

const attendance30d = [
  { date: 'Apr 01', rate: 0.94 },
  { date: 'Apr 02', rate: 0.95 },
  { date: 'Apr 03', rate: 0.93 },
  { date: 'Apr 04', rate: 0.96 },
  { date: 'Apr 05', rate: 0.97 },
  { date: 'Apr 08', rate: 0.92 },
  { date: 'Apr 09', rate: 0.94 },
  { date: 'Apr 10', rate: 0.95 },
  { date: 'Apr 11', rate: 0.93 },
  { date: 'Apr 12', rate: 0.94 },
  { date: 'Apr 15', rate: 0.78, anomaly: true },
  { date: 'Apr 16', rate: 0.95 },
  { date: 'Apr 17', rate: 0.96 },
  { date: 'Apr 18', rate: 0.94 },
  { date: 'Apr 19', rate: 0.95 },
  { date: 'Apr 22', rate: 0.97 },
  { date: 'Apr 23', rate: 0.96 },
  { date: 'Apr 24', rate: 0.95 },
  { date: 'Apr 25', rate: 0.94 },
  { date: 'Apr 26', rate: 0.93 },
  { date: 'Apr 29', rate: 0.94 },
  { date: 'Apr 30', rate: 0.95 },
];

const team14d = [
  { day: 'Apr 17', present: 11, late: 0, absent: 1, leave: 0 },
  { day: 'Apr 18', present: 10, late: 1, absent: 1, leave: 0 },
  { day: 'Apr 19', present: 11, late: 1, absent: 0, leave: 0 },
  { day: 'Apr 22', present: 9, late: 0, absent: 1, leave: 2 },
  { day: 'Apr 23', present: 10, late: 1, absent: 0, leave: 1 },
  { day: 'Apr 24', present: 11, late: 0, absent: 1, leave: 0 },
  { day: 'Apr 25', present: 12, late: 0, absent: 0, leave: 0 },
  { day: 'Apr 26', present: 10, late: 1, absent: 0, leave: 1 },
  { day: 'Apr 29', present: 9, late: 2, absent: 1, leave: 0 },
  { day: 'Apr 30', present: 9, late: 1, absent: 1, leave: 1 },
];

const runs6m = [
  { period: 'Nov', gross: 41200, deductions: 5800, net: 35400 },
  { period: 'Dec', gross: 42100, deductions: 5900, net: 36200 },
  { period: 'Jan', gross: 43000, deductions: 6000, net: 37000 },
  { period: 'Feb', gross: 43800, deductions: 6100, net: 37700 },
  { period: 'Mar', gross: 47200, deductions: 6500, net: 40700 },
  { period: 'Apr', gross: 48200, deductions: 6300, net: 41900 },
];

const sparkData = {
  netPay12m: [138, 140, 139, 141, 142, 144, 145, 145, 147, 149, 150, 152],
  punctuality: [4, 8, 6, 5, 9, 6, 7, 5, 4, 8, 6, 5],
  approvals: [3, 5, 2, 4, 6, 3, 4, 5, 7, 4, 3, 6],
  failures: [0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0],
};

function ChartsShowcase() {
  return (
    <Section
      title="Charts — foundation primitives"
      subtitle="Recharts wrapped behind token-driven primitives. Audit across all 5 themes; series colors come from useToken(), translucent fills from color-mix."
    >
      <Space orientation="vertical" size={20} style={{ width: '100%' }}>
        {/* Headline charts */}
        <ChartCard
          title="Org attendance · last 22 days"
          subtitle="TrendLine with anomaly dots. The dip on Apr 15 trips isAnomaly() and renders a haloed marker."
        >
          <TrendLine
            data={attendance30d}
            xKey="date"
            series={[{ key: 'rate', label: 'Attendance', tone: 'primary' }]}
            isAnomaly={(row) => Boolean(row.anomaly)}
            yTickFormatter={(v) => `${Math.round(v * 100)}%`}
            valueFormatter={(v) => `${Math.round(v * 100)}%`}
          />
        </ChartCard>

        <ChartCard
          title="Team attendance · last 10 days"
          subtitle="StackedBars, four-series stacked. Tones map to present/late/absent/leave."
        >
          <StackedBars
            data={team14d}
            xKey="day"
            series={[
              { key: 'present', label: 'Present', tone: 'success' },
              { key: 'late', label: 'Late', tone: 'warning' },
              { key: 'absent', label: 'Absent', tone: 'error' },
              { key: 'leave', label: 'On leave', tone: 'info' },
            ]}
          />
        </ChartCard>

        <KpiGrid minColumnWidth={320}>
          <ChartCard
            title="Run-over-run · last 6 months"
            subtitle="Grouped bars (stacked={false})."
            height={260}
          >
            <StackedBars
              data={runs6m}
              xKey="period"
              stacked={false}
              series={[
                { key: 'gross', label: 'Gross', tone: 'primary' },
                { key: 'net', label: 'Net', tone: 'success' },
              ]}
              yTickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
            />
          </ChartCard>

          <ChartCard
            title="Empty state"
            subtitle="What charts look like with zero data — flat dotted baseline, no axis-only frame."
            height={260}
          >
            <ChartEmpty
              title="No anomalies this run"
              description="When the variance scatter is empty we celebrate, not show a blank chart."
            />
          </ChartCard>
        </KpiGrid>

        {/* Inline primitives — ring + sparklines */}
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <Title level={5} style={{ margin: 0 }}>
            Inline primitives
          </Title>
          <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
            MetricRing for single-value progress and multi-segment splits;
            LineSpark / BarSpark for in-card trend context.
          </Text>
        </Space>

        <KpiGrid minColumnWidth={220}>
          <Card styles={{ body: { padding: 16, display: 'flex', justifyContent: 'center' } }}>
            <MetricRing
              tone="primary"
              value={14}
              max={21}
              label="14"
              caption="Annual · remaining"
              size={140}
            />
          </Card>
          <Card styles={{ body: { padding: 16, display: 'flex', justifyContent: 'center' } }}>
            <MetricRing
              segments={[
                { value: 6, tone: 'primary', label: 'Used' },
                { value: 1, tone: 'warning', label: 'Pending' },
                { value: 14, tone: 'success', label: 'Remaining' },
              ]}
              label="21"
              caption="Annual · 21 d"
              size={140}
            />
          </Card>
          <Card styles={{ body: { padding: 16, display: 'flex', justifyContent: 'center' } }}>
            <MetricRing
              tone="warning"
              value={9}
              max={10}
              label="90%"
              caption="Sick · used"
              size={140}
            />
          </Card>
          <Card styles={{ body: { padding: 16, display: 'flex', justifyContent: 'center' } }}>
            <MetricRing
              tone="success"
              value={0}
              max={5}
              label="0"
              caption="Bereavement"
              size={140}
            />
          </Card>
        </KpiGrid>

        <KpiGrid minColumnWidth={240}>
          <StatCard
            label="Net pay · 12 mo"
            value={<MoneyCell amount={152000} compact />}
            delta={
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <LineSpark data={sparkData.netPay12m} tone="success" area />
                +10.1% YoY
              </span>
            }
            tone="success"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Punctuality · 12 wk"
            value="6.2 min"
            delta={
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <LineSpark data={sparkData.punctuality} tone="primary" />
                avg variance vs shift start
              </span>
            }
            tone="primary"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Approvals velocity · 12 wk"
            value="3 d 14 h"
            delta={
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <BarSpark data={sparkData.approvals} tone="warning" />
                avg time to decide
              </span>
            }
            tone="warning"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Saga failures · 12 h"
            value="1"
            delta={
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <BarSpark data={sparkData.failures} tone="error" />
                last 12 hourly buckets
              </span>
            }
            tone="error"
            variant="accent-border"
            accentSide="left"
          />
        </KpiGrid>
      </Space>
    </Section>
  );
}
