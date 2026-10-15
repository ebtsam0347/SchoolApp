// Shared catalog-CRUD shell. Each /admin/lookups/* page composes this
// with a small config object: list query, create / update mutations,
// columns, and form-field rendering. The shell handles header + table +
// drawer + status pill + active-filter toggle + RFC7807 error mapping.
// Catalogs that carry a displayOrder opt into drag-to-reorder via the
// `reorder` config slot.

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
  type HTMLAttributes,
  type ReactNode,
} from 'react';
import {
  Alert,
  App,
  Button,
  Drawer,
  Form,
  Input,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type FormInstance,
  type TableProps,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GripVertical, Plus, RefreshCcw, Search as SearchIcon } from 'lucide-react';
import {
  DndContext,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DraggableSyntheticListeners,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

import { PageHeader } from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
  useDebounced,
} from '@/lib';

type ReorderEntry = { id: number; displayOrder: number };

type RowContextValue = {
  setActivatorNodeRef?: (el: HTMLElement | null) => void;
  listeners?: DraggableSyntheticListeners;
};
const RowContext = createContext<RowContextValue>({});

function SortableRow(props: HTMLAttributes<HTMLTableRowElement> & { 'data-row-key'?: string }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: props['data-row-key'] ?? '' });

  const style: CSSProperties = {
    ...props.style,
    transform: CSS.Translate.toString(transform),
    transition,
    ...(isDragging ? { position: 'relative', zIndex: 9999 } : {}),
  };
  const contextValue = useMemo<RowContextValue>(
    () => ({ setActivatorNodeRef, listeners }),
    [setActivatorNodeRef, listeners],
  );
  return (
    <RowContext.Provider value={contextValue}>
      <tr {...props} ref={setNodeRef} style={style} {...attributes} />
    </RowContext.Provider>
  );
}

function DragHandleCell({ disabled }: { disabled?: boolean }) {
  const { setActivatorNodeRef, listeners } = useContext(RowContext);
  return (
    <Button
      type="text"
      size="small"
      ref={setActivatorNodeRef}
      icon={<GripVertical size={14} strokeWidth={1.75} />}
      disabled={disabled}
      aria-label="Drag to reorder"
      {...(disabled ? {} : listeners)}
      style={{ cursor: disabled ? 'not-allowed' : 'grab', padding: '0 4px' }}
    />
  );
}

const { Text } = Typography;

export type CatalogShellConfig<T extends { id?: number; active?: boolean; displayOrder?: number }, V> = {
  title: ReactNode;
  description?: ReactNode;
  /** Subject for CASL gating of writes ('employee' for everything in v1). */
  writeSubject?: string;
  /** React-Query queryKey for the list. */
  queryKey: readonly unknown[];
  /** Loader for the list. */
  loadList: (signal: AbortSignal) => Promise<T[]>;
  /** Columns shown in the catalog table (the active toggle + actions are appended). */
  columns: TableProps<T>['columns'];
  /** Render the body of the create/edit drawer. Receives the AntD form instance + edit target. */
  renderForm: (form: FormInstance<V>, editing: T | null) => ReactNode;
  /** Convert form values → create body. */
  toCreate: (values: V) => Promise<unknown> | unknown;
  /** Convert form values → patch body. */
  toUpdate: (values: V, editing: T) => Promise<unknown> | unknown;
  /** Soft-deactivation patch. */
  setActive?: (row: T, active: boolean) => Promise<unknown> | unknown;
  /**
   * Override the default search predicate. The shell searches `code` + `name`
   * out of the box; opt in here when a catalog has a richer searchable surface
   * (e.g. cities want to match province code too).
   */
  searchMatch?: (row: T, query: string) => boolean;
  /** Placeholder for the search input. Defaults to "Search code or name…". */
  searchPlaceholder?: string;
  /**
   * Extra filter controls rendered to the right of search. Catalog-specific
   * (e.g. a Department dropdown for Sections). Pair with `extraFilter` to
   * apply the predicate. The caller owns the state.
   */
  extraFilters?: ReactNode;
  /** Predicate combined with search + active filtering. Return true to keep. */
  extraFilter?: (row: T) => boolean;
  /**
   * Opt-in drag-to-reorder. Requires T to carry `displayOrder`.
   * The shell sorts rows by displayOrder, renders a drag handle column,
   * and on drop calls `onReorder` with only the rows whose order changed.
   * Drag is disabled while search or extraFilter narrow the visible set.
   */
  reorder?: {
    onReorder: (entries: ReorderEntry[]) => Promise<unknown> | unknown;
  };
  /**
   * Label used in the edit-drawer title ("Edit {label}"). Defaults to
   * `row.name ?? row.code` — override when the catalog's identifying field
   * is named something else (e.g. Belongings' `belongingName`).
   */
  rowLabel?: (row: T) => ReactNode;
};

export function CatalogShell<
  T extends { id?: number; active?: boolean; code?: string; name?: string; displayOrder?: number },
  V,
