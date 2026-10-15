// /admin/audit — explorer.
//
// Search the append-only audit log by free-text message + filters. Filter
// dropdowns are populated from `/audit/facets` so they reflect distinct
// values the service has actually seen — no hardcoded enums. Selecting a
// row opens a drawer hydrated from `/audit/events/{id}` so redaction
// policy stays in one place. The drawer's "View by correlation" re-issues
// the search with `correlationId` set.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  App as AntdApp,
  Button,
  Dropdown,
  Empty,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { Bookmark, RefreshCcw, Save, Search, Trash2, X } from 'lucide-react';

import { auditApi, type AuditEventDto } from '@/audit';
import {
  AuditFilters,
  EventDetailDrawer,
  auditColumns,
  deleteFilter,
  deserializeFilters,
  isFiltersDirty,
  listSavedFilters,
  saveFilter,
  visibilityToParam,
  type FiltersState,
  type SavedFilter,
  type VisibilityFilter,
} from '@/audit/explorer';
import { RequestState, Section } from '@/components';
import { forbiddenOr } from '@/lib';

const { Text } = Typography;

const VISIBILITY_VALUES: VisibilityFilter[] = ['ALL', 'USER_VISIBLE', 'INTERNAL'];

type SearchSearch = {
  q?: string;
  entityType?: string;
  action?: string;
  sourceService?: string;
  visibility?: VisibilityFilter;
  /** ISO timestamp; paired with `to`. */
  from?: string;
  to?: string;
  page?: number;
  pageSize?: number;
  correlationId?: string;
  actorUserId?: number;
};

function readString(s: Record<string, unknown>, key: string): string | undefined {
  const v = s[key];
  return typeof v === 'string' && v.length > 0 ? v : undefined;
}

function readNumber(s: Record<string, unknown>, key: string): number | undefined {
  const v = s[key];
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && /^\d+$/.test(v)) return Number(v);
  return undefined;
}

export const Route = createFileRoute('/_app/admin/audit/')({
  component: AuditExplorer,
  validateSearch: (search: Record<string, unknown>): SearchSearch => ({
    q: readString(search, 'q'),
    entityType: readString(search, 'entityType'),
    action: readString(search, 'action'),
    sourceService: readString(search, 'sourceService'),
    visibility: VISIBILITY_VALUES.includes(search.visibility as VisibilityFilter)
      ? (search.visibility as VisibilityFilter)
      : undefined,
    from: readString(search, 'from'),
    to: readString(search, 'to'),
    page: readNumber(search, 'page'),
    pageSize: readNumber(search, 'pageSize'),
    correlationId: readString(search, 'correlationId'),
    actorUserId: readNumber(search, 'actorUserId'),
  }),
});

