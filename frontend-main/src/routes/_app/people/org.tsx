// /people/org — visual org chart.
//
// Driven by /employees/org-tree?root=&depth=3. The tree itself is rendered
// with react-d3-tree (SVG with foreignObject for the cards), so we get
// pan + zoom + collapse-by-click out of the box and can still author the
// node body in React + AntD tokens.
//
// AntD's <Tree> is text-list only — fine for nav, wrong for hierarchies
// where you need to see span-of-control at a glance. react-d3-tree is the
// smallest dep that gets us SVG layout + React custom nodes.
//
// Default root: HR (employee.write) lands on top-of-org so they can pivot
// across the company; everyone else lands on their own subtree to skip the
// drill-down from CEO. Either persona can use the breadcrumb to climb.

import { useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  InputNumber,
  Skeleton,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import Tree from 'react-d3-tree';
import type {
  CustomNodeElementProps,
  RawNodeDatum,
} from 'react-d3-tree';
import {
  ChevronDown,
  ChevronRight,
  GitBranch,
  IdCard,
  LayoutGrid,
  Maximize2,
  Minus,
  MoveHorizontal,
  Plus,
} from 'lucide-react';

import { AbilityContext, useAuth } from '@/auth';
import { PageHeader, PersonCell } from '@/components';
import { getErrorMessage } from '@/lib';
import { peopleBff, type OrgTreeNode } from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

type OrgSearch = { root?: number; depth?: number; scope?: 'top' };

export const Route = createFileRoute('/_app/people/org')({
  validateSearch: (s): OrgSearch => ({
    root: typeof s.root === 'number' ? s.root : s.root ? Number(s.root) : undefined,
    depth: typeof s.depth === 'number' ? s.depth : s.depth ? Number(s.depth) : undefined,
    scope: s.scope === 'top' ? 'top' : undefined,
  }),
  component: OrgChart,
});

const NODE_WIDTH = 260;
const NODE_HEIGHT = 96;
// react-d3-tree measures separation as a multiple of nodeSize for the axis
// perpendicular to the tree's growth direction, so these are dimensionless.
const SIBLING_SEP = 1.05;
const NONSIBLING_SEP = 1.3;
// Once any node exceeds this many direct reports, default to the horizontal
// layout — vertical sprawls off-screen for wide spans of control.
const WIDE_FANOUT_THRESHOLD = 8;
// Once the root's direct reports exceed this, the tree layout (any orientation)
// stops being readable at full-fit zoom. Switch to the grid view, which lays
// reports out as a wrapping CSS grid that uses the full canvas width.
const GRID_FANOUT_THRESHOLD = 12;

type LayoutMode = 'vertical' | 'horizontal' | 'grid';

function OrgChart() {
  const navigate = useNavigate({ from: '/people/org' });
  const search = useSearch({ from: '/_app/people/org' });
  const { user } = useAuth();
  const ability = useContext(AbilityContext);
  const isHr = ability.can('write', 'employee');
  const depth = search.depth ?? 3;

  // Resolve the caller's own employee id once — used as the default root
  // for non-HR users so a manager lands on their subtree instead of the CEO.
  const profileQuery = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    enabled: !!user && user.employeeLinked === true && !isHr,
    retry: false,
  });
  const myEmployeeId = profileQuery.data?.employee?.id;

  const wantsTop = search.scope === 'top';
  const effectiveRoot =
    search.root ?? (isHr || wantsTop ? undefined : myEmployeeId ?? undefined);
  const root = effectiveRoot;
  const isAtDefault = search.root === undefined && !wantsTop;
  const isAtTop = effectiveRoot === undefined;

  const treeQuery = useQuery({
    queryKey: ['people', 'org', root, depth],
    enabled: isHr || profileQuery.isFetched || profileQuery.isError,
    queryFn: ({ signal }) => peopleBff.orgTree({ root, depth }, signal),
  });

  const rootNode = treeQuery.data?.root;
  const treeData: RawNodeDatum[] = useMemo(
    () => (rootNode ? [toTreeDatum(rootNode)] : []),
    [rootNode],
  );
  const widestFanout = useMemo(
    () => (rootNode ? maxFanout(rootNode) : 0),
    [rootNode],
  );
  const rootFanout = rootNode?.children?.length ?? 0;

  // Auto-pick layout from the data. Grid wins for very wide root-level spans
  // (a manager's full team viewed at once); horizontal wins when any node deep
  // in the tree has many reports; vertical is the classic default otherwise.
  const autoLayout: LayoutMode =
    rootFanout >= GRID_FANOUT_THRESHOLD
      ? 'grid'
      : widestFanout >= WIDE_FANOUT_THRESHOLD
        ? 'horizontal'
        : 'vertical';
  const [userLayout, setUserLayout] = useState<LayoutMode | null>(null);
  const layout = userLayout ?? autoLayout;

  const cycleLayout = () => {
    const order: LayoutMode[] = ['vertical', 'horizontal', 'grid'];
    const next = order[(order.indexOf(layout) + 1) % order.length];
    setUserLayout(next);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="People"
        title="Reporting Chart"
        description={
          isHr
            ? 'Reporting hierarchy. Wide teams open in a grid view for readability; toggle layout from the canvas top-right.'
            : 'Your team. Wide teams open in a grid view for readability; toggle layout from the canvas top-right or jump to the top of the org from the breadcrumb.'
        }
        actions={
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            {!isAtDefault ? (
              <Button
                size="small"
                onClick={() => navigate({ search: () => ({ depth }) })}
              >
                {isHr ? 'Reset to top' : 'Back to my team'}
              </Button>
            ) : null}
            <Text type="secondary" style={{ fontSize: 13 }}>Depth</Text>
            <InputNumber
              min={1}
              max={6}
              value={depth}
              onChange={(v) => navigate({ search: (prev) => ({ ...prev, depth: v ?? 3 }) })}
              style={{ width: 80 }}
            />
          </span>
        }
      />

      <Breadcrumb
        items={[
          {
            title: isAtTop ? (
              <Text type="secondary" style={{ fontSize: 13 }}>Top of org</Text>
            ) : (
              <Button
                type="link"
                size="small"
                onClick={() =>
                  navigate({
                    search: () => (isHr ? { depth } : { depth, scope: 'top' }),
                  })
                }
              >
                Top of org
              </Button>
            ),
          },
          ...(root && rootNode
            ? [
                {
                  title: (
                    <span>
                      {rootNode.fullName} · {rootNode.employeeCode}
                    </span>
                  ),
                },
              ]
            : []),
        ]}
      />

      <Card styles={{ body: { padding: 0 } }}>
        {treeQuery.isLoading ? (
          <div style={{ padding: 24 }}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : treeQuery.isError ? (
          <div style={{ padding: 24 }}>
            <Alert
              type="error"
              showIcon
              title="Couldn't load org tree"
              description={getErrorMessage(treeQuery.error)}
            />
          </div>
        ) : !rootNode ? (
          <div style={{ padding: 24 }}>
            <Text type="secondary">No employees on file yet.</Text>
          </div>
        ) : layout === 'grid' ? (
          <OrgGridView
            rootNode={rootNode}
            layout={layout}
            onCycleLayout={cycleLayout}
            onDrillIn={(id) =>
              navigate({ search: (prev) => ({ ...prev, root: id }) })
            }
            onViewDetail={(id) =>
              navigate({ to: '/people/$id', params: { id: String(id) } })
            }
          />
        ) : (
          <OrgChartCanvas
            treeData={treeData}
            initialDepth={Math.max(1, Math.min(depth, 3))}
            orientation={layout}
            onCycleLayout={cycleLayout}
            onDrillIn={(id) =>
              navigate({ search: (prev) => ({ ...prev, root: id }) })
            }
            onViewDetail={(id) =>
              navigate({ to: '/people/$id', params: { id: String(id) } })
            }
          />
        )}
      </Card>
    </div>
  );
}

