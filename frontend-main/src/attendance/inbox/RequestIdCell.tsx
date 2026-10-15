// Request code on top, request id underneath — the "Request" cell every
// approvals-inbox table leads with (see requestIdColumns).

import { Typography } from 'antd';

const { Text } = Typography;

export function RequestIdCell({
  id,
  code,
  labelled = false,
}: {
  id: number;
  code: string | null | undefined;
  /** Emphasise the code and label the id (`ID #42`) so the two read as
   *  distinct values rather than a pair of similar-looking numbers. */
  labelled?: boolean;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
      <Text
        style={{
          fontFamily: 'monospace',
          fontSize: labelled ? 13 : 12,
          fontWeight: labelled ? 600 : undefined,
        }}
      >
        {code || '—'}
      </Text>
      <Text type="secondary" style={{ fontSize: 11 }}>
        {labelled ? `ID #${id}` : `#${id}`}
      </Text>
    </div>
  );
}
