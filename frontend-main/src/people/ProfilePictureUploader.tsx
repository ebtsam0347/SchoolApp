// Avatar upload. Three steps, per employee.md §7:
//
//   1. POST <base>/profile-picture/upload-url  → { uploadUrl, objectKey }
//   2. PUT  <uploadUrl>                        → bytes, straight to MinIO
//   3. POST <base>/profile-picture             → { objectKey }
//
// `<base>` is `/employees/me` for the signed-in user and `/employees/{id}`
// when HR edits someone else's record from the directory. The two differ
// only in permissions — the self routes are open to any employee, the
// by-id routes require `employee.write` — so one component drives both.
//
// The PUT bypasses `src/lib/http.ts` on purpose: it targets object storage
// on a different host, the presign expects no Authorization header, and an
// idempotency key would be ignored (same reasoning as chat/attachments.ts).
//
// Step 3's response comes off the write-side EmployeeDtoMapper, which passes
// null for `profilePictureUrl` — the signed GET URL is only minted on read
// paths. So the caller refetches instead of patching the cache from the
// response; `onUploaded` is the hook for that.

import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  App,
  Avatar,
  Button,
  Modal,
  Progress,
  Typography,
  theme as antdTheme,
} from 'antd';
import { ImageUp, Trash2 } from 'lucide-react';

import { getErrorMessage } from '@/lib';
import { employeeProfilePictureApi, myProfilePictureApi } from './api';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Mirrors `hrms.employee.documents.avatar-max-size-bytes` (default 2 MB).
 *  Checked client-side so an oversized pick fails before we burn a presign;
 *  the server re-checks at presign and again after the PUT. */
const MAX_BYTES = 2 * 1024 * 1024;

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

export type ProfilePictureUploaderProps = {
  open: boolean;
  onClose: () => void;
  /** Whose picture this is. Omit for the signed-in user (ESS routes); pass
   *  an id to edit another employee's record, which needs `employee.write`. */
  employeeId?: number;
  /** Shown in the modal title when editing someone else, so an HR user can
   *  see at a glance whose picture they're about to replace. */
  employeeName?: string;
  /** Current picture, used for the "before" preview. */
  currentUrl?: string | null;
  /** Initials fallback when there's no picture yet. */
  fallbackInitials?: string;
  /** Fired after step 3 succeeds. Refetch reads here — don't rely on the
   *  mutation response for the new image URL. */
  onUploaded: () => void;
};

export function ProfilePictureUploader({
  open,
  onClose,
  employeeId,
  employeeName,
  currentUrl,
  fallbackInitials,
  onUploaded,
}: ProfilePictureUploaderProps) {
  const { token } = useToken();
  const { message } = App.useApp();
  const inputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [percent, setPercent] = useState(0);

  // Object URLs are a manual resource — revoke on replace and on unmount,
  // otherwise repeated picks leak a blob per selection.
  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // Reset between openings so a cancelled pick doesn't reappear next time.
  useEffect(() => {
    if (!open) {
      setFile(null);
      setError(null);
      setPercent(0);
      setBusy(false);
    }
  }, [open]);

  const pick = (picked: File | undefined) => {
    if (!picked) return;
    if (!ACCEPTED.includes(picked.type)) {
      setError('Pick a JPEG, PNG or WebP image.');
      setFile(null);
      return;
    }
    if (picked.size > MAX_BYTES) {
      setError(
        `That image is ${formatBytes(picked.size)}. The limit is ${formatBytes(MAX_BYTES)}.`,
      );
      setFile(null);
      return;
    }
    setError(null);
    setPercent(0);
    setFile(picked);
  };

  const submit = async () => {
    if (!file) {
      setError('Pick an image first.');
      return;
    }
    setError(null);
    setBusy(true);
    setPercent(0);
    try {
      const presign = { filename: file.name, bytes: file.size };
      const { uploadUrl, objectKey } =
        employeeId === undefined
          ? await myProfilePictureApi.uploadUrl(presign)
          : await employeeProfilePictureApi.uploadUrl(employeeId, presign);

      await putToStorage(uploadUrl, file, (loaded, total) =>
        setPercent(Math.round((loaded / total) * 100)),
      );

      if (employeeId === undefined) {
        await myProfilePictureApi.set({ objectKey });
      } else {
        await employeeProfilePictureApi.set(employeeId, { objectKey });
      }

      message.success(
        employeeName
          ? `Profile picture updated for ${employeeName}.`
          : 'Profile picture updated.',
      );
      onUploaded();
      onClose();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const shownUrl = previewUrl ?? currentUrl ?? undefined;

  return (
    <Modal
      open={open}
      title={employeeName ? `Profile picture · ${employeeName}` : 'Profile picture'}
      onCancel={busy ? undefined : onClose}
      mask={{ closable: !busy }}
      closable={!busy}
      destroyOnHidden
      okText="Save picture"
      okButtonProps={{ disabled: !file || busy, loading: busy }}
      cancelButtonProps={{ disabled: busy }}
      onOk={() => void submit()}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 16,
          paddingTop: 8,
        }}
      >
        <Avatar
          size={128}
          src={shownUrl}
          style={{
            background: token.colorFillSecondary,
            color: token.colorTextSecondary,
            fontSize: 40,
            fontWeight: 600,
          }}
        >
          {fallbackInitials ?? '—'}
        </Avatar>

        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED.join(',')}
          style={{ display: 'none' }}
          onChange={(ev) => {
            pick(ev.target.files?.[0]);
            // Clear so re-picking the same file still fires onChange.
            ev.target.value = '';
          }}
        />

        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            icon={<ImageUp size={14} strokeWidth={1.75} />}
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            {file ? 'Choose a different image' : 'Choose image'}
          </Button>
          {file ? (
            <Button
              icon={<Trash2 size={14} strokeWidth={1.75} />}
              disabled={busy}
              onClick={() => {
                setFile(null);
                setError(null);
                setPercent(0);
              }}
            >
              Clear
            </Button>
          ) : null}
        </div>

        {file ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {file.name} · {formatBytes(file.size)}
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12, textAlign: 'center' }}>
            JPEG, PNG or WebP. Up to {formatBytes(MAX_BYTES)}. Square images
            look best.
          </Text>
        )}

        {busy ? (
          <Progress
            percent={percent}
            size="small"
            status={percent < 100 ? 'active' : 'success'}
            style={{ width: '100%' }}
          />
        ) : null}

        {error ? (
          <Alert
            type="error"
            showIcon
            title={error}
            style={{ width: '100%' }}
          />
        ) : null}
      </div>
    </Modal>
  );
}

/** XHR rather than fetch so we can report upload progress — browsers still
 *  don't surface upload progress on `fetch()`. */
function putToStorage(
  url: string,
  file: File,
  onProgress: (loaded: number, total: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url, true);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.upload.onprogress = (ev) => {
      if (ev.lengthComputable) onProgress(ev.loaded, ev.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(`Upload failed (${xhr.status})`));
    };
    xhr.onerror = () => reject(new Error('Upload failed (network error)'));
    xhr.send(file);
  });
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