/** Recursive shape react-d3-tree consumes. We stash the whole `OrgTreeNode`
 *  on `attributes` so the custom node renderer can read it without a lookup. */
function toTreeDatum(node: OrgTreeNode): OrgRawNode {
  const children = (node.children ?? []).map(toTreeDatum);
  return {
    name: node.fullName ?? '—',
    attributes: {
      id: node.id ?? 0,
      employeeCode: node.employeeCode ?? '',
      designation: node.designationTitle ?? '',
      profilePictureUrl: node.profilePictureUrl ?? '',
      directReportsCount: node.directReportsCount ?? 0,
    },
    children: children.length ? children : undefined,
  };
}

type OrgRawNode = RawNodeDatum & {
  attributes: {
    id: number;
    employeeCode: string;
    designation: string;
    profilePictureUrl: string;
    directReportsCount: number;
  };
};

/** Max number of direct reports any node in the tree has. Drives the
 *  default-orientation choice — wide spans need horizontal layout to stay
 *  readable. */
function maxFanout(node: OrgTreeNode): number {
  const here = node.children?.length ?? 0;
  let deepest = here;
  for (const c of node.children ?? []) {
    const sub = maxFanout(c);
    if (sub > deepest) deepest = sub;
  }
  return deepest;
}

function OrgChartCanvas({
  treeData,
  initialDepth,
  orientation,
  onCycleLayout,
  onDrillIn,
  onViewDetail,
}: {
  treeData: RawNodeDatum[];
  initialDepth: number;
  orientation: 'vertical' | 'horizontal';
  onCycleLayout: () => void;
  onDrillIn: (id: number) => void;
  onViewDetail: (id: number) => void;
}) {
  const { token } = useToken();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [translate, setTranslate] = useState({ x: 0, y: 80 });
  const [zoom, setZoom] = useState(0.9);

  // Recenter root inside the canvas on mount, on data swap, and whenever the
  // container resizes (window resize, sidebar toggle, orientation flip).
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const recenter = () => {
      const rect = el.getBoundingClientRect();
      // Vertical: root at top-center. Horizontal: root at left-center with a
      // small left gutter so the first card isn't clipped.
      if (orientation === 'vertical') {
        setTranslate({ x: rect.width / 2, y: 80 });
      } else {
        setTranslate({ x: NODE_WIDTH / 2 + 24, y: rect.height / 2 });
      }
    };
    recenter();
    const observer = new ResizeObserver(recenter);
    observer.observe(el);
    return () => observer.disconnect();
  }, [treeData, orientation]);

  // For horizontal layout, x = depth distance, y = sibling spacing. For
  // vertical it's the opposite. react-d3-tree expects nodeSize in the
  // perpendicular-to-growth axis to drive sibling separation.
  const nodeSize =
    orientation === 'vertical'
      ? { x: NODE_WIDTH + 32, y: NODE_HEIGHT + 32 }
      : { x: NODE_WIDTH + 80, y: NODE_HEIGHT + 16 };

  return (
    <div
      ref={containerRef}
      style={{
        position: 'relative',
        width: '100%',
        height: 'min(72vh, 760px)',
        background:
          `radial-gradient(circle, ${token.colorBorderSecondary} 1px, transparent 1px)`,
        backgroundSize: '18px 18px',
        borderBottomLeftRadius: token.borderRadiusLG,
        borderBottomRightRadius: token.borderRadiusLG,
        overflow: 'hidden',
      }}
    >
      <Tree
        data={treeData}
        orientation={orientation}
        pathFunc="step"
        translate={translate}
        zoom={zoom}
        zoomable
        collapsible
        initialDepth={initialDepth}
        nodeSize={nodeSize}
        separation={{ siblings: SIBLING_SEP, nonSiblings: NONSIBLING_SEP }}
        pathClassFunc={() => 'fshrms-org-link'}
        renderCustomNodeElement={(rd3) => (
          <OrgNode
            rd3={rd3}
            onDrillIn={onDrillIn}
            onViewDetail={onViewDetail}
            token={token}
          />
        )}
      />

      {/* Floating controls — small, low-chrome, top-right. */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          display: 'inline-flex',
          flexDirection: 'column',
          gap: 4,
          background: token.colorBgElevated,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadius,
          padding: 4,
          boxShadow: token.boxShadowTertiary,
        }}
      >
        <Button
          size="small"
          type="text"
          icon={
            orientation === 'vertical' ? (
              <MoveHorizontal size={14} strokeWidth={1.75} />
            ) : (
              <LayoutGrid size={14} strokeWidth={1.75} />
            )
          }
          onClick={onCycleLayout}
          title={
            orientation === 'vertical'
              ? 'Switch layout — next: horizontal'
              : 'Switch layout — next: grid'
          }
        />
        <div
          style={{
            height: 1,
            background: token.colorBorderSecondary,
            margin: '2px 4px',
          }}
        />
        <Button
          size="small"
          type="text"
          icon={<Plus size={14} strokeWidth={1.75} />}
          onClick={() => setZoom((z) => Math.min(z * 1.2, 2.5))}
          title="Zoom in"
        />
        <Button
          size="small"
          type="text"
          icon={<Minus size={14} strokeWidth={1.75} />}
          onClick={() => setZoom((z) => Math.max(z / 1.2, 0.3))}
          title="Zoom out"
        />
        <Button
          size="small"
          type="text"
          icon={<Maximize2 size={14} strokeWidth={1.75} />}
          onClick={() => {
            setZoom(0.9);
            const rect = containerRef.current?.getBoundingClientRect();
            if (!rect) return;
            if (orientation === 'vertical') {
              setTranslate({ x: rect.width / 2, y: 80 });
            } else {
              setTranslate({ x: NODE_WIDTH / 2 + 24, y: rect.height / 2 });
            }
          }}
          title="Reset view"
        />
      </div>

      <style>{`
        .fshrms-org-link {
          fill: none;
          stroke: ${token.colorBorder};
          stroke-width: 1.4;
          stroke-linecap: round;
        }
        .rd3t-tree-container {
          width: 100%;
          height: 100%;
        }
      `}</style>
    </div>
  );
}

