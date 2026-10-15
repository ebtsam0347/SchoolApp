// Bulk canteen attendance from a spreadsheet.
//
// The workbook is parsed here and posted as an identifier → count map:
//
//   Manual card  → POST /canteen/attendance/upload-excel/byEmployeeCode
//   Barcode      → POST /canteen/attendance/upload-excel/byBarCode
//
// Expected sheet: column A is the identifier (`employee_code` or `barCode`),
// column B is `count` — how many meals that line represents.
//
// Since the payload is a map, an identifier can appear only once. Duplicate
// sheet rows are summed rather than letting the last one win, and merged rows
// are marked in the preview so the operator sees it happen before uploading.
//
// Cancel and Upload both live in the body under the preview rather than in
// the drawer footer: they read better right beside the file they act on, and
// it keeps them clear of the bottom-right corner, where the router devtools
// button sits in dev builds.

import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Radio,
  Table,
  TimePicker,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { Clock, Download, FileSpreadsheet, Trash2, UploadCloud } from 'lucide-react';
import * as XLSX from 'xlsx';

import { DrawerForm } from '@/components';
import { getErrorMessage } from '@/lib';
import { canteenAttendanceApi } from './api';
import { CanteenUploadResult } from './CanteenUploadResult';
import { UPLOAD_TYPE_LABEL } from './format';
import type { CanteenExcelUploadResponse, CanteenUploadType } from './types';

const { Text } = Typography;

/**
 * Column A per upload type. First entry of each list is what the
 * downloadable template writes; the rest are spellings sheets in the wild
 * actually carry. Matching is case-, space- and underscore-insensitive.
 */
const IDENTITY_HEADERS: Record<CanteenUploadType, string[]> = {
  BARCODE: ['barCode', 'bar_code', 'barcode_no', 'barcode'],
  MANUAL_CARD: ['employee_code', 'employee code', 'employeecode', 'emp_code', 'code'],
};

/** Column B on both layouts. */
const COUNT_HEADERS = ['count', 'qty', 'quantity', 'meals', 'no_of_meals'];

const norm = (s: string) => s.replace(/[\s_]+/g, '').toLowerCase();

function pick(row: Record<string, unknown>, headers: string[]): string {
  const wanted = new Set(headers.map(norm));
  for (const [k, v] of Object.entries(row)) {
    if (wanted.has(norm(k))) {
      const s = String(v ?? '').trim();
      if (s) return s;
    }
  }
  return '';
}

/**
 * A blank count means one meal. Anything non-numeric or below 1 is treated
 * as unusable rather than coerced, so a stray label in the column cannot
 * become a meal charge.
 */
function parseCount(raw: string): number | null {
  if (!raw) return 1;
  const n = Number(raw);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n < 1) return null;
  return n;
}

/** One identifier after duplicate sheet rows have been merged. */
type PreviewRow = {
  identifier: string;
  count: number;
  /** Sheet rows this entry came from — more than one means it was merged. */
  sourceRows: number[];
};

export type UploadCanteenAttendanceDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type Values = {
  uploadType: CanteenUploadType;
  attendanceDate: Dayjs;
  attendanceTime: Dayjs;
};

