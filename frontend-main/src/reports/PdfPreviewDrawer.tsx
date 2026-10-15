// PDF preview drawer shared by every report that renders to PDF: the
// employee Print menu (profile, ID card, letters, certificates) and PDF
// entries run from the /reports catalog. Keep it mounted and toggle
// `open`: the file is fetched by `load` each time the drawer finishes
// opening (callers that already hold the file — the
// salary-certificate modal, the catalog runner — pass a loader that
// resolves it) and previewed in an <iframe> on a blob URL, which is
// revoked when it's replaced and when the drawer closes.

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Drawer, Space, Spin, theme as antdTheme } from 'antd';
import { Download, FileText, Printer } from 'lucide-react';

import { getErrorMessage, isForbidden, isNotFound, useIsDesktop } from '@/lib';

import { saveBlob, type ReportFile } from './download';

const { useToken } = antdTheme;

type Preview = ReportFile & { url: string };

export interface PdfPreviewDrawerProps {
  open: boolean;
  title: ReactNode;
  icon?: ReactNode;
  /** Fetches the PDF. Called when the drawer opens and on Retry. */
  load: (signal: AbortSignal) => Promise<ReportFile>;
  onClose: () => void;
  /** Drawer width on desktop. */
  width?: number;
  /**
   * Report-specific rendering of a failed load (e.g. a 422 with a link to
   * the tab where the missing data is fixed). Return `undefined` to fall
   * back to the generic alert. `close` closes the drawer — for links that
   * navigate away.
   */
  renderError?: (error: unknown, close: () => void) => ReactNode | undefined;
}

export function PdfPreviewDrawer({
  open,
  title,
  icon = <FileText size={16} strokeWidth={1.75} />,
  load,
  onClose,
  width = 900,
  renderError,
}: PdfPreviewDrawerProps) {
  const { token } = useToken();
  const isDesktop = useIsDesktop();
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  // Revoke each blob URL once it's replaced, and the last one on unmount.
  const previewUrl = preview?.url;
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  useEffect(() => () => abortRef.current?.abort(), []);

  async function render() {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setLoading(true);
    setError(null);
    try {
      const file = await load(ctrl.signal);
      if (ctrl.signal.aborted) return;
      setPreview({ ...file, url: URL.createObjectURL(file.blob) });
    } catch (e) {
      if (!ctrl.signal.aborted) setError(e);
    } finally {
      if (!ctrl.signal.aborted) setLoading(false);
    }
  }

  function handleClose() {
    abortRef.current?.abort();
    setPreview(null);
    setError(null);
    setLoading(false);
    onClose();
  }

  const custom = error ? renderError?.(error, handleClose) : undefined;
  const errorAlert = !error ? null : custom !== undefined ? (
    <div style={{ marginBottom: 12 }}>{custom}</div>
  ) : (
    <Alert
      type="error"
      showIcon
      style={{ marginBottom: 12 }}
      title={
        isNotFound(error)
          ? 'Employee not found or not in your team.'
          : isForbidden(error)
            ? "You don't have permission to print this report"
            : "Couldn't render the report"
      }
      description={
        isNotFound(error) || isForbidden(error) ? undefined : getErrorMessage(error)
      }
      action={
        isNotFound(error) || isForbidden(error) ? undefined : (
          <Button size="small" onClick={() => void render()}>
            Retry
          </Button>
        )
      }
    />
  );

  return (
    <Drawer
      open={open}
      onClose={handleClose}
      destroyOnHidden
      size={isDesktop ? width : '100%'}
      afterOpenChange={(visible) => {
        if (visible && !preview) void render();
      }}
      title={
        <Space size={10}>
          {icon}
          {title}
        </Space>
      }
      styles={{ body: { display: 'flex', flexDirection: 'column' } }}
      footer={
        <Space style={{ width: '100%', justifyContent: 'flex-end' }} wrap>
          <Button
            icon={<Printer size={14} strokeWidth={1.75} />}
            disabled={!preview || loading}
            onClick={() => iframeRef.current?.contentWindow?.print()}
          >
            Print
          </Button>
          <Button
            type="primary"
            icon={<Download size={14} strokeWidth={1.75} />}
            disabled={!preview || loading}
            onClick={() => preview && saveBlob(preview.blob, preview.filename)}
          >
            Download PDF
          </Button>
        </Space>
      }
    >
      {errorAlert}
      <div
        style={{
          position: 'relative',
          flex: 1,
          minHeight: isDesktop ? 0 : '60vh',
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
          background: token.colorFillQuaternary,
          overflow: 'hidden',
        }}
      >
        {preview ? (
          <iframe
            ref={iframeRef}
            title="Report preview"
            src={preview.url}
            style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
          />
        ) : null}
        {loading ? (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'grid',
              placeItems: 'center',
              background: `color-mix(in srgb, ${token.colorBgContainer} 60%, transparent)`,
            }}
          >
            <Spin />
          </div>
        ) : null}
      </div>
    </Drawer>
  );
}