>({
  config,
}: {
  config: CatalogShellConfig<T, V>;
}) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [showInactive, setShowInactive] = useState<'active' | 'all'>('active');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<T | null>(null);
  const [topLevel, setTopLevel] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounced(searchInput.trim().toLowerCase(), 200);
  const [form] = Form.useForm<V>();

  const listQuery = useQuery({
    queryKey: config.queryKey,
    queryFn: ({ signal }) => config.loadList(signal),
  });

  useEffect(() => {
    if (drawerOpen) {
      form.resetFields();
      setTopLevel(null);
    }
  }, [drawerOpen, editing, form]);

  const writeMutation = useMutation({
    mutationFn: async (values: V) => {
      if (editing) return config.toUpdate(values, editing);
      return config.toCreate(values);
    },
    onSuccess: () => {
      message.success(editing ? 'Updated.' : 'Created.');
      void qc.invalidateQueries({ queryKey: config.queryKey });
      setDrawerOpen(false);
      setEditing(null);
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  const setActiveMutation = useMutation({
    mutationFn: async (args: { row: T; active: boolean }) =>
      config.setActive?.(args.row, args.active),
    onSuccess: () => {
      message.success('Updated.');
      void qc.invalidateQueries({ queryKey: config.queryKey });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  // Two-stage filter: active toggle, then search. Search defaults to
  // matching code/name (case-insensitive) and folds in any string-typed
  // dataIndex column when the row carries a value there — so e.g. cities
  // pick up province codes without per-page wiring. Callers can still
  // override via `searchMatch`.
  const stringColumnKeys = useMemo(() => {
    const out: string[] = [];
    for (const c of config.columns ?? []) {
      const di = (c as { dataIndex?: unknown }).dataIndex;
      if (typeof di === 'string') out.push(di);
    }
    return out;
  }, [config.columns]);

  const matchesSearch = (row: T): boolean => {
    if (!debouncedSearch) return true;
    if (config.searchMatch) return config.searchMatch(row, debouncedSearch);
    const code = row.code?.toLowerCase() ?? '';
    const name = row.name?.toLowerCase() ?? '';
    if (code.includes(debouncedSearch) || name.includes(debouncedSearch)) return true;
    for (const key of stringColumnKeys) {
      const v = (row as unknown as Record<string, unknown>)[key];
      if (typeof v === 'string' && v.toLowerCase().includes(debouncedSearch)) return true;
    }
    return false;
  };

  // When reorder is enabled, sort everything by displayOrder so the
  // visible subset retains the canonical order even with the active/all
  // toggle. Ties break by id to keep the order stable across renders.
  const sorted = useMemo(() => {
    const data = listQuery.data ?? [];
    if (!config.reorder) return data;
    return [...data].sort((a, b) => {
      const ao = a.displayOrder ?? Number.MAX_SAFE_INTEGER;
      const bo = b.displayOrder ?? Number.MAX_SAFE_INTEGER;
      if (ao !== bo) return ao - bo;
      return (a.id ?? 0) - (b.id ?? 0);
    });
  }, [listQuery.data, config.reorder]);

  const visible = sorted.filter(
    (r) => showInactive === 'all' || r.active !== false,
  );
  const afterExtra = config.extraFilter ? visible.filter(config.extraFilter) : visible;
  const rows = afterExtra.filter(matchesSearch);
  const filteredOutBySearch = afterExtra.length - rows.length;
  const isFilterNarrowing =
    !!debouncedSearch || (!!config.extraFilter && afterExtra.length !== visible.length);
  const canDrag = !!config.reorder && !isFilterNarrowing;

  const reorderMutation = useMutation({
    mutationFn: async (entries: ReorderEntry[]) =>
      config.reorder?.onReorder(entries),
    onMutate: async (entries) => {
      await qc.cancelQueries({ queryKey: config.queryKey });
      const previous = qc.getQueryData<T[]>(config.queryKey);
      if (previous) {
        const patch = new Map(entries.map((e) => [e.id, e.displayOrder]));
        qc.setQueryData<T[]>(
          config.queryKey,
          previous.map((row) =>
            row.id != null && patch.has(row.id)
              ? { ...row, displayOrder: patch.get(row.id) }
              : row,
          ),
        );
      }
      return { previous };
    },
    onError: (e, _entries, ctx) => {
      if (ctx?.previous) qc.setQueryData(config.queryKey, ctx.previous);
      message.error(getErrorMessage(e));
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: config.queryKey });
    },
  });

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const activeId = Number(active.id);
    const overId = Number(over.id);

    // Operate on the FULL sorted list so reordering remains consistent
    // even when the active-only toggle is hiding inactive rows.
    const full = sorted;
    const fromIdx = full.findIndex((r) => r.id === activeId);
    const toIdx = full.findIndex((r) => r.id === overId);
    if (fromIdx === -1 || toIdx === -1) return;
    const moved = arrayMove(full, fromIdx, toIdx);

    // Renumber contiguously from 1; emit only rows whose order changed.
    const entries: ReorderEntry[] = [];
    moved.forEach((row, idx) => {
      const next = idx + 1;
      if (row.id != null && row.displayOrder !== next) {
        entries.push({ id: row.id, displayOrder: next });
      }
    });
    if (entries.length === 0) return;
    reorderMutation.mutate(entries);
  };

  const augmentedColumns: TableProps<T>['columns'] = [
    ...(config.reorder
      ? ([
          {
            title: '',
            key: '__drag',
            width: 44,
            render: () => <DragHandleCell disabled={!canDrag} />,
          },
        ] as NonNullable<TableProps<T>['columns']>)
      : []),
    ...(config.columns ?? []),
    {
      title: 'Status',
      key: '__status',
      width: 100,
      render: (_v, row) =>
        row.active === false ? (
          <Tag color="default">Inactive</Tag>
        ) : (
          <Tag color="success">Active</Tag>
        ),
    },
    {
      title: '',
      key: '__actions',
      width: 200,
      render: (_v, row) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setEditing(row);
              setDrawerOpen(true);
            }}
          >
            Edit
          </Button>
          {config.setActive ? (
            <Tooltip
              title={
                row.active === false
                  ? 'Reactivate — new assignments allowed.'
                  : 'Soft-deactivate — existing rows keep the reference, new assignments rejected.'
              }
            >
              <Button
                size="small"
                type={row.active === false ? 'link' : 'default'}
                danger={row.active !== false}
                loading={
                  setActiveMutation.isPending &&
                  setActiveMutation.variables?.row.id === row.id
                }
                disabled={
                  setActiveMutation.isPending &&
                  setActiveMutation.variables?.row.id !== row.id
                }
                onClick={() =>
                  setActiveMutation.mutate({ row, active: row.active === false })
                }
              >
                {row.active === false ? 'Reactivate' : 'Deactivate'}
              </Button>
            </Tooltip>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="Lookups"
        title={config.title}
        description={config.description}
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              type="primary"
              icon={<Plus size={16} strokeWidth={1.75} />}
              onClick={() => {
                setEditing(null);
                setDrawerOpen(true);
              }}
            >
              New
            </Button>
          </Space>
        }
      />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 'var(--app-space-md)',
          rowGap: 'var(--app-space-sm)',
        }}
      >
        <Input
          allowClear
          prefix={<SearchIcon size={14} strokeWidth={1.75} />}
          placeholder={config.searchPlaceholder ?? 'Search code or name…'}
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          style={{ width: 280 }}
          aria-label="Search"
        />
        <Space size="small" wrap>
          <Text type="secondary" style={{ fontSize: 13 }}>Show</Text>
          <Segmented<'active' | 'all'>
            value={showInactive}
            onChange={(v) => setShowInactive(v)}
            options={[
              { value: 'active', label: 'Active only' },
              { value: 'all', label: 'All' },
            ]}
            size="small"
          />
        </Space>
        {config.extraFilters}
        <Text type="secondary" style={{ fontSize: 13 }}>
          {rows.length.toLocaleString('en-PK')} {rows.length === 1 ? 'row' : 'rows'}
          {debouncedSearch && filteredOutBySearch > 0
            ? ` · ${filteredOutBySearch.toLocaleString('en-PK')} hidden by search`
            : ''}
        </Text>
      </div>

      {listQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load list"
          description={getErrorMessage(listQuery.error)}
        />
      ) : null}

      {config.reorder && isFilterNarrowing ? (
        <Alert
          type="info"
          showIcon
          message="Clear the search and filters to drag rows into a new order."
          style={{ marginBottom: 0 }}
        />
      ) : null}

      {config.reorder ? (
        <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
          <SortableContext
            items={rows.map((r) => String(r.id ?? ''))}
            strategy={verticalListSortingStrategy}
          >
            <Table<T>
              rowKey={(row) => String(row.id ?? Math.random())}
              loading={listQuery.isLoading}
              dataSource={rows}
              columns={augmentedColumns}
              size="middle"
              pagination={false}
              scroll={{ x: 'max-content' }}
              components={{ body: { row: SortableRow } }}
            />
          </SortableContext>
        </DndContext>
      ) : (
        <Table<T>
          rowKey={(row) => String(row.id ?? Math.random())}
          loading={listQuery.isLoading}
          dataSource={rows}
          columns={augmentedColumns}
          size="middle"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 'max-content' }}
        />
      )}

      <Drawer
        open={drawerOpen}
        title={
          editing
            ? `Edit ${config.rowLabel ? config.rowLabel(editing) : (editing.name ?? editing.code ?? '')}`
            : 'New'
        }
        size={420}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        extra={
          <Button
            type="primary"
            loading={writeMutation.isPending}
            onClick={() => form.submit()}
          >
            {editing ? 'Save' : 'Create'}
          </Button>
        }
        destroyOnHidden
      >
        {topLevel ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 12 }}
            title={topLevel}
            closable
            onClose={() => setTopLevel(null)}
          />
        ) : null}
        <Form
          form={form}
          layout="vertical"
          initialValues={(editing ?? undefined) as unknown as Record<string, unknown>}
          onFinish={(values) => writeMutation.mutate(values)}
        >
          {config.renderForm(form, editing)}
        </Form>
      </Drawer>
    </div>
  );
}
