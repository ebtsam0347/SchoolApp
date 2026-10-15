// /people/$id/documents — employee documents grid + two-step signed-URL
// upload + delete. Grouped by doc_type (CNIC / DEGREE / OFFER_LETTER /
// OTHER) so the most common categories surface together.

import { useRef, useState } from 'react';
import { createFileRoute, useParams } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Tooltip,
  Typography,
  Upload,
  theme as antdTheme,
} from 'antd';
import {
  ArrowDownToLine,
  ExternalLink,
  IdCard,
  GraduationCap,
  FileText,
  Folder,
  Trash2,
  Upload as UploadIcon,
} from 'lucide-react';

import { DocumentCard, EmptyState, Section } from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import { documentsApi, type EmployeeDocumentDto, type DocumentType } from '@/people';

const { Text, Paragraph } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/$id/documents')({
  component: DocumentsTab,
});

const DOC_TYPE_LABEL: Record<DocumentType, string> = {
  CNIC: 'CNIC',
  DEGREE: 'Degrees',
  OFFER_LETTER: 'Offer letter',
  OTHER: 'Other',
};

const DOC_TYPE_ICON: Record<DocumentType, typeof Folder> = {
  CNIC: IdCard,
  DEGREE: GraduationCap,
  OFFER_LETTER: FileText,
  OTHER: Folder,
};

const TYPE_ORDER: DocumentType[] = ['CNIC', 'DEGREE', 'OFFER_LETTER', 'OTHER'];

function DocumentsTab() {
  const { id } = useParams({ from: '/_app/people/$id/documents' });
  const employeeId = Number(id);
  const { token } = useToken();
  const [uploadOpen, setUploadOpen] = useState(false);

  const docsQuery = useQuery({
    queryKey: ['people', 'documents', employeeId],
    queryFn: ({ signal }) => documentsApi.list(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  if (docsQuery.isLoading) return <Skeleton active paragraph={{ rows: 5 }} />;

  if (docsQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load documents"
        description={getErrorMessage(docsQuery.error)}
      />
    );
  }

  const docs = docsQuery.data ?? [];
  const grouped = TYPE_ORDER.map((type) => ({
    type,
    docs: docs.filter((d) => d.docType === type),
  }));

  const empty = docs.length === 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <Paragraph type="secondary" style={{ margin: 0 }}>
          Upload CNIC scans, degrees, offer letters, and contract documents.
        </Paragraph>
        <Button
          type="primary"
          icon={<UploadIcon size={16} strokeWidth={1.75} />}
          onClick={() => setUploadOpen(true)}
        >
          Upload document
        </Button>
      </div>

      {empty ? (
        <EmptyState
          icon={Folder}
          title="No documents on file"
          description="Upload CNIC scans, degrees, offer letters, and any other employee paperwork."
          action={
            <Button
              type="primary"
              onClick={() => setUploadOpen(true)}
              icon={<UploadIcon size={16} />}
            >
              Upload document
            </Button>
          }
        />
      ) : (
        grouped.map(({ type, docs }) => {
          if (docs.length === 0) return null;
          const Icon = DOC_TYPE_ICON[type];
          return (
            <Section
              key={type}
              title={
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <Icon size={16} strokeWidth={1.75} color={token.colorPrimary} />
                  {DOC_TYPE_LABEL[type]}
                  <Text type="secondary" style={{ fontWeight: 400 }}>
                    {' · '}{docs.length}
                  </Text>
                </span>
              }
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
                  gap: 12,
                }}
              >
                {docs.map((doc) => (
                  <DocumentCard
                    key={doc.id}
                    title={doc.title ?? '—'}
                    fileName={objectKeyName(doc.objectKey)}
                    contentType={doc.contentType}
                    sizeBytes={doc.sizeBytes}
                    actions={<DocumentActions employeeId={employeeId} doc={doc} />}
                  />
                ))}
              </div>
            </Section>
          );
        })
      )}

      {uploadOpen ? (
        <UploadModal
          employeeId={employeeId}
          onClose={() => setUploadOpen(false)}
        />
      ) : null}
    </div>
  );
}

