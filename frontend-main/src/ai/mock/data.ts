// Mock fixtures for the Assistant (Preview) workspace. Hand-crafted
// to demo what the in-house AI features will look like once the
// ai-service (qwen2.5 + nomic-embed-text via Ollama, pgvector store)
// is wired through the BFF. Numbers, names and snippets are
// illustrative — clearly fictional in the aggregate.

export type ConversationId = string;
export type MessageId = string;

export interface Citation {
  sourceType: 'POLICY' | 'EMPLOYEE' | 'TIMELINE' | 'AUDIT';
  sourceId: string;
  title: string;
  snippet: string;
}

export interface Message {
  id: MessageId;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  body: string;
  createdAt: string;
  model?: string;
  promptTokens?: number;
  completionTokens?: number;
  citations?: Citation[];
}

export interface Conversation {
  id: ConversationId;
  userId: number;
  userName: string;
  title: string;
  updatedAt: string;
  archived: boolean;
  messages: Message[];
}

export const currentUser = { id: 1001, name: 'Hira K.' };

// ── Policy chunks (the "RAG" source-of-truth) ─────────────────────────

export interface PolicyChunk {
  sourceId: string;
  title: string;
  snippet: string;
}

export const policyChunks: PolicyChunk[] = [
  {
    sourceId: 'POL-LEAVE-2026-§3.2',
    title: 'Leave Policy 2026 — §3.2 Annual leave',
    snippet:
      'Confirmed employees accrue 18 working days of annual leave per calendar year, prorated by joining date. Up to 7 days may be carried forward; the remainder lapses on 31 December.',
  },
  {
    sourceId: 'POL-LEAVE-2026-§3.5',
    title: 'Leave Policy 2026 — §3.5 Casual leave',
    snippet:
      'Employees may take up to 10 casual leave days per calendar year. Casual leave does not carry forward and cannot be encashed at separation.',
  },
  {
    sourceId: 'POL-LEAVE-2026-§4.1',
    title: 'Leave Policy 2026 — §4.1 Sick leave',
    snippet:
      'Sick leave up to 10 days per year is fully paid. A medical certificate is required for any single absence longer than two consecutive working days.',
  },
  {
    sourceId: 'POL-PROBATION-2026-§2',
    title: 'Probation Policy — §2 Confirmation criteria',
    snippet:
      'New hires are on probation for the first 90 days. Confirmation requires a satisfactory review by the line manager and HR, completed no later than the 100th day from joining.',
  },
  {
    sourceId: 'POL-OT-2026-§5',
    title: 'Overtime Policy — §5 Eligibility',
    snippet:
      'Overtime is approved in advance by the line manager and is paid at 1.5× the basic hourly rate on weekdays and 2× on Sundays and gazetted holidays. Grade M3 and above are not eligible.',
  },
  {
    sourceId: 'POL-EXP-2026-§1.4',
    title: 'Travel & Expense Policy — §1.4 Per-diem',
    snippet:
      'Domestic per-diem is PKR 4,500 / day inclusive of meals and incidentals. Hotel reimbursement is capped at PKR 12,000 / night for grades up to M2 and PKR 18,000 / night for M3+.',
  },
  {
    sourceId: 'POL-CODE-2026-§7',
    title: 'Code of Conduct — §7 Disciplinary procedure',
    snippet:
      'Misconduct is graded Minor, Major or Gross. A show-cause notice precedes any formal action; the employee has 7 working days to respond before the disciplinary committee convenes.',
  },
];

// ── Suggested prompts ─────────────────────────────────────────────────

export const suggestedPrompts: { label: string; body: string }[] = [
  {
    label: 'Leave policy for confirmed staff',
    body: 'Summarise the leave policy for confirmed employees — annual, casual and sick.',
  },
  {
    label: 'Overtime eligibility',
    body: 'Who is eligible for overtime, and at what rates?',
  },
  {
    label: 'Find a backend engineer in Lahore',
    body: 'Find me backend engineers in Lahore who know Java and Postgres.',
  },
  {
    label: 'Summarise an employee timeline',
    body: 'Give me a short summary of Adeel Rahman\'s timeline over the last 12 months.',
  },
];

// ── Canned replies (fixture answers keyed by intent) ───────────────────

