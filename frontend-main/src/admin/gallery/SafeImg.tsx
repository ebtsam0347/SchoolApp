// <img> that shows a neutral placeholder instead of the browser's broken-image
// icon when the file cannot be loaded (deleted object, offline, bad data).

import { useEffect, useState, type CSSProperties } from 'react';
import { theme as antdTheme } from 'antd';
import { ImageOff } from 'lucide-react';

export function SafeImg({
  src,
  alt,
  style,
  loading = 'lazy',
}: {
  src: string;
  alt: string;
  style?: CSSProperties;
  loading?: 'lazy' | 'eager';
}) {
  const { token } = antdTheme.useToken();
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);

  if (failed) {
    return (
      <span
        role="img"
        aria-label={`${alt} (image unavailable)`}
        style={{
          display: 'grid',
          placeItems: 'center',
          width: '100%',
          height: '100%',
          minHeight: 48,
          color: token.colorTextTertiary,
          background: token.colorFillTertiary,
        }}
      >
        <ImageOff size={22} strokeWidth={1.5} aria-hidden="true" />
      </span>
    );
  }
  return <img src={src} alt={alt} loading={loading} onError={() => setFailed(true)} style={style} />;
}
