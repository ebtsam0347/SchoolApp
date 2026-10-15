// School-specific details for the public website. This is the one place to
// edit contact information, social links and admissions status.
//
// Rule: only enter details the school has confirmed. Anything left `null`
// renders as "to be announced" — the site never invents contact details,
// opening hours, fees or dates.

export const SCHOOL = {
  name: 'The Spirit of Islam School',
  shortName: 'Spirit of Islam',
  tagline: 'Thinking Is Our Curriculum',
  supportingTagline: 'Rooted in Faith. Growing Through Discovery.',
  essence: 'Faith in the Heart. Curiosity in the Mind. Purpose in Every Discovery.',
  mission:
    'A modern Islamic early-years school where children explore, create and grow in character through meaningful, activity-based learning.',
  logo: '/icons/school-logo.png',
  logoAlt: 'The Spirit of Islam School crest',

  contact: {
    phone: null as string | null,
    email: null as string | null,
    whatsapp: null as string | null,
    address: null as string | null,
    /** Google Maps embed URL (https://www.google.com/maps/embed?...). */
    mapEmbedUrl: null as string | null,
    /** Office hours, e.g. "Mon – Fri, 8:00 AM – 2:00 PM". */
    officeHours: null as string | null,
  },

  /** Only real, active accounts. Example: { label: 'Instagram', href: 'https://instagram.com/…' } */
  social: [] as { label: string; href: string }[],

  /**
   * false → the admissions form is labelled as an expression of interest.
   * Flip to true once admissions are formally open.
   */
  admissionsOpen: false,
};

/** Public origin for canonical / Open Graph URLs. Falls back to the current origin. */
export function siteUrl(): string {
  const configured = (import.meta.env.VITE_SITE_URL as string | undefined)?.replace(/\/+$/, '');
  if (configured) return configured;
  return typeof window === 'undefined' ? '' : window.location.origin;
}

/** Cloudflare Pages Function that forwards enquiries — see functions/forms/enquiry.ts. */
export const ENQUIRY_ENDPOINT = '/forms/enquiry';

export const NAV = [
  { to: '/home', label: 'Home' },
  { to: '/our-philosophy', label: 'Our Philosophy' },
  { to: '/learning-approach', label: 'Learning Approach' },
  { to: '/programmes', label: 'Our Programmes' },
  { to: '/a-day-at-school', label: 'A Day at School' },
  { to: '/admissions', label: 'Admissions' },
  { to: '/contact', label: 'Contact' },
] as const;
