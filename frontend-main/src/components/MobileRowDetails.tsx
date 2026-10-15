// Renders a record's full column set as a key/value list. Designed to drop
// into AntD `<Table expandable={{ expandedRowRender }}/>` on mobile so
// users can see the columns that auto-collapsed off-screen.
//
// Pair with `useMobileExpandable(columns)` to get the prebuilt
// expandable config that activates only at small viewports.

import { theme as antdTheme } from 'antd';
import type { ReactNode } from 'react';
import type { ColumnType, ExpandableConfig } from 'antd/es/table/interface';
import { useMediaQuery } from '@/lib';

const { useToken } = antdTheme;

export type MobileRowDetailsProps<T> = {
  /** All columns (visible + hidden). The render fn is called per cell. */
  columns: ReadonlyArray<ColumnType<T>>;
  row: T;
  /**
   * Column keys whose render output is too rich for the inline list (avatar
   * stacks, multi-line cards, etc.). They'll be skipped here since the user
   * already sees them in the row above.
   */
  skipKeys?: ReadonlyArray<string>;
};

function getKey<T>(c: ColumnType<T>, idx: number): string {
  if (typeof c.key === 'string' || typeof c.key === 'number') return String(c.key);
  if (typeof c.dataIndex === 'string') return c.dataIndex;
  if (Array.isArray(c.dataIndex)) return c.dataIndex.join('.');
  return `__col_${idx}`;
}

function getCell<T>(c: ColumnType<T>, row: T, idx: number): ReactNode {
  const dataIndex = c.dataIndex;
  let value: unknown = undefined;
  if (typeof dataIndex === 'string') {
    value = (row as Record<string, unknown>)[dataIndex];
  } else if (Array.isArray(dataIndex)) {
    let cursor: unknown = row;
    for (const seg of dataIndex) {
      if (cursor && typeof cursor === 'object') {
        cursor = (cursor as Record<string, unknown>)[String(seg)];
      } else {
        cursor = undefined;
        break;
      }
    }
    value = cursor;
  }
  if (typeof c.render === 'function') {
    return c.render(value, row, idx) as ReactNode;
  }
  return (value as ReactNode) ?? null;
}

/**
 * AntD `expandable` config that activates only at mobile widths. Pass the
 * full column list (including any always-shown columns); identifying
 * columns the user already sees in the row should go into `skipKeys`.
 *
 * `import { useMobileRowExpandable } from '@/components'`
 */
export function useMobileRowExpandable<T>(
  columns: ReadonlyArray<ColumnType<T>>,
  options?: { skipKeys?: ReadonlyArray<string>; breakpoint?: string },
): ExpandableConfig<T> | undefined {
  const isMobile = useMediaQuery(options?.breakpoint ?? '(max-width: 640px)');
  if (!isMobile) return undefined;
  return {
    expandedRowRender: (row) => (
      <MobileRowDetails columns={columns} row={row} skipKeys={options?.skipKeys} />
    ),
    rowExpandable: () => true,
    expandRowByClick: false,
  };
}

export function MobileRowDetails<T>({
  columns,
  row,
  skipKeys,
}: MobileRowDetailsProps<T>) {
  const { token } = useToken();
  const skip = new Set(skipKeys ?? []);
  return (
    <dl
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, max-content) 1fr',
        gap: '6px 12px',
        margin: 0,
        padding: '8px 4px',
      }}
    >
      {columns.map((c, i) => {
        const key = getKey(c, i);
        if (skip.has(key)) return null;
        const title = typeof c.title === 'string' || typeof c.title === 'number' ? c.title : '';
        if (!title) return null;
        return (
          <div key={key} style={{ display: 'contents' }}>
            <dt
              style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
                color: token.colorTextTertiary,
                paddingTop: 2,
              }}
            >
              {title}
            </dt>
            <dd style={{ margin: 0, fontSize: 13, color: token.colorText, minWidth: 0 }}>
              {getCell(c, row, i)}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