export function UploadCanteenAttendanceDrawer({
  open,
  onClose,
}: UploadCanteenAttendanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const uploadType = Form.useWatch('uploadType', form) ?? 'BARCODE';

  const [fileName, setFileName] = useState<string | null>(null);
  const [preview, setPreview] = useState<PreviewRow[]>([]);
  const [skipped, setSkipped] = useState<{ rowNumber: number; reason: string }[]>([]);
  const [result, setResult] = useState<CanteenExcelUploadResponse | null>(null);
  const [uploadedFor, setUploadedFor] = useState('');

  const reset = () => {
    setFileName(null);
    setPreview([]);
    setSkipped([]);
    setResult(null);
  };

  useEffect(() => {
    if (!open) {
      reset();
      form.resetFields();
    }
  }, [open, form]);

  // Swapping the layout changes which column is the identifier, so anything
  // parsed under the old one no longer describes the file.
  useEffect(() => {
    reset();
  }, [uploadType]);

  const identityLabel = uploadType === 'BARCODE' ? 'Bar code' : 'Employee code';
  const templateHeaders = useMemo(
    () => [IDENTITY_HEADERS[uploadType][0], COUNT_HEADERS[0]],
    [uploadType],
  );

  const totalMeals = preview.reduce((sum, r) => sum + r.count, 0);
  const mergedCount = preview.filter((r) => r.sourceRows.length > 1).length;

  const downloadTemplate = () => {
    const ws = XLSX.utils.aoa_to_sheet([
      templateHeaders,
      [uploadType === 'BARCODE' ? '8901234567890' : '1042', 1],
    ]);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Canteen');
    XLSX.writeFile(
      wb,
      `canteen-${uploadType.toLowerCase().replace('_', '-')}-template.xlsx`,
    );
  };

  const handleFile = async (f: File) => {
    setResult(null);

    let raw: Record<string, unknown>[] = [];
    try {
      const buf = await f.arrayBuffer();
      const wb = XLSX.read(buf, { type: 'array' });
      const sheet = wb.Sheets[wb.SheetNames[0]];
      raw = sheet
        ? XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' })
        : [];
    } catch {
      message.error('Could not read that file. Is it a valid .xlsx / .xls / .csv?');
      reset();
      return false;
    }

    const merged = new Map<string, PreviewRow>();
    const bad: { rowNumber: number; reason: string }[] = [];

    raw.forEach((r, i) => {
      // +2 = 1-based sheet row, plus the header line.
      const rowNumber = i + 2;
      const identifier = pick(r, IDENTITY_HEADERS[uploadType]);
      if (!identifier) {
        bad.push({ rowNumber, reason: `No ${identityLabel} value` });
        return;
      }
      const count = parseCount(pick(r, COUNT_HEADERS));
      if (count === null) {
        bad.push({ rowNumber, reason: 'Count must be a whole number of 1 or more' });
        return;
      }
      const existing = merged.get(identifier);
      if (existing) {
        existing.count += count;
        existing.sourceRows.push(rowNumber);
      } else {
        merged.set(identifier, { identifier, count, sourceRows: [rowNumber] });
      }
    });

    const rows = [...merged.values()];
    setFileName(f.name);
    setPreview(rows);
    setSkipped(bad);

    if (rows.length === 0) {
      message.warning(`No usable rows found. Expected a "${templateHeaders[0]}" column.`);
    } else {
      message.success(`Parsed ${rows.length} employees from ${f.name}`);
    }
    // Always false — AntD never uploads for us; the mutation does.
    return false;
  };

  const mutation = useMutation({
    mutationFn: (v: Values) => {
      const counts: Record<string, number> = {};
      for (const row of preview) counts[row.identifier] = row.count;
      return canteenAttendanceApi.uploadExcel(v.uploadType, counts, {
        attendanceDate: v.attendanceDate.format('YYYY-MM-DD'),
        attendanceTime: v.attendanceTime.format('HH:mm:ss'),
      });
    },
    onSuccess: (res, v) => {
      setResult(res ?? {});
      setUploadedFor(v.attendanceDate.format('YYYY-MM-DD'));
      // Drop the parsed file but keep the result on screen: this also
      // disables Upload, so a slow response cannot be submitted twice.
      setFileName(null);
      setPreview([]);
      setSkipped([]);
      queryClient.invalidateQueries({ queryKey: ['canteen'] });
      const ok = Object.keys(res?.success ?? {}).length;
      const failed = Object.keys(res?.error ?? {}).length;
      if (failed === 0) {
        message.success(`${ok} employees recorded`);
      } else {
        message.warning(`${ok} recorded, ${failed} failed — see the breakdown`);
      }
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const previewColumns: ColumnsType<PreviewRow> = [
    {
      title: identityLabel,
      dataIndex: 'identifier',
      render: (identifier: string, row) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{identifier}</span>
          {row.sourceRows.length > 1 ? (
            <Tooltip title={`Merged from sheet rows ${row.sourceRows.join(', ')}`}>
              <Tag color="gold" style={{ margin: 0, fontSize: 10 }}>
                merged ×{row.sourceRows.length}
              </Tag>
            </Tooltip>
          ) : null}
        </span>
      ),
    },
    { title: 'Count', dataIndex: 'count', width: 90, align: 'right' },
  ];

  const now = dayjs();

  return (
    <DrawerForm<Values>
      open={open}
      onClose={onClose}
      title="Upload canteen attendance"
      size={720}
      form={form}
      submitting={mutation.isPending}
      renderActions={({ requestClose }) => (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 12,
            marginTop: 16,
          }}
        >
          <Text type="secondary" style={{ fontSize: 12, marginRight: 'auto' }}>
            {preview.length > 0
              ? `${preview.length} employees · ${totalMeals} meals ready`
              : result
                ? 'Uploaded. Choose another file to upload again.'
                : 'Choose a file to enable upload.'}
          </Text>
          <Button onClick={requestClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            icon={<UploadCloud size={15} strokeWidth={1.75} />}
            loading={mutation.isPending}
            disabled={preview.length === 0}
          >
            {preview.length ? `Upload ${preview.length} employees` : 'Upload'}
          </Button>
        </div>
      )}
      initialValues={{
        uploadType: 'BARCODE',
        attendanceDate: now,
        attendanceTime: now,
      }}
      onFinish={(v) => {
        if (preview.length === 0) return;
        mutation.mutate(v);
      }}
    >
      <Form.Item
        label="Upload type"
        name="uploadType"
        rules={[{ required: true }]}
        extra={
          uploadType === 'BARCODE'
            ? 'Column A is the scanned barcode value.'
            : 'Column A is the employee code off the printed card.'
        }
        style={{ marginBottom: 12 }}
      >
        <Radio.Group buttonStyle="solid">
          <Radio.Button value="BARCODE">
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              {UPLOAD_TYPE_LABEL.BARCODE}
              <Tag color="gold" style={{ margin: 0, fontSize: 10, lineHeight: '16px' }}>
                Soon
              </Tag>
            </span>
          </Radio.Button>
          <Radio.Button value="MANUAL_CARD">
            {UPLOAD_TYPE_LABEL.MANUAL_CARD}
          </Radio.Button>
        </Radio.Group>
      </Form.Item>

      {uploadType === 'BARCODE' ? (
        <Alert
          type="info"
          showIcon
          icon={<Clock size={16} />}
          style={{ marginBottom: 16 }}
          title="Barcode upload — coming soon"
          description="The barcode import is still being built on the service side. Use Manual card, which matches on employee code, until it ships."
        />
      ) : null}

      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item
          label="Attendance date"
          name="attendanceDate"
          rules={[{ required: true, message: 'Pick the date' }]}
          style={{ flex: 1 }}
        >
          <DatePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
            disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
          />
        </Form.Item>
        <Form.Item
          label="Attendance time"
          name="attendanceTime"
          rules={[{ required: true, message: 'Pick the time' }]}
          style={{ flex: 1 }}
        >
          <TimePicker
            style={{ width: '100%' }}
            format="HH:mm"
            minuteStep={1}
            needConfirm={false}
          />
        </Form.Item>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          marginBottom: 8,
        }}
      >
        <Text strong style={{ fontSize: 13 }}>
          Excel or CSV file
        </Text>
        <Button size="small" icon={<Download size={14} />} onClick={downloadTemplate}>
          Template
        </Button>
      </div>

      <Upload.Dragger
        accept=".xlsx,.xls,.csv"
        showUploadList={false}
        multiple={false}
        beforeUpload={handleFile}
      >
        <p style={{ marginBottom: 8 }}>
          <UploadCloud size={20} />
        </p>
        <p style={{ margin: 0 }}>Click or drag an Excel or CSV file here</p>
        <p style={{ margin: 0, fontSize: 12, opacity: 0.65 }}>
          Column A: {templateHeaders[0]} · Column B: {templateHeaders[1]} (blank
          counts as 1)
        </p>
      </Upload.Dragger>

      {fileName ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginTop: 12,
            flexWrap: 'wrap',
          }}
        >
          <FileSpreadsheet size={14} />
          <Text style={{ fontSize: 12 }}>{fileName}</Text>
          <Tag color="blue" style={{ margin: 0 }}>
            {preview.length} employees · {totalMeals} meals
          </Tag>
          {mergedCount > 0 ? (
            <Tooltip title="Duplicate rows for the same identifier were added together.">
              <Tag color="gold" style={{ margin: 0 }}>
                {mergedCount} merged
              </Tag>
            </Tooltip>
          ) : null}
          {skipped.length > 0 ? (
            <Tag color="orange" style={{ margin: 0 }}>
              {skipped.length} skipped
            </Tag>
          ) : null}
          <Button size="small" type="text" icon={<Trash2 size={14} />} onClick={reset}>
            Clear
          </Button>
        </div>
      ) : null}

      {skipped.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12 }}
          title={`${skipped.length} sheet rows skipped`}
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {skipped.slice(0, 8).map((s) => (
                <li key={s.rowNumber} style={{ fontSize: 12 }}>
                  Row {s.rowNumber} — {s.reason}
                </li>
              ))}
              {skipped.length > 8 ? (
                <li style={{ fontSize: 12 }}>…and {skipped.length - 8} more</li>
              ) : null}
            </ul>
          }
        />
      ) : null}

      {preview.length > 0 && !result ? (
        <Table<PreviewRow>
          rowKey="identifier"
          size="small"
          style={{ marginTop: 12 }}
          columns={previewColumns}
          dataSource={preview}
          pagination={{
            pageSize: 10,
            size: 'small',
            showSizeChanger: false,
            showTotal: (n, [start, end]) =>
              `${start}–${end} of ${n.toLocaleString('en-PK')}`,
          }}
        />
      ) : null}

      {result ? (
        <CanteenUploadResult result={result} attendanceDate={uploadedFor} />
      ) : null}
    </DrawerForm>
  );
}
