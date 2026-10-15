// Mock fixtures for the Accounts (Preview) workspace. Numbers are
// hand-tuned for a mid-size manufacturing company (Forward Sports
// stand-in) — realistic enough to demo, clearly fictional in the
// aggregate. All values in PKR; YTD assumes May 2026.

export interface KpiPoint {
  label: string;
  value: number;
}

export interface ExecKpis {
  cashOnHand: number;
  cashDeltaPct: number;
  mtdRevenue: number;
  mtdRevenueDeltaPct: number;
  ytdRevenue: number;
  grossMarginPct: number;
  netMarginPct: number;
  workingCapital: number;
  arOutstanding: number;
  apOutstanding: number;
  inventoryValue: number;
  arDays: number;
  apDays: number;
}

export const execKpis: ExecKpis = {
  cashOnHand: 184_250_000,
  cashDeltaPct: 6.4,
  mtdRevenue: 312_480_000,
  mtdRevenueDeltaPct: 11.8,
  ytdRevenue: 3_148_220_000,
  grossMarginPct: 38.4,
  netMarginPct: 14.7,
  workingCapital: 421_900_000,
  arOutstanding: 268_440_000,
  apOutstanding: 142_180_000,
  inventoryValue: 489_300_000,
  arDays: 47,
  apDays: 36,
};

export interface MonthPoint {
  month: string;
  revenue: number;
  cogs: number;
  opex: number;
  netIncome: number;
  cashIn: number;
  cashOut: number;
  cashClose: number;
}

// 12 trailing months ending May 2026.
export const monthly: MonthPoint[] = [
  { month: 'Jun 25', revenue: 248_120_000, cogs: 156_320_000, opex: 58_400_000, netIncome: 21_900_000, cashIn: 230_120_000, cashOut: 218_500_000, cashClose: 121_400_000 },
  { month: 'Jul 25', revenue: 261_840_000, cogs: 162_180_000, opex: 60_120_000, netIncome: 26_140_000, cashIn: 245_900_000, cashOut: 228_800_000, cashClose: 138_500_000 },
  { month: 'Aug 25', revenue: 274_520_000, cogs: 168_900_000, opex: 61_440_000, netIncome: 30_280_000, cashIn: 258_200_000, cashOut: 240_100_000, cashClose: 156_600_000 },
  { month: 'Sep 25', revenue: 268_300_000, cogs: 165_700_000, opex: 60_980_000, netIncome: 28_320_000, cashIn: 252_700_000, cashOut: 244_600_000, cashClose: 164_700_000 },
  { month: 'Oct 25', revenue: 285_780_000, cogs: 174_120_000, opex: 62_840_000, netIncome: 33_400_000, cashIn: 269_400_000, cashOut: 250_900_000, cashClose: 183_200_000 },
  { month: 'Nov 25', revenue: 292_100_000, cogs: 178_240_000, opex: 63_600_000, netIncome: 34_900_000, cashIn: 273_400_000, cashOut: 261_700_000, cashClose: 194_900_000 },
  { month: 'Dec 25', revenue: 318_650_000, cogs: 192_800_000, opex: 68_200_000, netIncome: 39_900_000, cashIn: 298_200_000, cashOut: 289_400_000, cashClose: 203_700_000 },
  { month: 'Jan 26', revenue: 281_400_000, cogs: 173_120_000, opex: 62_120_000, netIncome: 30_900_000, cashIn: 264_800_000, cashOut: 268_500_000, cashClose: 200_000_000 },
  { month: 'Feb 26', revenue: 274_220_000, cogs: 169_320_000, opex: 61_400_000, netIncome: 28_980_000, cashIn: 257_900_000, cashOut: 253_700_000, cashClose: 204_200_000 },
  { month: 'Mar 26', revenue: 296_840_000, cogs: 180_120_000, opex: 64_280_000, netIncome: 35_800_000, cashIn: 279_400_000, cashOut: 266_900_000, cashClose: 216_700_000 },
  { month: 'Apr 26', revenue: 298_960_000, cogs: 181_900_000, opex: 65_120_000, netIncome: 36_400_000, cashIn: 281_200_000, cashOut: 274_800_000, cashClose: 223_100_000 },
  { month: 'May 26', revenue: 312_480_000, cogs: 188_640_000, opex: 66_780_000, netIncome: 38_900_000, cashIn: 294_700_000, cashOut: 268_900_000, cashClose: 248_900_000 },
];

export interface CashForecastPoint {
  weekStart: string;
  inflows: number;
  outflows: number;
  net: number;
  closingBalance: number;
}