function DocumentActions({
  employeeId,
  doc,
}: {
  employeeId: number;
  doc: EmployeeDocumentDto;
}) {
  const { message } = App.useApp();
  const qc = useQueryClient();

  const deleteMutation = useMutation({
    mutationFn: () => documentsApi.delete(employeeId, doc.id!),
    onSuccess: () => {
      message.success('Document removed.');
      void qc.invalidateQueries({ queryKey: ['people', 'documents', employeeId] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  return (
    <>
      {doc.downloadUrl ? (
        <Tooltip title="Open in a new tab">
          <Button
            type="text"
            size="small"
            href={doc.downloadUrl}
            target="_blank"
            rel="noreferrer"
            icon={<ExternalLink size={14} />}
          />
        </Tooltip>
      ) : null}
      {doc.downloadUrl ? (
        <Tooltip title="Download">
          <Button
            type="text"
            size="small"
            href={doc.downloadUrl}
            download
            icon={<ArrowDownToLine size={14} />}
          />
        </Tooltip>
      ) : null}
      <Popconfirm
        title="Delete document?"
        description="The document and its file will both be deleted."
        okType="danger"
        okText="Delete"
        cancelText="Cancel"
        onConfirm={() => deleteMutation.mutate()}
      >
        <Tooltip title="Delete">
          <Button
            type="text"
            size="small"
            danger
            loading={deleteMutation.isPending}
            icon={<Trash2 size={14} />}
          />
        </Tooltip>
      </Popconfirm>
    </>
  );
}

/* --------------------- Upload modal --------------------- */

function UploadModal({
  employeeId,
  onClose,
}: {
  employeeId: number;
  onClose: () => void;
}) {
  type UploadForm = { docType: DocumentType; title: string };
  const [form] = Form.useForm<UploadForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Two-step pattern from employee.md §7:
  //   1. POST /employees/{id}/documents/upload-url  →  { uploadUrl, objectKey }
  //   2. PUT file directly to MinIO via uploadUrl
  //   3. POST /employees/{id}/documents             →  { docType, title, objectKey, contentType }
  const submit = async (values: UploadForm) => {
    if (!file) {
      setTopLevel('Pick a file to upload.');
      return;
    }
    setTopLevel(null);
    setUploading(true);
    try {
      const { uploadUrl, objectKey } = await documentsApi.uploadUrl(employeeId, {
        docType: values.docType,
        filename: file.name,
        contentType: file.type || undefined,
        bytes: file.size,
      });
      const putRes = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: file.type ? { 'Content-Type': file.type } : undefined,
      });
      if (!putRes.ok) {
        throw new Error(`Upload failed (${putRes.status})`);
      }
      await documentsApi.record(employeeId, {
        docType: values.docType,
        title: values.title.trim(),
        objectKey,
        contentType: file.type || undefined,
      });
      message.success('Document uploaded.');
      void qc.invalidateQueries({ queryKey: ['people', 'documents', employeeId] });
      onClose();
    } catch (e) {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <Modal
      open
      title="Upload document"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Upload"
      confirmLoading={uploading}
      destroyOnHidden
      width={520}
    >
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form form={form} layout="vertical" onFinish={submit} initialValues={{ docType: 'OTHER' }}>
        <Form.Item
          name="docType"
          label="Type"
          rules={[{ required: true }]}
        >
          <Select
            options={TYPE_ORDER.map((t) => ({ value: t, label: DOC_TYPE_LABEL[t] }))}
          />
        </Form.Item>
        <Form.Item
          name="title"
          label="Title"
          rules={[{ required: true, message: 'Give the document a title' }]}
        >
          <Input placeholder="e.g. CNIC scan front, Bachelor degree, Offer letter v2" />
        </Form.Item>
        <Form.Item label="File" required>
          <Upload.Dragger
            multiple={false}
            beforeUpload={(f) => {
              setFile(f);
              return false;
            }}
            onRemove={() => setFile(null)}
            fileList={
              file
                ? [
                    {
                      uid: '-1',
                      name: file.name,
                      size: file.size,
                      status: 'done' as const,
                    },
                  ]
                : []
            }
          >
            <p style={{ marginBottom: 4 }}>Click or drag a file here</p>
            <p style={{ color: 'var(--ant-color-text-secondary)', fontSize: 12 }}>
              Uploads use a secure direct link — large files don't need to travel
              through the app first.
            </p>
          </Upload.Dragger>
          <input ref={inputRef} type="file" hidden />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function objectKeyName(key?: string): string | undefined {
  if (!key) return undefined;
  const idx = key.lastIndexOf('/');
  return idx >= 0 ? key.slice(idx + 1) : key;
}
