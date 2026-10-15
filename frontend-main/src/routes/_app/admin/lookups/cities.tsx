// /admin/lookups/cities — Cities & Provinces (master-detail).
//
// Provinces sit on the left as a clickable list; the selected province
// drives the cities query (`GET /cities?provinceId=N`) so we never pull
// the full national catalog at once. Punjab is the default. Provinces
// rarely change — their CRUD lives behind a "Manage provinces" drawer.

import { useEffect, useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Input,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
  type TableColumnsType,
} from 'antd';
import { Edit3, Map as MapIcon, MapPin, Plus, RefreshCcw, Search as SearchIcon, Settings2 } from 'lucide-react';

import {
  DrawerForm,
  EmptyState,
  PageHeader,
  RequestState,
  StatusPill,
} from '@/components';
import { handleFormError, useDebounced } from '@/lib';
import {
  catalogsApi,
  type CityDto,
  type ProvinceDto,
} from '@/people';

const { useToken } = antdTheme;
const { Text } = Typography;

const DEFAULT_PROVINCE_CODE = 'PB';
const PROVINCES_KEY = ['catalog', 'provinces-all'] as const;

type CityForm = { code: string; name: string };

export const Route = createFileRoute('/_app/admin/lookups/cities')({
  validateSearch: (search): { province?: string } => ({
    province:
      typeof search.province === 'string' && search.province
        ? search.province.toUpperCase()
        : undefined,
  }),
  component: CitiesAndProvinces,
});

function CitiesAndProvinces() {
  const { token } = useToken();
  const { province: provinceParam } = Route.useSearch();
  const navigate = Route.useNavigate();

  const provincesQuery = useQuery({
    queryKey: PROVINCES_KEY,
    queryFn: ({ signal }) => catalogsApi.provinces(signal),
  });

  const provinces = useMemo(
    () => (provincesQuery.data ?? []).filter((p): p is ProvinceDto & { id: number } => p.id != null),
    [provincesQuery.data],
  );
  const activeProvinces = useMemo(
    () => provinces.filter((p) => p.active !== false),
    [provinces],
  );

  // Resolve the selected province: URL param wins, else PB, else first
  // active. Once we know what to land on, sync it back to the URL so
  // refresh / share-link stays sticky.
  const resolvedProvince = useMemo<(ProvinceDto & { id: number }) | undefined>(() => {
    if (provinces.length === 0) return undefined;
    if (provinceParam) {
      const hit = provinces.find((p) => p.code === provinceParam);
      if (hit) return hit;
    }
    const pb = activeProvinces.find((p) => p.code === DEFAULT_PROVINCE_CODE);
    if (pb) return pb;
    return activeProvinces[0] ?? provinces[0];
  }, [provinces, activeProvinces, provinceParam]);

  useEffect(() => {
    if (resolvedProvince && resolvedProvince.code !== provinceParam) {
      void navigate({
        to: '/admin/lookups/cities',
        search: { province: resolvedProvince.code },
        replace: true,
      });
    }
  }, [resolvedProvince, provinceParam, navigate]);

  const [provinceManagerOpen, setProvinceManagerOpen] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="Lookups"
        title="Cities & Provinces"
        description="Pick a province on the left to see and manage its cities. Provinces rarely change — open Manage provinces for the seven Pakistan regions."
      />

      {provincesQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load provinces"
          description="Refresh, or check that the catalog service is reachable."
        />
      ) : null}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(240px, 280px) minmax(0, 1fr)',
          gap: 20,
          alignItems: 'start',
        }}
      >
        <ProvincesSidebar
          provinces={provinces}
          loading={provincesQuery.isLoading}
          activeId={resolvedProvince?.id}
          onSelect={(p) =>
            navigate({
              to: '/admin/lookups/cities',
              search: { province: p.code },
              replace: true,
            })
          }
          onManage={() => setProvinceManagerOpen(true)}
          token={token}
        />

        {resolvedProvince ? (
          <CitiesPanel key={resolvedProvince.id} province={resolvedProvince} token={token} />
        ) : !provincesQuery.isLoading ? (
          <EmptyPanel token={token} />
        ) : (
          <Placeholder token={token} />
        )}
      </div>

      <ProvinceManagerDrawer
        open={provinceManagerOpen}
        onClose={() => setProvinceManagerOpen(false)}
      />
    </div>
  );
}

/* ----------------------------- Sidebar ------------------------------- */