// 13-week rolling cash forecast from week of 2026-05-04.
export const cashForecast: CashForecastPoint[] = Array.from({ length: 13 }).map((_, i) => {
  const start = new Date(2026, 4, 4 + i * 7);
  const weekStart = start.toISOString().slice(0, 10);
  const inflows = 62_000_000 + Math.round(Math.sin(i * 0.6) * 8_000_000) + i * 1_400_000;
  const outflows = 58_000_000 + Math.round(Math.cos(i * 0.7) * 6_000_000) + i * 1_100_000;
  const net = inflows - outflows;
  const closingBalance = 184_250_000 + i * 4_300_000 + Math.round(Math.sin(i) * 6_000_000);
  return { weekStart, inflows, outflows, net, closingBalance };
});

export interface AgingBucket {
  label: string;
  amount: number;
}

export const arAging: AgingBucket[] = [
  { label: 'Current', amount: 142_300_000 },
  { label: '1–30', amount: 71_900_000 },
  { label: '31–60', amount: 32_400_000 },
  { label: '61–90', amount: 14_200_000 },
  { label: '90+', amount: 7_640_000 },
];

export const apAging: AgingBucket[] = [
  { label: 'Current', amount: 86_400_000 },
  { label: '1–30', amount: 38_700_000 },
  { label: '31–60', amount: 12_800_000 },
  { label: '61–90', amount: 3_180_000 },
  { label: '90+', amount: 1_100_000 },
];

export interface PartyRevenue {
  name: string;
  amount: number;
  share: number;
}

export const topCustomers: PartyRevenue[] = [
  { name: 'Adidas Pakistan', amount: 624_900_000, share: 0.198 },
  { name: 'Wilson Sporting Goods', amount: 482_100_000, share: 0.153 },
  { name: 'Forward Pakistan Retail', amount: 318_700_000, share: 0.101 },
  { name: 'Decathlon Asia', amount: 246_400_000, share: 0.078 },
  { name: 'Spalding USA', amount: 198_320_000, share: 0.063 },
  { name: 'Other (412 customers)', amount: 1_277_800_000, share: 0.406 },
];

export const topProducts: PartyRevenue[] = [
  { name: 'Match Football – Tier 1', amount: 884_120_000, share: 0.281 },
  { name: 'Training Football – Tier 2', amount: 612_800_000, share: 0.195 },
  { name: 'Volleyball – Premium', amount: 318_440_000, share: 0.101 },
  { name: 'Basketball – Indoor', amount: 246_900_000, share: 0.078 },
  { name: 'Goalkeeper gloves', amount: 186_240_000, share: 0.059 },
  { name: 'Other SKUs (84)', amount: 899_720_000, share: 0.286 },
];

export interface ManufacturingKpis {
  costPerUnitTrend: { month: string; pkr: number }[];
  inventoryTurnover: number;
  wipValue: number;
  scrapPct: number;
  productionUnitsMtd: number;
  productionUnitsMtdDeltaPct: number;
}

export const mfgKpis: ManufacturingKpis = {
  costPerUnitTrend: [
    { month: 'Dec 25', pkr: 1248 },
    { month: 'Jan 26', pkr: 1262 },
    { month: 'Feb 26', pkr: 1271 },
    { month: 'Mar 26', pkr: 1255 },
    { month: 'Apr 26', pkr: 1241 },
    { month: 'May 26', pkr: 1229 },
  ],
  inventoryTurnover: 4.8,
  wipValue: 78_400_000,
  scrapPct: 2.3,
  productionUnitsMtd: 248_300,
  productionUnitsMtdDeltaPct: 4.6,
};

export interface JournalEntry {
  id: string;
  postedOn: string;
  reference: string;
  description: string;
  debit: number;
  credit: number;
  status: 'POSTED' | 'DRAFT' | 'REVERSED';
  source: 'Manual' | 'AP' | 'AR' | 'Payroll' | 'Bank' | 'Inventory' | 'Depreciation';
  createdBy: string;
}

