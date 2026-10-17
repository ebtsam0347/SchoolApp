import { useEffect, useLayoutEffect, useRef } from 'react';
import { SCHOOL, siteUrl } from './config';
import { isUrdu } from './i18n';

// ---------------------------------------------------------------------------
// Scroll reveal: one shared IntersectionObserver marks elements with
// `data-revealed` the first time they enter the viewport. site.css only hides
// [data-reveal] elements under prefers-reduced-motion: no-preference, and
// browsers without IntersectionObserver reveal immediately.

let observer: IntersectionObserver | null = null;

function getObserver(): IntersectionObserver | null {
  if (observer) return observer;
  if (typeof window === 'undefined' || !('IntersectionObserver' in window)) return null;
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        (entry.target as HTMLElement).dataset.revealed = '';
        observer?.unobserve(entry.target);
      }
    },
    { rootMargin: '0px 0px -6% 0px', threshold: 0.08 },
  );
  return observer;
}

export function useReveal<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const o = getObserver();
    if (!o) {
      el.dataset.revealed = '';
      return;
    }
    o.observe(el);
    return () => o.unobserve(el);
  }, []);
  return ref;
}

// ---------------------------------------------------------------------------
// Per-page <title>, description, canonical and Open Graph tags.

function upsertMeta(attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    el.dataset.siteMeta = '';
    document.head.appendChild(el);
  }
  el.content = content;
}

export function usePageMeta({
  title,
  description,
  path,
}: {
  /** Page name; null for the home page. */
  title: string | null;
  description: string;
  path: string;
}) {
  useEffect(() => {
    const fullTitle = title ? `${title} | ${SCHOOL.name}` : `${SCHOOL.name} — ${SCHOOL.tagline}`;
    const url = siteUrl() + path;
    document.title = fullTitle;

    upsertMeta('name', 'description', description);
    upsertMeta('property', 'og:type', 'website');
    upsertMeta('property', 'og:site_name', SCHOOL.name);
    upsertMeta('property', 'og:title', fullTitle);
    upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:url', url);
    upsertMeta('property', 'og:image', siteUrl() + SCHOOL.logo);
    upsertMeta('name', 'twitter:card', 'summary');

    let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!canonical) {
      canonical = document.createElement('link');
      canonical.rel = 'canonical';
      document.head.appendChild(canonical);
    }
    canonical.href = url;

    return () => {
      document.title = SCHOOL.name;
      canonical?.remove();
    };
  }, [title, description, path]);
}

// ---------------------------------------------------------------------------
// <html lang dir> for the chosen website language (i18n.ts). Restored when the
// visitor leaves the public site for the staff portal, which is English-only.

export function useSiteLanguage() {
  useLayoutEffect(() => {
    const html = document.documentElement;
    const prev = { lang: html.lang, dir: html.dir };
    html.lang = isUrdu ? 'ur' : 'en';
    html.dir = isUrdu ? 'rtl' : 'ltr';
    return () => {
      html.lang = prev.lang || 'en';
      html.dir = prev.dir || 'ltr';
    };
  }, []);
}