function OrgNode({
  rd3,
  onDrillIn,
  onViewDetail,
  token,
}: {
  rd3: CustomNodeElementProps;
  onDrillIn: (id: number) => void;
  onViewDetail: (id: number) => void;
  token: ReturnType<typeof useToken>['token'];
}) {
  const datum = rd3.nodeDatum;
  const raw = datum.attributes ?? {};
  const attrs = {
    id: typeof raw.id === 'number' ? raw.id : 0,
    employeeCode: typeof raw.employeeCode === 'string' ? raw.employeeCode : '',
    designation: typeof raw.designation === 'string' ? raw.designation : '',
    profilePictureUrl:
      typeof raw.profilePictureUrl === 'string' ? raw.profilePictureUrl : '',
    directReportsCount:
      typeof raw.directReportsCount === 'number' ? raw.directReportsCount : 0,
  };
  const collapsed = datum.__rd3t.collapsed === true;
  const hasChildren = (datum.children?.length ?? 0) > 0 || collapsed;

  return (
    <foreignObject
      width={NODE_WIDTH}
      height={NODE_HEIGHT}
      x={-NODE_WIDTH / 2}
      y={-NODE_HEIGHT / 2}
      style={{ overflow: 'visible' }}
    >
      <div
        style={{
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
          background: token.colorBgElevated,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
          padding: '12px 14px',
          boxShadow: token.boxShadowTertiary,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          cursor: attrs.id ? 'pointer' : 'default',
          transition: 'transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-1px)';
          e.currentTarget.style.boxShadow = token.boxShadowSecondary;
          e.currentTarget.style.borderColor = token.colorPrimaryBorder;
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = '';
          e.currentTarget.style.boxShadow = token.boxShadowTertiary;
          e.currentTarget.style.borderColor = token.colorBorderSecondary;
        }}
        onClick={() => {
          if (attrs.id) onDrillIn(attrs.id);
        }}
        title={attrs.id ? 'Click to focus this subtree' : undefined}
      >
        <PersonCell
          size="compact"
          person={{
            fullName: datum.name,
            employeeCode: attrs.employeeCode || undefined,
            designation: attrs.designation || null,
            profilePictureUrl: attrs.profilePictureUrl || undefined,
          }}
        />

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 6,
          }}
        >
          {attrs.directReportsCount > 0 ? (
            <Tag style={{ margin: 0, fontSize: 11 }}>
              {attrs.directReportsCount}{' '}
              {attrs.directReportsCount === 1 ? 'report' : 'reports'}
            </Tag>
          ) : (
            <span />
          )}
          <span style={{ display: 'inline-flex', gap: 4 }}>
            {attrs.id ? (
              <Button
                size="small"
                type="text"
                icon={<IdCard size={14} strokeWidth={1.75} />}
                onClick={(e) => {
                  e.stopPropagation();
                  onViewDetail(attrs.id);
                }}
                title="View employee detail"
                style={{ padding: '0 6px' }}
              />
            ) : null}
            {hasChildren ? (
              <Button
                size="small"
                type="text"
                icon={
                  collapsed ? (
                    <ChevronRight size={14} strokeWidth={1.75} />
                  ) : (
                    <ChevronDown size={14} strokeWidth={1.75} />
                  )
                }
                onClick={(e) => {
                  e.stopPropagation();
                  rd3.toggleNode();
                }}
                title={collapsed ? 'Expand' : 'Collapse'}
                style={{ padding: '0 6px' }}
              >
                {collapsed ? 'Expand' : 'Collapse'}
              </Button>
            ) : null}
          </span>
        </div>
      </div>
    </foreignObject>
  );
}