function AuditExplorer() {
  const navSearch = Route.useSearch();
  const navigate = Route.useNavigate();
  const { message } = AntdApp.useApp();

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [saved, setSaved] = useState<SavedFilter[]>(() => listSavedFilters());
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveName, setSaveName] = useState('');

  // Filters live in the URL — survive hard refresh, deep-linkable.
  const filters: FiltersState = {
    q: navSearch.q ?? '',
    entityType: navSearch.entityType,
    action: navSearch.action,
    sourceService: navSearch.sourceService,
    visibility: navSearch.visibility ?? 'ALL',
    range:
      navSearch.from && navSearch.to
        ? [dayjs(navSearch.from), dayjs(navSearch.to)]
        : null,
  };
  const page = navSearch.page ?? 1;
  const pageSize = navSearch.pageSize ?? 25;

  const correlationId = navSearch.correlationId;
  const actorUserId = navSearch.actorUserId;

  const facetsQuery = useQuery({
    queryKey: ['admin', 'audit', 'facets'],
    queryFn: ({ signal }) => auditApi.facets(signal),
    staleTime: 5 * 60_000,
  });

  const searchQuery = useQuery({
    queryKey: [
      'admin',
      'audit',
      'search',
      { ...filters, correlationId, actorUserId, page, pageSize },
    ],
    queryFn: ({ signal }) =>
      auditApi.search(
        {
          q: filters.q || undefined,
          entityType: filters.entityType,
          action: filters.action,
          correlationId,
          actorUserId,
          userVisible: visibilityToParam(filters.visibility),
          from: filters.range?.[0]?.toISOString(),
          to: filters.range?.[1]?.toISOString(),
          page: page - 1,
          size: pageSize,
          sort: 'occurredAt,desc',
        },
        signal,
      ),
    placeholderData: (prev) => prev,
  });

  const facets = facetsQuery.data;
  const entityOptions = (facets?.entityTypes ?? []).map((f) => ({
    value: f.value,
    label: f.value,
    count: f.count,
  }));
  const actionOptions = (facets?.actions ?? []).map((f) => ({
    value: f.value,
    label: f.value,
    count: f.count,
  }));
  const serviceOptions = (facets?.sourceServices ?? []).map((f) => ({
    value: f.value,
    label: f.value,
    count: f.count,
  }));

  const updateFilters = (partial: Partial<FiltersState>) => {
    void navigate({
      search: (prev) => ({
        ...prev,
        ...('q' in partial ? { q: partial.q || undefined } : null),
        ...('entityType' in partial ? { entityType: partial.entityType } : null),
        ...('action' in partial ? { action: partial.action } : null),
        ...('sourceService' in partial
          ? { sourceService: partial.sourceService }
          : null),
        ...('visibility' in partial
          ? {
              visibility:
                partial.visibility && partial.visibility !== 'ALL'
                  ? partial.visibility
                  : undefined,
            }
          : null),
        ...('range' in partial
          ? {
              from: partial.range?.[0]?.toISOString(),
              to: partial.range?.[1]?.toISOString(),
            }
          : null),
        page: undefined,
      }),
      replace: true,
    });
  };

  const setFiltersFromSaved = (next: FiltersState) => {
    void navigate({
      search: (prev) => ({
        ...prev,
        q: next.q || undefined,
        entityType: next.entityType,
        action: next.action,
        sourceService: next.sourceService,
        visibility: next.visibility !== 'ALL' ? next.visibility : undefined,
        from: next.range?.[0]?.toISOString(),
        to: next.range?.[1]?.toISOString(),
        page: undefined,
      }),
      replace: true,
    });
  };

  const reset = () =>
    void navigate({ search: () => ({}), replace: true });

  const setPage = (next: number) => {
    void navigate({
      search: (prev) => ({ ...prev, page: next === 1 ? undefined : next }),
      replace: true,
    });
  };

  const setPageSize = (next: number) => {
    void navigate({
      search: (prev) => ({
        ...prev,
        pageSize: next === 25 ? undefined : next,
        page: undefined,
      }),
      replace: true,
    });
  };

  const clearCorrelation = () =>
    void navigate({
      search: (prev) => ({ ...prev, correlationId: undefined }),
      replace: true,
    });
  const clearActor = () =>
    void navigate({
      search: (prev) => ({ ...prev, actorUserId: undefined }),
      replace: true,
    });

  const hasDeepLinkChips = correlationId || typeof actorUserId === 'number';

  return (
    <Section
      title={
        <Space>
          <Search size={16} strokeWidth={1.75} />
          Explorer
        </Space>
      }
      subtitle="Search by message, then narrow with filters or a date range."
      actions={
        <Space>
          <Button onClick={reset}>Reset</Button>
          <Dropdown
            disabled={saved.length === 0}
            trigger={['click']}
            menu={{
              items: saved.map((s) => ({
                key: s.name,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'space-between', minWidth: 200 }}>
                    <span style={{ fontWeight: 500 }}>{s.name}</span>
                    <Trash2
                      size={13}
                      strokeWidth={1.75}
                      onClick={(e) => {
                        e.stopPropagation();
                        const next = deleteFilter(s.name);
                        setSaved(next);
                        message.success(`Removed "${s.name}"`);
                      }}
                      style={{ marginLeft: 12, cursor: 'pointer', opacity: 0.6 }}
                    />
                  </span>
                ),
                onClick: () => {
                  setFiltersFromSaved(deserializeFilters(s.filters));
                  message.success(`Loaded "${s.name}"`);
                },
              })),
            }}
          >
            <Button icon={<Bookmark size={14} strokeWidth={1.75} />}>
              Saved {saved.length > 0 ? `(${saved.length})` : ''}
            </Button>
          </Dropdown>
          <Button
            icon={<Save size={14} strokeWidth={1.75} />}
            disabled={!isFiltersDirty(filters)}
            onClick={() => {
              setSaveName('');
              setSaveOpen(true);
            }}
          >
            Save
          </Button>
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => searchQuery.refetch()}
              loading={searchQuery.isFetching && !searchQuery.isLoading}
              aria-label="Refresh"
            />
          </Tooltip>
        </Space>
      }
    >
      <div style={{ marginBottom: hasDeepLinkChips ? 8 : 16 }}>
        <AuditFilters
          filters={filters}
          onChange={updateFilters}
          facetsLoading={facetsQuery.isLoading}
          entityOptions={entityOptions}
          actionOptions={actionOptions}
          serviceOptions={serviceOptions}
        />
      </div>

      {hasDeepLinkChips && (
        <div
          style={{
            display: 'flex',
            gap: 8,
            flexWrap: 'wrap',
            marginBottom: 16,
            alignItems: 'center',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            Filtering by:
          </Text>
          {correlationId && (
            <Tag
              closable
              closeIcon={<X size={11} />}
              onClose={(e) => {
                e.preventDefault();
                clearCorrelation();
              }}
              style={{ margin: 0 }}
            >
              correlation <code>{correlationId.slice(0, 8)}…</code>
            </Tag>
          )}
          {typeof actorUserId === 'number' && (
            <Tag
              closable
              closeIcon={<X size={11} />}
              onClose={(e) => {
                e.preventDefault();
                clearActor();
              }}
              style={{ margin: 0 }}
            >
              actor #{actorUserId}
            </Tag>
          )}
        </div>
      )}

      <RequestState
        loading={false}
        error={searchQuery.isError ? searchQuery.error : undefined}
        errorTitle={forbiddenOr(
          searchQuery.error,
          "You don't have permission to read audit events",
          "Couldn't search audit events",
        )}
      >
        <Table<AuditEventDto>
          rowKey={(r) => r.id}
          size="middle"
          columns={auditColumns}
          dataSource={searchQuery.data?.content ?? []}
          loading={searchQuery.isLoading}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  filters.q ||
                  filters.entityType ||
                  filters.action ||
                  correlationId ||
                  actorUserId
                    ? 'No matches.'
                    : 'Start typing to filter.'
                }
              />
            ),
          }}
          pagination={{
            current: page,
            pageSize,
            total: searchQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 25, 50, 100],
            showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
          onRow={(record) => ({
            onClick: () => setSelectedId(record.id),
            style: { cursor: 'pointer' },
          })}
          scroll={{ x: 1100 }}
        />
      </RequestState>

      <EventDetailDrawer
        eventId={selectedId}
        onClose={() => setSelectedId(null)}
        onViewByCorrelation={(corr) => {
          setSelectedId(null);
          void navigate({
            search: () => ({ correlationId: corr }),
            replace: false,
          });
        }}
      />

      <Modal
        open={saveOpen}
        title="Save filter set"
        okText="Save"
        onOk={() => {
          if (saveName.trim().length === 0) return;
          const next = saveFilter(saveName, filters);
          setSaved(next);
          setSaveOpen(false);
          message.success(`Saved "${saveName.trim()}"`);
        }}
        okButtonProps={{ disabled: saveName.trim().length === 0 }}
        onCancel={() => setSaveOpen(false)}
        destroyOnHidden
        mask={{ closable: false }}
      >
        <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
          Names are unique — saving over an existing name overwrites it. Up to 50 sets per browser.
        </Typography.Paragraph>
        <Input
          value={saveName}
          onChange={(e) => setSaveName(e.target.value)}
          placeholder='e.g. "Denied logins last 24h"'
          autoFocus
          maxLength={80}
        />
      </Modal>
    </Section>
  );
}