export interface CannedReply {
  match: (q: string) => boolean;
  body: string;
  citations?: Citation[];
}

const policyCite = (sourceId: string): Citation => {
  const c = policyChunks.find((p) => p.sourceId === sourceId)!;
  return { sourceType: 'POLICY', sourceId: c.sourceId, title: c.title, snippet: c.snippet };
};

export const cannedReplies: CannedReply[] = [
  {
    match: (q) => /leave|annual|casual|sick/i.test(q),
    body:
      "Here's a short summary of the leave entitlements for confirmed school staff:\n\n" +
      '• **Annual leave** — 18 working days per calendar year, prorated by joining date. Up to 7 days may be carried forward; the remainder lapses on 31 December.\n' +
      '• **Casual leave** — 10 days per year. Cannot be carried forward and is not encashable at separation.\n' +
      '• **Sick leave** — 10 paid days per year. A medical certificate is required for any absence longer than two consecutive working days.\n\n' +
      'Probationers are eligible for casual and sick leave only; annual leave begins to accrue after confirmation.',
    citations: [
      policyCite('POL-LEAVE-2026-§3.2'),
      policyCite('POL-LEAVE-2026-§3.5'),
      policyCite('POL-LEAVE-2026-§4.1'),
      policyCite('POL-PROBATION-2026-§2'),
    ],
  },
  {
    match: (q) => /overtime|ot |over time/i.test(q),
    body:
      'Overtime is approved in advance by the line manager. The applicable rates are:\n\n' +
      '• **Weekdays** — 1.5× the basic hourly rate.\n' +
      '• **Sundays and gazetted holidays** — 2× the basic hourly rate.\n\n' +
      'Grades **M3 and above are not eligible** for overtime — their compensation already accounts for variable hours.',
    citations: [policyCite('POL-OT-2026-§5')],
  },
  {
    match: (q) => /probation|confirm/i.test(q),
    body:
      'New hires are on probation for the **first 90 days** from their joining date. ' +
      'Confirmation requires a satisfactory review by the line manager and HR, completed no later than the **100th day**. ' +
      'During probation, employees accrue casual and sick leave but not annual leave.',
    citations: [policyCite('POL-PROBATION-2026-§2')],
  },
  {
    match: (q) => /per[- ]?diem|travel|expense|reimburse/i.test(q),
    body:
      'Domestic travel allowances:\n\n' +
      '• **Per-diem** — PKR 4,500 / day, inclusive of meals and incidentals.\n' +
      '• **Hotel** — capped at PKR 12,000 / night for grades up to M2, and PKR 18,000 / night for M3 and above.\n\n' +
      'International travel uses a per-country grid maintained by Finance — ask your line manager for the rate before booking.',
    citations: [policyCite('POL-EXP-2026-§1.4')],
  },
  {
    match: (q) => /disciplin|misconduct|show[- ]?cause/i.test(q),
    body:
      'The disciplinary procedure grades misconduct as **Minor**, **Major** or **Gross**. ' +
      'Any formal action is preceded by a show-cause notice; the employee has **7 working days** to respond before the disciplinary committee convenes. ' +
      'HR maintains the case file and issues the final decision letter.',
    citations: [policyCite('POL-CODE-2026-§7')],
  },
  {
    match: (q) => /backend|engineer|java|postgres|lahore|karachi|islamabad/i.test(q),
    body:
      'I found 4 strong matches in the Lahore office whose profile cards mention Java and Postgres:\n\n' +
      '1. **Adeel Rahman** — Senior Backend Engineer · IT · 6.2 yrs · Java, Spring Boot, PostgreSQL, Kafka.\n' +
      '2. **Saima Tariq** — Backend Engineer II · IT · 3.4 yrs · Java, Spring, Postgres, Redis.\n' +
      '3. **Bilal Ahmed** — Tech Lead · IT · 8.7 yrs · Java, Postgres, system design, mentoring.\n' +
      '4. **Faiza Iqbal** — Backend Engineer · IT · 1.9 yrs · Java, Postgres, Liquibase.\n\n' +
      'You can open the **Semantic search** page from the sidebar for the full ranked list with relevance scores.',
  },
  {
    match: (q) => /summari[sz]e|summary|recent|timeline|history/i.test(q),
    body:
      'Here\'s a short summary of recent activity for **Adeel Rahman** (May 2025 → May 2026):\n\n' +
      '• Confirmed off probation on **2025-08-21** following a positive 90-day review.\n' +
      '• Completed the **Spring Boot 4 internal certification** in October 2025.\n' +
      '• Moved from **Backend Engineer II → Senior Backend Engineer** on **2026-01-01** with a 22% increment.\n' +
      '• Took 12 days of annual leave (Eid + winter break), no unauthorised absences.\n' +
      '• Closed **34 tickets** as primary author and reviewed **128 PRs** in the period.\n\n' +
      'No disciplinary records, no overdue trainings, certifications all current.',
    citations: [
      { sourceType: 'TIMELINE', sourceId: 'EMP-1042-TL', title: 'Adeel Rahman — Timeline', snippet: 'Confirmation, increment, training events.' },
      { sourceType: 'AUDIT', sourceId: 'AUD-1042-12M', title: 'Audit trail (12 months)', snippet: '128 PR reviews, 34 tickets closed.' },
    ],
  },
];

