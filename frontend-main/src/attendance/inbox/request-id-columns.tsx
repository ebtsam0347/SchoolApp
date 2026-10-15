// "Request" column shared by the approvals-inbox tables: request code on
// top with the request id underneath — the same two values the server-side
// inbox search matches on. Sorts by id.

import type { TableColumnsType } from 'antd';
import { RequestIdCell } from './RequestIdCell';

type RowWithRequest = { request: { id: number; requestCode: string } };

export function requestIdColumns<Row extends RowWithRequest>({
  labelled = false,
}: { labelled?: boolean } = {}): TableColumnsType<Row> {
  return [
    {
      title: 'Request',
      key: 'request',
      width: 150,
      render: (_v, row) => (
        <RequestIdCell id={row.request.id} code={row.request.requestCode} labelled={labelled} />
      ),
      sorter: (a, b) => a.request.id - b.request.id,
    },
  ];
}