export const recentJournals: JournalEntry[] = [
  { id: 'JE-26-04812', postedOn: '2026-05-03', reference: 'INV-AD-2206', description: 'Sales — Adidas PO 88-2206', debit: 28_400_000, credit: 28_400_000, status: 'POSTED', source: 'AR', createdBy: 'Hira K.' },
  { id: 'JE-26-04811', postedOn: '2026-05-03', reference: 'PR-MAY-26', description: 'Payroll posting — May 2026', debit: 64_240_000, credit: 64_240_000, status: 'POSTED', source: 'Payroll', createdBy: 'System' },
  { id: 'JE-26-04810', postedOn: '2026-05-03', reference: 'BILL-LH-1188', description: 'Lahore PVC — raw material', debit: 8_120_000, credit: 8_120_000, status: 'POSTED', source: 'AP', createdBy: 'Adeel R.' },
  { id: 'JE-26-04809', postedOn: '2026-05-02', reference: 'BNK-HBL-2042', description: 'HBL — bank charges', debit: 84_200, credit: 84_200, status: 'POSTED', source: 'Bank', createdBy: 'Adeel R.' },
  { id: 'JE-26-04808', postedOn: '2026-05-02', reference: 'DEP-MAY-FA', description: 'May depreciation — fixed assets', debit: 4_240_000, credit: 4_240_000, status: 'POSTED', source: 'Depreciation', createdBy: 'System' },
  { id: 'JE-26-04807', postedOn: '2026-05-02', reference: 'WIP-CONS-0425', description: 'WIP — material consumption', debit: 14_820_000, credit: 14_820_000, status: 'POSTED', source: 'Inventory', createdBy: 'System' },
  { id: 'JE-26-04806', postedOn: '2026-05-01', reference: 'ADJ-SALES-RET', description: 'Sales return — Wilson order 882', debit: 1_240_000, credit: 1_240_000, status: 'POSTED', source: 'Manual', createdBy: 'Hira K.' },
  { id: 'JE-26-04805', postedOn: '2026-05-01', reference: 'INV-DC-1924', description: 'Sales — Decathlon DC-1924', debit: 18_640_000, credit: 18_640_000, status: 'POSTED', source: 'AR', createdBy: 'Hira K.' },
  { id: 'JE-26-04804-D', postedOn: '2026-05-01', reference: 'ADJ-FX-APR', description: 'FX revaluation — USD payables', debit: 2_080_000, credit: 2_080_000, status: 'DRAFT', source: 'Manual', createdBy: 'Adeel R.' },
  { id: 'JE-26-04803', postedOn: '2026-04-30', reference: 'BNK-MCB-9921', description: 'MCB transfer to vendor — Sialkot Leather', debit: 6_840_000, credit: 6_840_000, status: 'POSTED', source: 'Bank', createdBy: 'Adeel R.' },
];

export interface CoaNode {
  code: string;
  name: string;
  type: 'Asset' | 'Liability' | 'Equity' | 'Income' | 'Expense';
  balance: number;
  children?: CoaNode[];
}

export const coa: CoaNode[] = [
  {
    code: '1000',
    name: 'Assets',
    type: 'Asset',
    balance: 1_842_300_000,
    children: [
      {
        code: '1100',
        name: 'Current Assets',
        type: 'Asset',
        balance: 1_142_120_000,
        children: [
          { code: '1110', name: 'Cash & bank', type: 'Asset', balance: 184_250_000 },
          { code: '1120', name: 'Trade debtors', type: 'Asset', balance: 268_440_000 },
          { code: '1130', name: 'Inventory — raw', type: 'Asset', balance: 218_900_000 },
          { code: '1140', name: 'Inventory — WIP', type: 'Asset', balance: 78_400_000 },
          { code: '1150', name: 'Inventory — finished', type: 'Asset', balance: 192_000_000 },
          { code: '1170', name: 'Advances to suppliers', type: 'Asset', balance: 64_120_000 },
          { code: '1190', name: 'Other receivables', type: 'Asset', balance: 136_010_000 },
        ],
      },
      {
        code: '1500',
        name: 'Fixed Assets',
        type: 'Asset',
        balance: 700_180_000,
        children: [
          { code: '1510', name: 'Plant & machinery', type: 'Asset', balance: 522_400_000 },
          { code: '1520', name: 'Buildings', type: 'Asset', balance: 142_900_000 },
          { code: '1530', name: 'Office equipment', type: 'Asset', balance: 18_400_000 },
          { code: '1540', name: 'Vehicles', type: 'Asset', balance: 16_480_000 },
        ],
      },
    ],
  },
  {
    code: '2000',
    name: 'Liabilities',
    type: 'Liability',
    balance: 642_840_000,
    children: [
      { code: '2100', name: 'Trade creditors', type: 'Liability', balance: 142_180_000 },
      { code: '2200', name: 'Accrued expenses', type: 'Liability', balance: 38_900_000 },
      { code: '2300', name: 'Sales tax payable', type: 'Liability', balance: 26_400_000 },
      { code: '2310', name: 'Income tax payable', type: 'Liability', balance: 18_840_000 },
      { code: '2400', name: 'Long-term loans', type: 'Liability', balance: 384_120_000 },
      { code: '2500', name: 'Provident fund payable', type: 'Liability', balance: 32_400_000 },
    ],
  },
  {
    code: '3000',
    name: 'Equity',
    type: 'Equity',
    balance: 1_199_460_000,
    children: [
      { code: '3100', name: 'Share capital', type: 'Equity', balance: 600_000_000 },
      { code: '3200', name: 'Retained earnings', type: 'Equity', balance: 537_120_000 },
      { code: '3300', name: 'Current year earnings', type: 'Equity', balance: 62_340_000 },
    ],
  },
  {
    code: '4000',
    name: 'Income',
    type: 'Income',
    balance: 3_148_220_000,
    children: [
      { code: '4100', name: 'Sales — exports', type: 'Income', balance: 2_186_400_000 },
      { code: '4200', name: 'Sales — local', type: 'Income', balance: 924_120_000 },
      { code: '4300', name: 'Other income', type: 'Income', balance: 37_700_000 },
    ],
  },
  {
    code: '5000',
    name: 'Expenses',
    type: 'Expense',
    balance: 2_690_780_000,
    children: [
      { code: '5100', name: 'Cost of goods sold', type: 'Expense', balance: 1_938_900_000 },
      { code: '5200', name: 'Salaries & wages', type: 'Expense', balance: 421_900_000 },
      { code: '5300', name: 'Utilities', type: 'Expense', balance: 84_200_000 },
      { code: '5400', name: 'Freight & duty', type: 'Expense', balance: 142_400_000 },
      { code: '5500', name: 'Depreciation', type: 'Expense', balance: 56_400_000 },
      { code: '5900', name: 'Other operating expenses', type: 'Expense', balance: 46_980_000 },
    ],
  },
];