/** Grid view for wide-fanout managers. Tree layouts can't make 30 siblings
 *  readable at full-fit zoom; the grid drops the connecting lines, places the
 *  manager card at the top, and packs reports into a wrapping CSS grid that
 *  uses the full canvas width. Each tile drills into that report's subtree. */
function OrgGridView({
  rootNode,
  layout,
  onCycleLayout,
  onDrillIn,
  onViewDetail,
}: {
  rootNode: OrgTreeNode;
  layout: LayoutMode;
  onCycleLayout: () => void;
  onDrillIn: (id: number) => void;
  onViewDetail: (id: number) => void;
}) {
  const { token } = useToken();
  const reports = rootNode.children ?? [];

  return (
    <div
      style={{
        position: 'relative',
        padding: '20px 24px 28px',
        background:
          `radial-gradient(circle, ${token.colorBorderSecondary} 1px, transparent 1px)`,
        backgroundSize: '18px 18px',
        borderBottomLeftRadius: token.borderRadiusLG,
        borderBottomRightRadius: token.borderRadiusLG,
      }}
    >
      {/* Manager header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          marginBottom: 24,
        }}
      >
        <div
          style={{
            minWidth: 280,
            maxWidth: 420,
            background: token.colorBgElevated,
            border: `1px solid ${token.colorPrimaryBorder}`,
            borderRadius: token.borderRadiusLG,
            padding: '14px 18px',
            boxShadow: token.boxShadowTertiary,
          }}
        >
          <PersonCell
            person={{
              fullName: rootNode.fullName ?? '—',
              employeeCode: rootNode.employeeCode,
              designation: rootNode.designationTitle ?? null,
              profilePictureUrl: rootNode.profilePictureUrl,
            }}
            to={rootNode.id ? `/people/${rootNode.id}` : undefined}
          />
          {reports.length > 0 ? (
            <div style={{ marginTop: 10 }}>
              <Tag style={{ margin: 0 }}>
                {reports.length} {reports.length === 1 ? 'direct report' : 'direct reports'}
              </Tag>
            </div>
          ) : null}
        </div>
      </div>

      {/* Stem line from manager card to grid */}
      {reports.length > 0 ? (
        <div
          style={{
            width: 1,
            height: 18,
            background: token.colorBorderSecondary,
            margin: '0 auto 18px',
          }}
        />
      ) : null}

      {/* Report grid — auto-fit columns so the canvas width is fully used. */}
      {reports.length === 0 ? (
        <Text type="secondary" style={{ display: 'block', textAlign: 'center' }}>
          No direct reports.
        </Text>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 12,
          }}
        >
          {reports.map((r) => (
            <ReportTile
              key={r.id ?? r.fullName}
              node={r}
              onClick={() => r.id && onDrillIn(r.id)}
              onViewDetail={onViewDetail}
              token={token}
            />
          ))}
        </div>
      )}

      {/* Layout cycle button — same control as the tree canvas. */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          background: token.colorBgElevated,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadius,
          padding: 4,
          boxShadow: token.boxShadowTertiary,
        }}
      >
        <Button
          size="small"
          type="text"
          icon={<GitBranch size={14} strokeWidth={1.75} />}
          onClick={onCycleLayout}
          title={`Switch layout — next: ${nextLayoutLabel(layout)}`}
        />
      </div>
    </div>
  );
}

