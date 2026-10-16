// Shared building blocks for the public website.

import { Link } from '@tanstack/react-router';
import { ArrowRight, Camera } from 'lucide-react';
import { useId, useState, type CSSProperties, type ReactNode } from 'react';
import { PHOTOS, PILLARS, type PhotoKey } from './content';
import { useReveal } from './hooks';
import { usePublishedPhoto } from './published-photos';

// ---------------------------------------------------------------------------
// Reveal

type RevealTag = 'div' | 'li' | 'section' | 'article' | 'figure' | 'header';

export function Reveal({
  children,
  as = 'div',
  variant = 'rise',
  delay = 0,
  className,
  style,
  id,
}: {
  children: ReactNode;
  as?: RevealTag;
  variant?: 'rise' | 'fade' | 'mask' | 'scale';
  /** Stagger index; each step adds 80ms. */
  delay?: number;
  className?: string;
  style?: CSSProperties;
  id?: string;
}) {
  const ref = useReveal<HTMLDivElement>();
  const Tag = as as 'div';
  return (
    <Tag
      ref={ref}
      id={id}
      data-reveal={variant}
      className={className}
      style={{ ...style, ['--reveal-delay' as string]: `${delay * 80}ms` }}
    >
      {children}
    </Tag>
  );
}

// ---------------------------------------------------------------------------
// Typography helpers

/** Highlighted words inside a heading (royal blue + gold underline; gold on dark). */
export function Hl({ children }: { children: ReactNode }) {
  return <em className="s-hl">{children}</em>;
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <span className="s-eyebrow">
      <span className="s-eyebrow__dot" aria-hidden="true" />
      {children}
    </span>
  );
}

export function SectionHead({
  eyebrow,
  title,
  lede,
  align = 'left',
  as: H = 'h2',
  className,
}: {
  eyebrow?: string;
  title: ReactNode;
  lede?: ReactNode;
  align?: 'left' | 'center';
  as?: 'h1' | 'h2';
  className?: string;
}) {
  return (
    <Reveal as="header" className={`s-head s-head--${align} ${className ?? ''}`}>
      {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
      <H className="s-h2">{title}</H>
      {lede && <p className="s-lede">{lede}</p>}
    </Reveal>
  );
}

/** Text link with an arrow that slides on hover. */
export function ArrowLink({ children }: { children: ReactNode }) {
  return (
    <span className="s-arrow-link">
      {children}
      <ArrowRight size={16} strokeWidth={2} aria-hidden="true" />
    </span>
  );
}

// ---------------------------------------------------------------------------
// Photos — the image an admin published for this slot (see
// published-photos.ts), else PHOTOS[key].src, else an illustrated placeholder.

export function PhotoSlot({
  photo,
  shape = 'rounded',
  ratio = '4 / 3',
  priority = false,
  tone = 'light',
  className,
}: {
  photo: PhotoKey;
  shape?: 'rounded' | 'arch' | 'blob' | 'pill';
  ratio?: string;
  priority?: boolean;
  tone?: 'light' | 'dark';
  className?: string;
}) {
  const p = PHOTOS[photo];
  const Icon = p.icon;
  const published = usePublishedPhoto(photo);
  const src = published?.url ?? p.src;
  // A URL that failed to load (deleted object, network) falls back to the
  // placeholder instead of showing a broken-image icon.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  return (
    <figure
      className={`s-photo s-photo--${shape} s-photo--${tone} ${className ?? ''}`}
      style={{ aspectRatio: ratio }}
    >
      {src && failedSrc !== src ? (
        <img
          src={src}
          alt={published?.alt || p.alt}
          onError={() => setFailedSrc(src)}
          loading={priority ? 'eager' : 'lazy'}
          fetchPriority={priority ? 'high' : undefined}
          decoding="async"
        />
      ) : (
        <div className="s-photo__ph" role="img" aria-label={p.alt}>
          <GeometricPattern className="s-photo__pattern" />
          <span className="s-photo__ring" aria-hidden="true" />
          <span className="s-photo__icon" aria-hidden="true">
            <Icon size={34} strokeWidth={1.5} />
          </span>
          <span className="s-photo__hint" aria-hidden="true">
            <Camera size={12} strokeWidth={2} />
            {p.hint}
          </span>
        </div>
      )}
    </figure>
  );
}

// ---------------------------------------------------------------------------
// Decoration