export interface VendorBill {
  id: string;
  vendor: string;
  billNumber: string;
  billDate: string;
  dueDate: string;
  amount: number;
  status: 'DRAFT' | 'AWAITING_APPROVAL' | 'APPROVED' | 'PAID' | 'OVERDUE';
  matchStatus: '3-way matched' | 'PO mismatch' | 'GRN mismatch' | 'No PO';
  poNumber?: string;
}

export const vendorBills: VendorBill[] = [
  { id: 'BILL-1188', vendor: 'Lahore PVC Co.', billNumber: 'INV-22041', billDate: '2026-05-03', dueDate: '2026-06-02', amount: 8_120_000, status: 'APPROVED', matchStatus: '3-way matched', poNumber: 'PO-26-882' },
  { id: 'BILL-1187', vendor: 'Sialkot Leather (Pvt) Ltd', billNumber: 'SL-2206', billDate: '2026-05-02', dueDate: '2026-05-31', amount: 14_280_000, status: 'AWAITING_APPROVAL', matchStatus: '3-way matched', poNumber: 'PO-26-869' },
  { id: 'BILL-1186', vendor: 'PrintLab Karachi', billNumber: 'PL-9921', billDate: '2026-05-02', dueDate: '2026-05-30', amount: 1_840_000, status: 'AWAITING_APPROVAL', matchStatus: 'PO mismatch', poNumber: 'PO-26-844' },
  { id: 'BILL-1185', vendor: 'KAPCO Power', billNumber: 'KAPCO-MAY26', billDate: '2026-05-01', dueDate: '2026-05-21', amount: 6_420_000, status: 'APPROVED', matchStatus: 'No PO' },
  { id: 'BILL-1184', vendor: 'Punjab Yarn Mills', billNumber: 'PYM-4421', billDate: '2026-04-29', dueDate: '2026-05-29', amount: 22_400_000, status: 'PAID', matchStatus: '3-way matched', poNumber: 'PO-26-812' },
  { id: 'BILL-1183', vendor: 'Sialkot Logistics', billNumber: 'SL-LOG-188', billDate: '2026-04-28', dueDate: '2026-04-30', amount: 1_280_000, status: 'OVERDUE', matchStatus: 'GRN mismatch', poNumber: 'PO-26-800' },
  { id: 'BILL-1182', vendor: 'PTCL Business', billNumber: 'PTCL-MAY26', billDate: '2026-04-28', dueDate: '2026-05-15', amount: 184_000, status: 'APPROVED', matchStatus: 'No PO' },
  { id: 'BILL-1181', vendor: 'AccessToolings', billNumber: 'AT-220', billDate: '2026-04-27', dueDate: '2026-05-27', amount: 4_240_000, status: 'AWAITING_APPROVAL', matchStatus: '3-way matched', poNumber: 'PO-26-798' },
];