function ProvincesSidebar({
  provinces,
  loading,
  activeId,
  onSelect,
  onManage,
  token,
}: {
  provinces: ProvinceDto[];
  loading: boolean;
  activeId?: number;
  onSelect: (p: ProvinceDto) => void;
  onManage: () => void;
  token: ReturnType<typeof useToken>['token'];
}) {
  const visible = provinces.filter((p) => p.active !== false || p.id === activeId);

  return (
    <aside
      style={{
        display: 'flex',
        flexDirection: 'column',
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 14,
        overflow: 'hidden',
        position: 'sticky',
        top: 'calc(var(--app-topbar-height, 64px) + 16px)',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '14px 16px 10px',
        }}
      >
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <MapIcon size={14} strokeWidth={1.75} style={{ color: token.colorTextSecondary }} />
          <Text
            style={{
              fontSize: 11,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: token.colorTextSecondary,
              fontWeight: 600,
            }}
          >
            Provinces
          </Text>
        </span>
        <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {provinces.length}
        </Text>
      </header>

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          padding: '4px 8px 8px',
          minHeight: 240,
        }}
      >
        {loading && provinces.length === 0 ? (
          <SkeletonRows token={token} />
        ) : visible.length === 0 ? (
          <Text
            type="secondary"
            style={{ fontSize: 12, padding: '12px 8px' }}
          >
            No provinces yet.
          </Text>
        ) : (
          visible.map((p) => (
            <ProvinceRow
              key={p.id}
              province={p}
              active={p.id === activeId}
              onClick={() => onSelect(p)}
              token={token}
            />
          ))
        )}
      </div>

      <footer
        style={{
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          padding: '8px 8px 10px',
          background: token.colorFillQuaternary,
        }}
      >
        <Button
          type="text"
          size="small"
          block
          icon={<Settings2 size={13} strokeWidth={1.75} />}
          onClick={onManage}
          style={{ justifyContent: 'flex-start', color: token.colorTextSecondary }}
        >
          Manage provinces
        </Button>
      </footer>
    </aside>
  );
}

function ProvinceRow({
  province,
  active,
  onClick,
  token,
}: {
  province: ProvinceDto;
  active: boolean;
  onClick: () => void;
  token: ReturnType<typeof useToken>['token'];
}) {
  const inactive = province.active === false;
  const bg = active ? token.colorPrimaryBg : 'transparent';
  const fg = active ? token.colorPrimaryActive : token.colorText;

  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '9px 10px',
        background: bg,
        border: '1px solid transparent',
        borderColor: active ? token.colorPrimaryBorder : 'transparent',
        borderRadius: 8,
        cursor: 'pointer',
        textAlign: 'left',
        transition: 'background 140ms ease, border-color 140ms ease',
        marginBottom: 2,
        outline: 'none',
        opacity: inactive ? 0.55 : 1,
      }}
      onMouseEnter={(e) => {
        if (!active) e.currentTarget.style.background = token.colorFillTertiary;
      }}
      onMouseLeave={(e) => {
        if (!active) e.currentTarget.style.background = 'transparent';
      }}
      onFocus={(e) => {
        e.currentTarget.style.borderColor = token.colorPrimaryBorder;
      }}
      onBlur={(e) => {
        e.currentTarget.style.borderColor = active ? token.colorPrimaryBorder : 'transparent';
      }}
    >
      <span
        aria-hidden="true"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          borderRadius: 8,
          background: active ? token.colorPrimary : token.colorFillSecondary,
          color: active ? token.colorTextLightSolid : token.colorTextSecondary,
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.04em',
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          flexShrink: 0,
        }}
      >
        {province.code ?? '??'}
      </span>
      <span style={{ display: 'inline-flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
        <Text
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: fg,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {province.name ?? province.code}
        </Text>
        {inactive ? (
          <Text style={{ fontSize: 11, color: token.colorTextTertiary }}>Inactive</Text>
        ) : null}
      </span>
    </button>
  );
}

function SkeletonRows({ token }: { token: ReturnType<typeof useToken>['token'] }) {
  return (
    <>
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          aria-hidden="true"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '9px 10px',
            marginBottom: 2,
          }}
        >
          <span
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              background: token.colorFillSecondary,
            }}
          />
          <span
            style={{
              height: 12,
              width: '60%',
              borderRadius: 6,
              background: token.colorFillSecondary,
            }}
          />
        </div>
      ))}
    </>
  );
}

/* ----------------------------- Cities panel -------------------------- */