/** Interlaced eight-point star (khatam) tile — a quiet Islamic geometric texture. */
export function GeometricPattern({ className, size = 72 }: { className?: string; size?: number }) {
  const id = useId().replace(/:/g, '');
  const c = size / 2;
  const s = size * 0.36;
  return (
    <svg className={className} aria-hidden="true" focusable="false" width="100%" height="100%">
      <defs>
        <pattern id={`gp-${id}`} width={size} height={size} patternUnits="userSpaceOnUse">
          <g fill="none" stroke="currentColor" strokeWidth="1">
            <rect x={c - s / 2} y={c - s / 2} width={s} height={s} />
            <rect x={c - s / 2} y={c - s / 2} width={s} height={s} transform={`rotate(45 ${c} ${c})`} />
            <circle cx={c} cy={c} r={s * 0.22} />
            <path d={`M0 0L${c - s * 0.7} ${c - s * 0.7}M${size} 0L${c + s * 0.7} ${c - s * 0.7}M0 ${size}L${c - s * 0.7} ${c + s * 0.7}M${size} ${size}L${c + s * 0.7} ${c + s * 0.7}`} />
          </g>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={`url(#gp-${id})`} />
    </svg>
  );
}

/** Small four-point sparkle, gold by default. */
export function Sparkle({ className, size = 18 }: { className?: string; size?: number }) {
  return (
    <svg className={`s-sparkle ${className ?? ''}`} width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M12 0c.6 5.6 3.4 9.4 12 12-8.6 2.6-11.4 6.4-12 12-.6-5.6-3.4-9.4-12-12C8.6 9.4 11.4 5.6 12 0z"
        fill="currentColor"
      />
    </svg>
  );
}

export function LeafMark({ className }: { className?: string }) {
  return (
    <svg className={`s-leaf ${className ?? ''}`} viewBox="0 0 64 64" aria-hidden="true">
      <path
        d="M10 54C10 26 28 10 56 8c-2 28-18 46-46 46z"
        fill="currentColor"
        opacity=".14"
      />
      <path
        d="M10 54C10 26 28 10 56 8c-2 28-18 46-46 46zM10 54L44 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Page hero for inner pages

export function PageHero({
  eyebrow,
  title,
  lede,
  photo,
  children,
}: {
  eyebrow: string;
  title: ReactNode;
  lede: ReactNode;
  photo: PhotoKey;
  children?: ReactNode;
}) {
  return (
    <section className="s-page-hero">
      <GeometricPattern className="s-page-hero__pattern" />
      <div className="s-container s-page-hero__grid">
        <div className="s-page-hero__copy s-enter">
          <Eyebrow>{eyebrow}</Eyebrow>
          <h1 className="s-h1">{title}</h1>
          <p className="s-lede s-lede--lg">{lede}</p>
          {children && <div className="s-actions">{children}</div>}
        </div>
        <div className="s-page-hero__media">
          <Sparkle className="s-page-hero__spark" size={22} />
          <LeafMark className="s-page-hero__leaf" />
          <PhotoSlot photo={photo} shape="arch" ratio="4 / 4.4" priority className="s-enter-mask" />
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Call-to-action band (royal → bright blue)

export function CtaBand({
  title,
  body,
  children,
}: {
  title: ReactNode;
  body: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="s-section s-section--tight">
      <div className="s-container">
        <Reveal className="s-cta-band" variant="scale">
          <GeometricPattern className="s-cta-band__pattern" />
          <Sparkle className="s-cta-band__spark" size={20} />
          <div className="s-cta-band__copy">
            <h2 className="s-h2 s-h2--light">{title}</h2>
            <p>{body}</p>
          </div>
          <div className="s-actions">{children}</div>
        </Reveal>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Six learning pillars (bento grid)

export function PillarsGrid() {
  return (
    <div className="s-pillars">
      {PILLARS.map((p, i) => {
        const Icon = p.icon;
        const feature = i === 0;
        return (
          <Reveal
            as="article"
            key={p.id}
            delay={i}
            className={`s-pillar ${feature ? 's-pillar--feature' : ''} ${i === 1 ? 's-pillar--dark' : ''}`}
          >
            {feature && <PhotoSlot photo="thinking" shape="rounded" ratio="16 / 9" className="s-pillar__photo" />}
            <span className="s-pillar__icon" aria-hidden="true">
              <Icon size={22} strokeWidth={1.75} />
            </span>
            <h3 className="s-h3">{p.title}</h3>
            <p>{p.body}</p>
            <Link to={p.link.to} className="s-pillar__link">
              <ArrowLink>{p.link.label}</ArrowLink>
            </Link>
          </Reveal>
        );
      })}
    </div>
  );
}

/** Small "planned approach" disclaimer used on programme / admissions pages. */
export function ProvisionalNote({ children }: { children: ReactNode }) {
  return (
    <p className="s-note" role="note">
      <Sparkle size={14} />
      <span>{children}</span>
    </p>
  );
}