export const fallbackReply: CannedReply = {
  match: () => true,
  body:
    "I can answer questions about school policies, find people in the directory, " +
    'and summarise an employee\'s timeline or recent audit activity. Try one of the suggested ' +
    'prompts on the left, or ask me something specific — I\'ll do my best with the records ' +
    "I'm allowed to read.",
};

export function pickReply(question: string): CannedReply {
  return cannedReplies.find((r) => r.match(question)) ?? fallbackReply;
}

// ── Seed conversations (populated on first load) ──────────────────────

const TODAY = '2026-05-04';
const YESTERDAY = '2026-05-03';
const LAST_WEEK = '2026-04-29';

export const seedConversations: Conversation[] = [
  {
    id: 'conv-001',
    userId: currentUser.id,
    userName: currentUser.name,
    title: 'Leave policy for confirmed employees',
    updatedAt: `${TODAY}T10:14:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-001-1',
        role: 'USER',
        body: 'What are the leave entitlements for confirmed employees?',
        createdAt: `${TODAY}T10:13:42+05:00`,
      },
      {
        id: 'm-001-2',
        role: 'ASSISTANT',
        body: cannedReplies[0].body,
        citations: cannedReplies[0].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 412,
        completionTokens: 184,
        createdAt: `${TODAY}T10:14:00+05:00`,
      },
    ],
  },
  {
    id: 'conv-002',
    userId: currentUser.id,
    userName: currentUser.name,
    title: 'Overtime rates and eligibility',
    updatedAt: `${YESTERDAY}T16:22:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-002-1',
        role: 'USER',
        body: 'How is overtime paid, and which grades are eligible?',
        createdAt: `${YESTERDAY}T16:21:18+05:00`,
      },
      {
        id: 'm-002-2',
        role: 'ASSISTANT',
        body: cannedReplies[1].body,
        citations: cannedReplies[1].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 286,
        completionTokens: 102,
        createdAt: `${YESTERDAY}T16:22:00+05:00`,
      },
    ],
  },
  {
    id: 'conv-003',
    userId: currentUser.id,
    userName: currentUser.name,
    title: 'Adeel Rahman — last 12 months',
    updatedAt: `${LAST_WEEK}T11:08:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-003-1',
        role: 'USER',
        body: 'Summarise Adeel Rahman\'s last 12 months.',
        createdAt: `${LAST_WEEK}T11:07:30+05:00`,
      },
      {
        id: 'm-003-2',
        role: 'ASSISTANT',
        body: cannedReplies[6].body,
        citations: cannedReplies[6].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 612,
        completionTokens: 248,
        createdAt: `${LAST_WEEK}T11:08:00+05:00`,
      },
    ],
  },
];

// Other users' conversations — shown in the admin transcripts reader.
export const otherUsersConversations: Conversation[] = [
  {
    id: 'conv-101',
    userId: 1042,
    userName: 'Adeel Rahman',
    title: 'Per-diem for the Karachi trip',
    updatedAt: `${YESTERDAY}T09:42:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-101-1',
        role: 'USER',
        body: 'What\'s the per-diem and hotel cap if I travel to Karachi for two nights?',
        createdAt: `${YESTERDAY}T09:41:08+05:00`,
      },
      {
        id: 'm-101-2',
        role: 'ASSISTANT',
        body: cannedReplies[3].body,
        citations: cannedReplies[3].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 248,
        completionTokens: 142,
        createdAt: `${YESTERDAY}T09:42:00+05:00`,
      },
    ],
  },
  {
    id: 'conv-102',
    userId: 1188,
    userName: 'Bilal Ahmed',
    title: 'Probation review timing',
    updatedAt: `${LAST_WEEK}T14:20:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-102-1',
        role: 'USER',
        body: 'When does probation end for someone who joined on 1 February?',
        createdAt: `${LAST_WEEK}T14:19:24+05:00`,
      },
      {
        id: 'm-102-2',
        role: 'ASSISTANT',
        body: cannedReplies[2].body,
        citations: cannedReplies[2].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 198,
        completionTokens: 86,
        createdAt: `${LAST_WEEK}T14:20:00+05:00`,
      },
    ],
  },
  {
    id: 'conv-103',
    userId: 1212,
    userName: 'Saima Tariq',
    title: 'Disciplinary procedure overview',
    updatedAt: `${LAST_WEEK}T08:15:00+05:00`,
    archived: false,
    messages: [
      {
        id: 'm-103-1',
        role: 'USER',
        body: 'How does the show-cause process work?',
        createdAt: `${LAST_WEEK}T08:14:10+05:00`,
      },
      {
        id: 'm-103-2',
        role: 'ASSISTANT',
        body: cannedReplies[4].body,
        citations: cannedReplies[4].citations,
        model: 'qwen2.5:7b-instruct-q4_K_M',
        promptTokens: 220,
        completionTokens: 94,
        createdAt: `${LAST_WEEK}T08:15:00+05:00`,
      },
    ],
  },
];

// ── Semantic search results ───────────────────────────────────────────

export interface EmployeeMatch {
  employeeId: number;
  code: string;
  name: string;
  designation: string;
  department: string;
  city: string;
  manager: string;
  joiningDate: string;
  skills: string[];
  score: number; // 0..1 cosine similarity
  highlight: string;
}

export const employeeMatches: EmployeeMatch[] = [
  {
    employeeId: 1042,
    code: 'FS-1042',
    name: 'Adeel Rahman',
    designation: 'Senior Backend Engineer',
    department: 'IT',
    city: 'Lahore',
    manager: 'Bilal Ahmed',
    joiningDate: '2019-08-12',
    skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Kafka', 'Redis'],
    score: 0.92,
    highlight: 'Six years of Java + Postgres backend work; led the payroll-service rewrite in 2025.',
  },
  {
    employeeId: 1188,
    code: 'FS-1188',
    name: 'Bilal Ahmed',
    designation: 'Tech Lead',
    department: 'IT',
    city: 'Lahore',
    manager: 'Faisal Mahmood (CTO)',
    joiningDate: '2017-03-04',
    skills: ['Java', 'PostgreSQL', 'System Design', 'Mentoring', 'Kubernetes'],
    score: 0.89,
    highlight: 'Tech lead for the IT group in Lahore; deep Java + Postgres background.',
  },
  {
    employeeId: 1212,
    code: 'FS-1212',
    name: 'Saima Tariq',
    designation: 'Backend Engineer II',
    department: 'IT',
    city: 'Lahore',
    manager: 'Adeel Rahman',
    joiningDate: '2022-11-18',
    skills: ['Java', 'Spring', 'PostgreSQL', 'Redis'],
    score: 0.84,
    highlight: 'Backend engineer in Lahore; Java/Postgres day-to-day on the employee-service.',
  },
  {
    employeeId: 1304,
    code: 'FS-1304',
    name: 'Faiza Iqbal',
    designation: 'Backend Engineer',
    department: 'IT',
    city: 'Lahore',
    manager: 'Adeel Rahman',
    joiningDate: '2024-06-03',
    skills: ['Java', 'PostgreSQL', 'Liquibase'],
    score: 0.78,
    highlight: 'Junior backend engineer in Lahore; recent hire with Java + Postgres exposure.',
  },
  {
    employeeId: 1422,
    code: 'FS-1422',
    name: 'Hamza Sheikh',
    designation: 'Backend Engineer II',
    department: 'IT',
    city: 'Karachi',
    manager: 'Imran Saeed',
    joiningDate: '2021-09-10',
    skills: ['Java', 'Spring', 'PostgreSQL'],
    score: 0.71,
    highlight: 'Java + Postgres engineer, but based in Karachi rather than Lahore.',
  },
  {
    employeeId: 1488,
    code: 'FS-1488',
    name: 'Mariam Yousaf',
    designation: 'Frontend Engineer',
    department: 'IT',
    city: 'Lahore',
    manager: 'Bilal Ahmed',
    joiningDate: '2023-02-21',
    skills: ['React', 'TypeScript', 'Vite'],
    score: 0.42,
    highlight: 'Lahore IT, but frontend rather than backend — included for breadth.',
  },
];

// ── Summaries gallery ─────────────────────────────────────────────────

export interface SummaryFixture {
  id: string;
  kind: 'EMPLOYEE_TIMELINE' | 'AUDIT_BY_ACTOR' | 'TEAM_PULSE';
  subjectName: string;
  subjectMeta: string;
  rangeLabel: string;
  body: string;
  citations: Citation[];
}

export const summaryFixtures: SummaryFixture[] = [
  {
    id: 'sum-emp-1042',
    kind: 'EMPLOYEE_TIMELINE',
    subjectName: 'Adeel Rahman',
    subjectMeta: 'FS-1042 · Senior Backend Engineer · IT · Lahore',
    rangeLabel: 'Last 12 months',
    body:
      'Adeel had a strong year. Confirmed off probation in August 2025 with a positive review, ' +
      'completed the Spring Boot 4 internal certification in October, and moved from Backend Engineer II ' +
      'to Senior Backend Engineer on 1 January 2026 with a 22% increment. He took 12 days of annual leave ' +
      'across Eid and the winter break with no unauthorised absences. On the contribution side he closed ' +
      '34 tickets as primary author and reviewed 128 pull requests, with no disciplinary records and all ' +
      'certifications current.',
    citations: [
      { sourceType: 'TIMELINE', sourceId: 'EMP-1042-TL', title: 'Adeel Rahman — Timeline', snippet: 'Confirmation, increment, training events over 12 months.' },
      { sourceType: 'AUDIT', sourceId: 'AUD-1042-12M', title: 'Audit trail (12 months)', snippet: '128 PR reviews, 34 tickets closed.' },
    ],
  },
  {
    id: 'sum-aud-1042',
    kind: 'AUDIT_BY_ACTOR',
    subjectName: 'Adeel Rahman',
    subjectMeta: 'FS-1042 · 487 audit events',
    rangeLabel: 'Last 30 days',
    body:
      'Adeel\'s recent audit footprint is consistent with his role as a senior backend engineer. ' +
      'Over the past 30 days the dominant activity is code review (98 PR comment events) and ticket ' +
      'transitions (54 status changes). He approved 12 timesheet requests for his direct reports ' +
      'and authored 6 employee-service deployments through the standard release flow. There are ' +
      'no failed-authentication, permission-denied or out-of-hours admin events in the period.',
    citations: [
      { sourceType: 'AUDIT', sourceId: 'AUD-1042-30D', title: 'Audit trail (30 days)', snippet: '487 events, mostly PR review and ticket transitions.' },
    ],
  },
  {
    id: 'sum-team-it',
    kind: 'TEAM_PULSE',
    subjectName: 'IT — Lahore',
    subjectMeta: '24 engineers · 3 leads',
    rangeLabel: 'May 2026',
    body:
      'The Lahore IT team is stable: 24 engineers across 3 leads, attrition steady at one departure ' +
      'in the period, and no open disciplinary cases. Average attendance is 96.4%, slightly above the ' +
      'company average. Pending HR queues for this team are clean — three certifications expire next ' +
      'quarter (all in good time to renew) and no probation reviews are overdue. Payroll variance ' +
      'this month is +3.2% MoM, consistent with the January promotion cohort\'s first full month at ' +
      'the new grade.',
    citations: [
      { sourceType: 'EMPLOYEE', sourceId: 'DEPT-IT-LHE', title: 'IT — Lahore directory snapshot', snippet: '24 engineers, 3 leads.' },
      { sourceType: 'TIMELINE', sourceId: 'DEPT-IT-LHE-TL', title: 'IT — Lahore timeline (May 2026)', snippet: 'Attrition, attendance, queues.' },
    ],
  },
];

// ── Ingest runs (admin/ops view) ──────────────────────────────────────

export type IngestStatus = 'OK' | 'RUNNING' | 'FAILED';

export interface IngestRun {
  id: string;
  sourceType: 'POLICY' | 'EMPLOYEE' | 'TIMELINE' | 'AUDIT';
  startedAt: string;
  finishedAt?: string;
  status: IngestStatus;
  inserted: number;
  updated: number;
  skipped: number;
  errorMessage?: string;
}

export const ingestRuns: IngestRun[] = [
  {
    id: 'ING-2026-05-04-002',
    sourceType: 'EMPLOYEE',
    startedAt: '2026-05-04T02:05:00+05:00',
    finishedAt: '2026-05-04T02:08:14+05:00',
    status: 'OK',
    inserted: 4,
    updated: 22,
    skipped: 1196,
  },
  {
    id: 'ING-2026-05-04-001',
    sourceType: 'POLICY',
    startedAt: '2026-05-04T02:00:00+05:00',
    finishedAt: '2026-05-04T02:04:48+05:00',
    status: 'OK',
    inserted: 0,
    updated: 0,
    skipped: 142,
  },
  {
    id: 'ING-2026-05-03-002',
    sourceType: 'EMPLOYEE',
    startedAt: '2026-05-03T02:05:00+05:00',
    finishedAt: '2026-05-03T02:08:42+05:00',
    status: 'OK',
    inserted: 6,
    updated: 18,
    skipped: 1198,
  },
  {
    id: 'ING-2026-05-03-001',
    sourceType: 'POLICY',
    startedAt: '2026-05-03T02:00:00+05:00',
    finishedAt: '2026-05-03T02:04:32+05:00',
    status: 'OK',
    inserted: 0,
    updated: 4,
    skipped: 138,
  },
  {
    id: 'ING-2026-05-02-002',
    sourceType: 'EMPLOYEE',
    startedAt: '2026-05-02T02:05:00+05:00',
    finishedAt: '2026-05-02T02:11:08+05:00',
    status: 'FAILED',
    inserted: 412,
    updated: 0,
    skipped: 0,
    errorMessage: 'employee-service /v1/employees timed out after 30s on page 9; aborted partway. Retried successfully on the 2026-05-03 run.',
  },
  {
    id: 'ING-2026-05-02-001',
    sourceType: 'POLICY',
    startedAt: '2026-05-02T02:00:00+05:00',
    finishedAt: '2026-05-02T02:04:18+05:00',
    status: 'OK',
    inserted: 4,
    updated: 0,
    skipped: 138,
  },
  {
    id: 'ING-2026-05-01-002',
    sourceType: 'EMPLOYEE',
    startedAt: '2026-05-01T02:05:00+05:00',
    finishedAt: '2026-05-01T02:09:02+05:00',
    status: 'OK',
    inserted: 2,
    updated: 14,
    skipped: 1198,
  },
  {
    id: 'ING-2026-05-01-001',
    sourceType: 'POLICY',
    startedAt: '2026-05-01T02:00:00+05:00',
    finishedAt: '2026-05-01T02:03:58+05:00',
    status: 'OK',
    inserted: 0,
    updated: 0,
    skipped: 142,
  },
];

// ── Index health (admin/ops widget) ───────────────────────────────────

export const indexHealth = {
  totalEmbeddings: 4286,
  bySource: [
    { sourceType: 'POLICY', count: 142, lastEmbeddedAt: '2026-05-04T02:04:48+05:00' },
    { sourceType: 'EMPLOYEE', count: 1224, lastEmbeddedAt: '2026-05-04T02:08:14+05:00' },
    { sourceType: 'TIMELINE', count: 1820, lastEmbeddedAt: '2026-05-03T18:42:00+05:00' },
    { sourceType: 'AUDIT', count: 1100, lastEmbeddedAt: '2026-05-03T18:42:00+05:00' },
  ],
  vectorDim: 768,
  model: 'nomic-embed-text',
  pgvectorIndex: 'hnsw',
};
