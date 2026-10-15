// /reports — single catalogue page driven by /bff/reports/catalog.
//
// The catalog is already permission-filtered server-side, so every entry
// the page receives is one the caller can run. We:
//   - segment by service (All / People / Attendance / Leaves / Payroll),
//   - debounce a substring search across title + description,
//   - group results by service when the segment is "All", and People
//     reports further by kind (lists, letters in bulk, per employee),
//   - open a parameter drawer (RunReportDrawer) on Run — except for sync
//     reports with no parameters (e.g. Department Strength), which
//     download straight from the card,
//   - open sync PDFs in the preview drawer instead of downloading them,
//   - list the payroll Account Reports (their own registry, adapted to
//     catalog entries) as the "Accounts" service, sub-grouped by registry
//     group, with their own run drawer.
//
// "Job history" sits at /reports/jobs (peer page). We add a deep link
// in the page header so users hopping back to check on an async report
// don't have to navigate the sidebar.

import { useContext, useMemo, useState } from 'react';
import { App as AntdApp, Button, Col, Row, Segmented, Space, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { History, RefreshCcw } from 'lucide-react';

import {
  FilterBar,
  PageHeader,
  RequestState,
  Section,
} from '@/components';
import { AbilityContext } from '@/auth';
import { forbiddenOr, getErrorMessage, useDebounced } from '@/lib';

import { AccountsRunDrawer } from '../accounts/AccountsRunDrawer';
import {
  accountsGroupLabel,
  isAccountsEntry,
  useAccountsCatalog,
  type AccountsCatalogEntry,
} from '../accounts/catalog';

import { fetchReportCatalog, fetchSyncReport, runSyncReport } from '../bff-api';
import type { ReportFile } from '../download';
import { PdfPreviewDrawer } from '../PdfPreviewDrawer';
import type { ReportCatalogEntry, ReportService } from '../types';
import { RunReportDrawer } from './RunReportDrawer';
import { ReportCatalogCard } from './ReportCatalogCard';
import { SERVICE_LABEL, SERVICE_ORDER } from './labels';
import {
  EMPLOYEE_KIND_LABEL,
  EMPLOYEE_KIND_ORDER,
  employeeReportKind,
} from './presets';

const { Text } = Typography;

type PdfPreview = {
  entry: ReportCatalogEntry;
  load: (signal: AbortSignal) => Promise<ReportFile>;
};

/** People reports split by kind, accounts by registry group; other services stay one flat group. */
function subGroups(
  service: ReportService,
  items: ReportCatalogEntry[],
): Array<{ label: string | null; items: ReportCatalogEntry[] }> {
  if (service === 'accounts') {
    const byGroup = new Map<string, ReportCatalogEntry[]>();
    for (const e of items) {
      const label = accountsGroupLabel(e) ?? '';
      byGroup.set(label, [...(byGroup.get(label) ?? []), e]);
    }
    return [...byGroup].map(([label, inGroup]) => ({ label: label || null, items: inGroup }));
  }
  if (service !== 'employee') return [{ label: null, items }];
  return EMPLOYEE_KIND_ORDER.flatMap((kind) => {
    const inKind = items.filter((e) => employeeReportKind(e) === kind);
    return inKind.length ? [{ label: EMPLOYEE_KIND_LABEL[kind], items: inKind }] : [];
  });
}

type SegmentValue = 'all' | ReportService;

const SEGMENT_OPTIONS: { label: string; value: SegmentValue }[] = [
  { label: 'All', value: 'all' },
  ...SERVICE_ORDER.map((s) => ({ label: SERVICE_LABEL[s], value: s })),
];

export function CatalogPage() {
  const catalogQuery = useQuery({
    queryKey: ['reports', 'catalog'],
    queryFn: ({ signal }) => fetchReportCatalog({ signal }),
    // Catalog rarely changes (driven by codebase, not data) — keep it
    // warm so navigating back from /reports/jobs is instant.
    staleTime: 5 * 60 * 1000,
  });

  // Account reports come from payroll-service's own registry.
  const ability = useContext(AbilityContext);
  const canRunAccounts = ability.can('run', 'payroll.reports');
  const accountsQuery = useAccountsCatalog(canRunAccounts);

  const [activeService, setActiveService] = useState<SegmentValue>('all');
  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounced(searchInput.trim(), 200);
  const [runEntry, setRunEntry] = useState<ReportCatalogEntry | null>(null);
  const [accountsEntry, setAccountsEntry] = useState<AccountsCatalogEntry | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  // Kept after close so the drawer can animate out; `pdfOpen` toggles it.
  const [pdf, setPdf] = useState<PdfPreview | null>(null);
  const [pdfOpen, setPdfOpen] = useState(false);
  const openPdf = (next: PdfPreview) => {
    setPdf(next);
    setPdfOpen(true);
  };
  const { message } = AntdApp.useApp();

  async function run(entry: ReportCatalogEntry) {
    if (isAccountsEntry(entry)) {
      setAccountsEntry(entry);
      return;
    }
    if (entry.mode !== 'sync' || entry.parameters.length > 0) {
      setRunEntry(entry);
      return;
    }
    if (entry.format === 'pdf') {
      openPdf({ entry, load: (signal) => fetchSyncReport(entry, {}, { signal }) });
      return;
    }
    if (downloadingId) return;
    setDownloadingId(entry.id);
    try {
      await runSyncReport(entry, {});
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setDownloadingId(null);
    }
  }

  const filtered = useMemo(() => {
    const all = [...(catalogQuery.data ?? []), ...(accountsQuery.data ?? [])];
    const needle = debouncedSearch.toLowerCase();
    return all
      .filter((e) => activeService === 'all' || e.service === activeService)
      .filter(
        (e) =>
          !needle ||
          e.title.toLowerCase().includes(needle) ||
          e.description.toLowerCase().includes(needle),
      );
  }, [catalogQuery.data, accountsQuery.data, activeService, debouncedSearch]);

  const grouped = useMemo<
    Array<{ service: ReportService; items: ReportCatalogEntry[] }>
  >(() => {
    if (filtered.length === 0) return [];
    const map = new Map<ReportService, ReportCatalogEntry[]>();
    for (const e of filtered) {
      const list = map.get(e.service) ?? [];
      list.push(e);
      map.set(e.service, list);
    }
    return SERVICE_ORDER.flatMap((s) =>
      map.has(s) ? [{ service: s, items: map.get(s)! }] : [],
    );
  }, [filtered]);

  const filterActive = activeService !== 'all' || debouncedSearch.length > 0;

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Insights"
        title="Reports"
        description="Generate and download reports across people, time, and pay. Some reports are direct downloads; longer ones run in the background."
        actions={
          <Space>
            <Link to="/reports/jobs">
              <Button icon={<History size={14} strokeWidth={1.75} />}>
                Job history
              </Button>
            </Link>
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              loading={catalogQuery.isFetching || accountsQuery.isFetching}
              onClick={() => {
                void catalogQuery.refetch();
                if (canRunAccounts) void accountsQuery.refetch();
              }}
              aria-label="Refresh catalog"
            />
          </Space>
        }
      />

      <FilterBar
        search={{
          value: searchInput,
          onChange: setSearchInput,
          placeholder: 'Search reports…',
        }}
        filters={
          <Segmented
            options={SEGMENT_OPTIONS}
            value={activeService}
            onChange={(v) => setActiveService(v as SegmentValue)}
          />
        }
        onReset={
          filterActive
            ? () => {
                setActiveService('all');
                setSearchInput('');
              }
            : undefined
        }
      />

      <RequestState
        loading={catalogQuery.isLoading}
        error={catalogQuery.error}
        empty={!catalogQuery.isLoading && filtered.length === 0}
        emptyTitle="No reports available"
        emptyDescription="Your account doesn't have permission to run any reports yet — speak with HR or the workspace admin."
        filterActive={filterActive}
        onClearFilters={() => {
          setActiveService('all');
          setSearchInput('');
        }}
        errorTitle={forbiddenOr(
          catalogQuery.error,
          "You don't have permission to view reports",
          "Couldn't load reports",
        )}
        onRetry={() => catalogQuery.refetch()}
      >
        <Space orientation="vertical" size={20} style={{ width: '100%' }}>
          {grouped.map((g) => (
            <Section
              key={g.service}
              title={SERVICE_LABEL[g.service]}
              subtitle={`${g.items.length} report${g.items.length === 1 ? '' : 's'}`}
            >
              <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                {subGroups(g.service, g.items).map((sub) => (
                  <div key={sub.label ?? 'all'}>
                    {sub.label ? (
                      <Text
                        type="secondary"
                        strong
                        style={{ display: 'block', marginBottom: 8, fontSize: 13 }}
                      >
                        {sub.label}
                      </Text>
                    ) : null}
                    <Row gutter={[16, 16]}>
                      {sub.items.map((entry) => (
                        <Col xs={24} md={12} lg={8} key={entry.id}>
                          <ReportCatalogCard
                            entry={entry}
                            running={downloadingId === entry.id}
                            onRun={() => void run(entry)}
                          />
                        </Col>
                      ))}
                    </Row>
                  </div>
                ))}
              </Space>
            </Section>
          ))}
        </Space>
      </RequestState>

      <RunReportDrawer
        entry={runEntry}
        open={!!runEntry}
        onClose={() => setRunEntry(null)}
        onPdf={(entry, file) => openPdf({ entry, load: () => Promise.resolve(file) })}
      />

      <AccountsRunDrawer
        entry={accountsEntry}
        open={!!accountsEntry}
        onClose={() => setAccountsEntry(null)}
        onPdf={(entry, file) => openPdf({ entry, load: () => Promise.resolve(file) })}
      />

      <PdfPreviewDrawer
        open={pdfOpen}
        title={pdf?.entry.title ?? 'Report'}
        load={(signal) =>
          pdf ? pdf.load(signal) : Promise.reject(new Error('No report selected'))
        }
        onClose={() => setPdfOpen(false)}
      />
    </Space>
  );
}