export interface CustomerInvoice {
  id: string;
  customer: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  amount: number;
  paid: number;
  status: 'OPEN' | 'PARTIAL' | 'PAID' | 'OVERDUE';
}

export const customerInvoices: CustomerInvoice[] = [
  { id: 'INV-2206', customer: 'Adidas Pakistan', invoiceNumber: 'AD-2206', invoiceDate: '2026-05-03', dueDate: '2026-06-02', amount: 28_400_000, paid: 0, status: 'OPEN' },
  { id: 'INV-2205', customer: 'Wilson Sporting Goods', invoiceNumber: 'WL-2205', invoiceDate: '2026-05-02', dueDate: '2026-06-01', amount: 24_120_000, paid: 0, status: 'OPEN' },
  { id: 'INV-2204', customer: 'Decathlon Asia', invoiceNumber: 'DC-1924', invoiceDate: '2026-05-01', dueDate: '2026-05-31', amount: 18_640_000, paid: 0, status: 'OPEN' },
  { id: 'INV-2203', customer: 'Forward Pakistan Retail', invoiceNumber: 'FPR-882', invoiceDate: '2026-04-28', dueDate: '2026-05-28', amount: 6_240_000, paid: 3_120_000, status: 'PARTIAL' },
  { id: 'INV-2202', customer: 'Spalding USA', invoiceNumber: 'SP-1144', invoiceDate: '2026-04-26', dueDate: '2026-05-26', amount: 12_840_000, paid: 0, status: 'OPEN' },
  { id: 'INV-2201', customer: 'Adidas Pakistan', invoiceNumber: 'AD-2188', invoiceDate: '2026-04-22', dueDate: '2026-04-30', amount: 14_280_000, paid: 0, status: 'OVERDUE' },
  { id: 'INV-2200', customer: 'Diadora Italia', invoiceNumber: 'DI-188', invoiceDate: '2026-04-18', dueDate: '2026-05-18', amount: 8_900_000, paid: 8_900_000, status: 'PAID' },
];

export interface BankAccount {
  id: string;
  name: string;
  number: string;
  bank: string;
  balance: number;
  currency: 'PKR' | 'USD' | 'EUR';
  pkrEquivalent: number;
}

export const bankAccounts: BankAccount[] = [
  { id: 'BNK-HBL-2042', name: 'HBL Operating', number: '••• 2042', bank: 'Habib Bank', balance: 78_400_000, currency: 'PKR', pkrEquivalent: 78_400_000 },
  { id: 'BNK-MCB-9921', name: 'MCB Disbursement', number: '••• 9921', bank: 'MCB Bank', balance: 42_180_000, currency: 'PKR', pkrEquivalent: 42_180_000 },
  { id: 'BNK-BOA-USD', name: 'BoA Export USD', number: '••• 6614', bank: 'Bank of America', balance: 188_400, currency: 'USD', pkrEquivalent: 52_750_000 },
  { id: 'BNK-MEEZAN-PKR', name: 'Meezan Payroll', number: '••• 1188', bank: 'Meezan Bank', balance: 10_920_000, currency: 'PKR', pkrEquivalent: 10_920_000 },
];

export interface CloseTask {
  id: string;
  title: string;
  owner: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
  dueDate: string;
}

export const closeTasks: CloseTask[] = [
  { id: 'CT-01', title: 'Bank reconciliations — all accounts', owner: 'Adeel R.', status: 'IN_PROGRESS', dueDate: '2026-05-06' },
  { id: 'CT-02', title: 'AR aging review & write-offs', owner: 'Hira K.', status: 'PENDING', dueDate: '2026-05-07' },
  { id: 'CT-03', title: 'AP cut-off accruals', owner: 'Adeel R.', status: 'PENDING', dueDate: '2026-05-07' },
  { id: 'CT-04', title: 'Inventory count & WIP valuation', owner: 'Saima T.', status: 'IN_PROGRESS', dueDate: '2026-05-08' },
  { id: 'CT-05', title: 'Depreciation run', owner: 'System', status: 'DONE', dueDate: '2026-05-04' },
  { id: 'CT-06', title: 'FX revaluation — USD payables', owner: 'Adeel R.', status: 'BLOCKED', dueDate: '2026-05-08' },
  { id: 'CT-07', title: 'Trial balance review', owner: 'Bilal A. (CFO)', status: 'PENDING', dueDate: '2026-05-10' },
  { id: 'CT-08', title: 'Sales tax annexure (FBR)', owner: 'Hira K.', status: 'PENDING', dueDate: '2026-05-12' },
];