function CitiesPanel({
  province,
  token,
}: {
  province: ProvinceDto & { id: number };
  token: ReturnType<typeof useToken>['token'];
}) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const [searchInput, setSearchInput] = useState('');
  const debounced = useDebounced(searchInput.trim().toLowerCase(), 200);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<CityDto | null>(null);

  const citiesQuery = useQuery({
    queryKey: ['catalog', 'cities', { provinceId: province.id }],
    queryFn: ({ signal }) => catalogsApi.cities(province.id, signal),
  });

  const all = citiesQuery.data ?? [];
  const filtered = debounced
    ? all.filter((c) => {
        const hay = `${c.code ?? ''} ${c.name ?? ''}`.toLowerCase();
        return hay.includes(debounced);
      })
    : all;
  const activeCount = all.filter((c) => c.active !== false).length;

  const toggleMutation = useMutation({
    mutationFn: (args: { city: CityDto; active: boolean }) =>
      catalogsApi.updateCity(args.city.id!, { active: args.active }),
    onSuccess: (_v, vars) => {
      message.success(
        vars.active ? `Reactivated ${vars.city.name}` : `Deactivated ${vars.city.name}`,
      );
      void queryClient.invalidateQueries({
        queryKey: ['catalog', 'cities', { provinceId: province.id }],
      });
    },
    onError: () => message.error('Could not update city. Try again.'),
  });

  const cols: TableColumnsType<CityDto> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 140,
      render: (c?: string) => (
        <code style={{ fontSize: 12, fontWeight: 500 }}>{c ?? '—'}</code>
      ),
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (n?: string) => <span style={{ fontWeight: 500 }}>{n ?? '—'}</span>,
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_v, row) => (
        <StatusPill tone={row.active === false ? 'neutral' : 'success'} size="sm">
          {row.active === false ? 'Inactive' : 'Active'}
        </StatusPill>
      ),
    },
    {
      title: '',
      key: '__actions',
      align: 'right',
      width: 200,
      render: (_v, row) => (
        <Space size={4}>
          <Tooltip title="Edit">
            <Button
              size="small"
              icon={<Edit3 size={13} strokeWidth={1.75} />}
              onClick={() => setEditing(row)}
              aria-label={`Edit ${row.name ?? row.code}`}
            />
          </Tooltip>
          <Button
            size="small"
            danger={row.active !== false}
            type={row.active === false ? 'link' : 'default'}
            loading={
              toggleMutation.isPending &&
              toggleMutation.variables?.city.id === row.id
            }
            onClick={() =>
              toggleMutation.mutate({ city: row, active: row.active === false })
            }
          >
            {row.active === false ? 'Reactivate' : 'Deactivate'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <section
      style={{
        display: 'flex',
        flexDirection: 'column',
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 14,
        overflow: 'hidden',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '16px 20px',
          gap: 16,
          flexWrap: 'wrap',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <MapPin size={15} strokeWidth={1.75} style={{ color: token.colorPrimary }} />
            <Text style={{ fontSize: 16, fontWeight: 600 }}>{province.name}</Text>
            <Tag style={{ marginInlineEnd: 0, fontFamily: 'ui-monospace, monospace' }}>
              {province.code}
            </Tag>
          </span>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {citiesQuery.isLoading
              ? 'Loading…'
              : `${activeCount} active · ${all.length} total`}
          </Text>
        </span>
        <Space>
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => citiesQuery.refetch()}
              loading={citiesQuery.isFetching}
              aria-label="Refresh"
            />
          </Tooltip>
          <Button
            type="primary"
            icon={<Plus size={14} strokeWidth={1.75} />}
            onClick={() => setCreateOpen(true)}
          >
            New city
          </Button>
        </Space>
      </header>

      <div style={{ padding: '12px 20px', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
        <Input
          allowClear
          prefix={<SearchIcon size={14} strokeWidth={1.75} />}
          placeholder={`Search cities in ${province.name ?? ''}…`}
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          style={{ maxWidth: 320 }}
          aria-label="Search cities"
        />
      </div>

      <div style={{ padding: '4px 12px 16px' }}>
        <RequestState
          loading={citiesQuery.isLoading}
          error={citiesQuery.error}
          empty={!citiesQuery.isLoading && filtered.length === 0}
          errorTitle="Couldn't load cities"
          emptyFallback={
            all.length === 0 ? (
              <EmptyState
                icon={MapPin}
                title={`No cities in ${province.name ?? province.code} yet`}
                description="Add the first city so HR can use it on employee addresses."
                action={
                  <Button
                    type="primary"
                    icon={<Plus size={14} strokeWidth={1.75} />}
                    onClick={() => setCreateOpen(true)}
                  >
                    Add a city
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={SearchIcon}
                title="No matches"
                description="Try a different search term."
                action={<Button onClick={() => setSearchInput('')}>Clear search</Button>}
              />
            )
          }
        >
          <Table<CityDto>
            rowKey={(row) => String(row.id ?? Math.random())}
            dataSource={filtered}
            columns={cols}
            size="middle"
            pagination={{ pageSize: 25, hideOnSinglePage: true, showSizeChanger: true }}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            style={{ background: 'transparent' }}
          />
        </RequestState>
      </div>

      <CityDrawer
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        province={province}
      />
      <CityDrawer
        open={!!editing}
        editing={editing}
        onClose={() => setEditing(null)}
        province={province}
      />
    </section>
  );
}

/* ------------------------------ Drawers ------------------------------ */

function CityDrawer({
  open,
  onClose,
  editing,
  province,
}: {
  open: boolean;
  onClose: () => void;
  editing?: CityDto | null;
  province: ProvinceDto & { id: number };
}) {
  const [form] = Form.useForm<CityForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (open) {
      form.resetFields();
      if (editing) {
        form.setFieldsValue({
          code: editing.code ?? '',
          name: editing.name ?? '',
        });
      }
    }
  }, [open, editing, form]);

  const mutation = useMutation({
    mutationFn: (values: CityForm) => {
      if (editing) {
        return catalogsApi.updateCity(editing.id!, {
          code: values.code,
          name: values.name,
        });
      }
      return catalogsApi.createCity({
        code: values.code,
        name: values.name,
        provinceId: province.id,
      });
    },
    onSuccess: (c) => {
      message.success(editing ? `Updated ${c.name}` : `Added ${c.name}`);
      void queryClient.invalidateQueries({
        queryKey: ['catalog', 'cities', { provinceId: province.id }],
      });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<CityForm>
      open={open}
      onClose={onClose}
      title={
        editing
          ? `Edit ${editing.name ?? editing.code ?? 'city'}`
          : `New city in ${province.name ?? province.code}`
      }
      form={form}
      submitText={editing ? 'Save' : 'Create'}
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title={`Province: ${province.name ?? province.code}`}
        description="Cities are scoped to a province. Pick a different province from the sidebar to add a city there."
      />

      <Form.Item
        label="Code"
        name="code"
        rules={[
          { required: true, message: 'Pick a short code' },
          { max: 32, message: 'Max 32 characters' },
        ]}
      >
        <Input
          placeholder="SKT"
          style={{ fontFamily: 'ui-monospace, monospace', textTransform: 'uppercase' }}
          maxLength={32}
        />
      </Form.Item>

      <Form.Item
        label="Name"
        name="name"
        rules={[
          { required: true, message: 'Name the city' },
          { max: 128 },
        ]}
      >
        <Input placeholder="Sialkot" maxLength={128} />
      </Form.Item>
    </DrawerForm>
  );
}

/* ------------------- Province manager (rare CRUD) -------------------- */

function ProvinceManagerDrawer({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { token } = useToken();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const provincesQuery = useQuery({
    queryKey: PROVINCES_KEY,
    queryFn: ({ signal }) => catalogsApi.provinces(signal),
    enabled: open,
  });

  const [editing, setEditing] = useState<ProvinceDto | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      setEditing(null);
      setCreateOpen(false);
    }
  }, [open]);

  const toggleMutation = useMutation({
    mutationFn: (args: { province: ProvinceDto; active: boolean }) =>
      catalogsApi.updateProvince(args.province.id!, { active: args.active }),
    onSuccess: (_v, vars) => {
      message.success(
        vars.active ? `Reactivated ${vars.province.name}` : `Deactivated ${vars.province.name}`,
      );
      void queryClient.invalidateQueries({ queryKey: PROVINCES_KEY });
    },
    onError: () => message.error('Could not update province.'),
  });

  return (
    <>
      <DrawerForm
        open={open}
        onClose={onClose}
        title="Manage provinces"
        size={460}
        confirmCloseWhenDirty={false}
        footer={null}
      >
        <Text
          type="secondary"
          style={{ display: 'block', marginBottom: 12, fontSize: 12 }}
        >
          Pakistan ships seven regions out of the box (PB · SD · KP · BL · ICT · AJK · GB). Edit only if a region is added or renamed.
        </Text>

        <Button
          type="primary"
          icon={<Plus size={14} strokeWidth={1.75} />}
          onClick={() => setCreateOpen(true)}
          style={{ marginBottom: 16 }}
        >
          New province
        </Button>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          {(provincesQuery.data ?? []).map((p, idx, arr) => (
            <div
              key={p.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                padding: '10px 12px',
                borderBottom:
                  idx === arr.length - 1 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
                background: p.active === false ? token.colorFillQuaternary : token.colorBgContainer,
              }}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                <Tag style={{ marginInlineEnd: 0, fontFamily: 'ui-monospace, monospace' }}>
                  {p.code}
                </Tag>
                <Text style={{ fontWeight: 500 }}>{p.name}</Text>
                {p.active === false ? (
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    Inactive
                  </Text>
                ) : null}
              </span>
              <Space size={4}>
                <Tooltip title="Rename / edit">
                  <Button
                    size="small"
                    icon={<Edit3 size={13} strokeWidth={1.75} />}
                    onClick={() => setEditing(p)}
                    aria-label={`Edit ${p.name}`}
                  />
                </Tooltip>
                <Button
                  size="small"
                  danger={p.active !== false}
                  type={p.active === false ? 'link' : 'default'}
                  loading={
                    toggleMutation.isPending &&
                    toggleMutation.variables?.province.id === p.id
                  }
                  onClick={() =>
                    toggleMutation.mutate({ province: p, active: p.active === false })
                  }
                >
                  {p.active === false ? 'Reactivate' : 'Deactivate'}
                </Button>
              </Space>
            </div>
          ))}
          {!provincesQuery.isLoading && (provincesQuery.data ?? []).length === 0 ? (
            <Text type="secondary" style={{ padding: 16, fontSize: 13 }}>
              No provinces on file.
            </Text>
          ) : null}
        </div>
      </DrawerForm>

      <ProvinceEditDrawer
        open={createOpen || !!editing}
        editing={editing}
        onClose={() => {
          setCreateOpen(false);
          setEditing(null);
        }}
      />
    </>
  );
}

function ProvinceEditDrawer({
  open,
  editing,
  onClose,
}: {
  open: boolean;
  editing: ProvinceDto | null;
  onClose: () => void;
}) {
  const [form] = Form.useForm<{ code: string; name: string }>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (open) {
      form.resetFields();
      if (editing) {
        form.setFieldsValue({ code: editing.code ?? '', name: editing.name ?? '' });
      }
    }
  }, [open, editing, form]);

  const mutation = useMutation({
    mutationFn: (values: { code: string; name: string }) =>
      editing
        ? catalogsApi.updateProvince(editing.id!, values)
        : catalogsApi.createProvince(values),
    onSuccess: (p) => {
      message.success(editing ? `Updated ${p.name}` : `Added ${p.name}`);
      void queryClient.invalidateQueries({ queryKey: PROVINCES_KEY });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm
      open={open}
      onClose={onClose}
      title={editing ? `Edit ${editing.name ?? editing.code ?? ''}` : 'New province'}
      form={form}
      submitText={editing ? 'Save' : 'Create'}
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <Form.Item
        label="Code"
        name="code"
        rules={[{ required: true, message: 'Province code is required' }, { max: 16 }]}
      >
        <Input
          placeholder="PB"
          style={{ fontFamily: 'ui-monospace, monospace', textTransform: 'uppercase' }}
          maxLength={16}
        />
      </Form.Item>
      <Form.Item
        label="Name"
        name="name"
        rules={[{ required: true, message: 'Province name is required' }, { max: 128 }]}
      >
        <Input placeholder="Punjab" maxLength={128} />
      </Form.Item>
    </DrawerForm>
  );
}

/* ----------------------------- Fallback panels ------------------------ */

function EmptyPanel({ token }: { token: ReturnType<typeof useToken>['token'] }) {
  return (
    <section
      style={{
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 14,
        padding: 32,
      }}
    >
      <EmptyState
        icon={MapPin}
        title="No provinces yet"
        description="Add Pakistan's seven regions (PB, SD, KP, BL, ICT, AJK, GB) from Manage provinces, then you can start adding cities."
      />
    </section>
  );
}

function Placeholder({ token }: { token: ReturnType<typeof useToken>['token'] }) {
  return (
    <section
      style={{
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 14,
        height: 320,
      }}
    />
  );
}
