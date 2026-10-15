// Tiny User-Agent classifier — kept for surfaces that only have a raw UA
// string (the self sign-in events feed). Sessions now carry parsed
// `deviceFamily` / `browser` / `os` directly from auth-service, so prefer
// those fields when available.

import type { BrowserInfo, DeviceFamily, OsInfo } from '@/auth';
import { Laptop, Smartphone, Tablet, type LucideIcon } from 'lucide-react';

export type { DeviceFamily };

export type UserAgentKind = 'browser' | 'script';

export type ParsedUserAgent = {
  kind: UserAgentKind;
  browser: string | null;
  os: string | null;
  /** Short script name when `kind === 'script'` (e.g. "curl", "python-urllib"). */
  script: string | null;
  /** One-line label suitable for display ("Chrome on macOS" / "Script (curl)"). */
  label: string;
};

export function deviceFamilyFromUA(ua: string | null | undefined): DeviceFamily {
  if (!ua) return 'unknown';
  if (scriptFromUA(ua)) return 'unknown';
  if (/iPad|Tablet/i.test(ua)) return 'tablet';
  if (/iPhone|Android.+Mobile|Mobile|Phone|Windows Phone/i.test(ua)) return 'mobile';
  if (/Mozilla|Chrome|Safari|Firefox|Edge|Opera/i.test(ua)) return 'desktop';
  return 'unknown';
}

export function browserFromUA(ua: string | null | undefined): string | null {
  if (!ua) return null;
  if (scriptFromUA(ua)) return null;
  if (/Edg\//.test(ua)) return 'Edge';
  if (/OPR\//.test(ua)) return 'Opera';
  if (/Firefox\//.test(ua)) return 'Firefox';
  if (/Chrome\//.test(ua)) return 'Chrome';
  if (/Version\/.+Safari\//.test(ua)) return 'Safari';
  return null;
}

export function osFromUA(ua: string | null | undefined): string | null {
  if (!ua) return null;
  if (scriptFromUA(ua)) return null;
  if (/Windows NT 10/.test(ua)) return 'Windows';
  if (/Mac OS X/.test(ua)) return 'macOS';
  if (/iPhone|iPad/.test(ua)) return 'iOS';
  if (/Android/.test(ua)) return 'Android';
  if (/Linux/.test(ua)) return 'Linux';
  return null;
}

// Recognise non-browser clients. Order is most-specific-first; the returned
// string is the short canonical name we want to show in "Script (…)".
function scriptFromUA(ua: string): string | null {
  if (/^Python-urllib/i.test(ua)) return 'python-urllib';
  if (/^python-requests/i.test(ua)) return 'python-requests';
  if (/^aiohttp/i.test(ua)) return 'aiohttp';
  if (/^httpx/i.test(ua)) return 'httpx';
  if (/^curl\//i.test(ua)) return 'curl';
  if (/^Wget/i.test(ua)) return 'wget';
  if (/^Go-http-client/i.test(ua)) return 'go-http';
  if (/^Java\//i.test(ua)) return 'java';
  if (/^okhttp\//i.test(ua)) return 'okhttp';
  if (/^node(-fetch|\.js)?\//i.test(ua)) return 'node';
  if (/^axios\//i.test(ua)) return 'axios';
  if (/^got\s*\(/i.test(ua)) return 'got';
  if (/PostmanRuntime/i.test(ua)) return 'postman';
  if (/insomnia/i.test(ua)) return 'insomnia';
  if (/HTTPie/i.test(ua)) return 'httpie';
  return null;
}

/** Parse a raw UA into `{ kind, browser, os, script, label }`. Always returns a label. */
export function parseUserAgent(ua: string | null | undefined): ParsedUserAgent {
  if (!ua || !ua.trim()) {
    return { kind: 'browser', browser: null, os: null, script: null, label: 'Unknown client' };
  }
  const script = scriptFromUA(ua);
  if (script) {
    return { kind: 'script', browser: null, os: null, script, label: `Script (${script})` };
  }
  const browser = browserFromUA(ua);
  const os = osFromUA(ua);
  const label =
    browser && os ? `${browser} on ${os}` : browser ?? os ?? 'Unknown client';
  return { kind: 'browser', browser, os, script: null, label };
}

/** Lucide icon for a device family — used across session cards. */
export function deviceFamilyIcon(family: DeviceFamily): LucideIcon {
  switch (family) {
    case 'mobile':
      return Smartphone;
    case 'tablet':
      return Tablet;
    default:
      return Laptop;
  }
}

/** "Chrome 119" / "Chrome" / null — server-supplied parsed browser. */
export function formatBrowser(browser: BrowserInfo | null | undefined): string | null {
  if (!browser) return null;
  return browser.version ? `${browser.name} ${browser.version}` : browser.name;
}

/** "macOS 14" / "macOS" / null — server-supplied parsed os. */
export function formatOs(os: OsInfo | null | undefined): string | null {
  if (!os) return null;
  return os.version ? `${os.name} ${os.version}` : os.name;
}
