// Two-step attachment upload:
//   1. POST /chat/attachments/upload-url → presigned PUT for MinIO
//   2. PUT  <presigned url>              → bytes (no bearer, no idempotency)
//
// The PUT bypasses our http wrapper because:
//   - it targets MinIO directly (different host),
//   - presigning expects no Authorization header,
//   - idempotency-key would be ignored.
// Returns an `AttachmentRef` ready to drop into `SendMessageRequest`.

import { attachmentsApi } from './api';
import type { AttachmentRef } from './types';

export type UploadProgress = (loaded: number, total: number) => void;

export async function uploadAttachment(
  file: File,
  onProgress?: UploadProgress,
): Promise<AttachmentRef> {
  const presigned = await attachmentsApi.uploadUrl({
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    originalName: file.name,
  });

  // XHR over fetch so we can report PUT progress; browsers still don't expose
  // upload-progress on `fetch()`. Wrap in a Promise for ergonomics.
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', presigned.uploadUrl, true);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    if (onProgress) {
      xhr.upload.onprogress = (ev) => {
        if (ev.lengthComputable) onProgress(ev.loaded, ev.total);
      };
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(`Upload failed (${xhr.status})`));
    };
    xhr.onerror = () => reject(new Error('Upload failed (network error)'));
    xhr.send(file);
  });

  return {
    objectKey: presigned.objectKey,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    originalName: file.name,
  };
}