function ReportTile({
  node,
  onClick,
  onViewDetail,
  token,
}: {
  node: OrgTreeNode;
  onClick: () => void;
  onViewDetail: (id: number) => void;
  token: ReturnType<typeof useToken>['token'];
}) {
  return (
    <div
      style={{
        background: token.colorBgElevated,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        padding: '10px 12px',
        cursor: node.id ? 'pointer' : 'default',
        transition: 'transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-1px)';
        e.currentTarget.style.boxShadow = token.boxShadowSecondary;
        e.currentTarget.style.borderColor = token.colorPrimaryBorder;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = '';
        e.currentTarget.style.boxShadow = '';
        e.currentTarget.style.borderColor = token.colorBorderSecondary;
      }}
      onClick={onClick}
      title={node.id ? 'Click to focus this subtree' : undefined}
    >
      <PersonCell
        size="compact"
        person={{
          fullName: node.fullName ?? '—',
          employeeCode: node.employeeCode,
          designation: node.designationTitle ?? null,
          profilePictureUrl: node.profilePictureUrl,
        }}
      />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 6,
        }}
      >
        {node.directReportsCount && node.directReportsCount > 0 ? (
          <Tag style={{ margin: 0, fontSize: 11 }}>
            {node.directReportsCount}{' '}
            {node.directReportsCount === 1 ? 'report' : 'reports'}
          </Tag>
        ) : (
          <span />
        )}
        {node.id ? (
          <Button
            size="small"
            type="text"
            icon={<IdCard size={14} strokeWidth={1.75} />}
            onClick={(e) => {
              e.stopPropagation();
              onViewDetail(node.id!);
            }}
            title="View employee detail"
            style={{ padding: '0 6px' }}
          />
        ) : null}
      </div>
    </div>
  );
}

function nextLayoutLabel(layout: LayoutMode): string {
  switch (layout) {
    case 'vertical':
      return 'horizontal';
    case 'horizontal':
      return 'grid';
    case 'grid':
      return 'vertical';
  }
}
