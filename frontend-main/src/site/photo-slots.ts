// Catalogue of the public website's manageable photo slots. Pure data with no
// imports so both the SPA and the Worker (worker/media.ts) can use it: the
// Worker only accepts assignments to keys listed here, and the admin UI uses
// the page / section labels to tell an admin exactly where a photo appears.
//
// A slot key is the same as the `PhotoKey` in content.ts. One slot can appear
// in several places (`usedOn`); assigning an image to the slot updates all of
// them. Keep this list in sync with the PHOTO_SPECS keys in content.ts.

export type SitePage = 'home' | 'our-philosophy' | 'learning-approach' | 'programmes' | 'a-day-at-school' | 'admissions' | 'contact';

export const SITE_PAGES: { key: SitePage; label: string; path: string }[] = [
  { key: 'home', label: 'Homepage', path: '/home' },
  { key: 'our-philosophy', label: 'Our Philosophy', path: '/our-philosophy' },
  { key: 'learning-approach', label: 'Learning Approach', path: '/learning-approach' },
  { key: 'programmes', label: 'Programmes', path: '/programmes' },
  { key: 'a-day-at-school', label: 'A Day at School', path: '/a-day-at-school' },
  { key: 'admissions', label: 'Admissions', path: '/admissions' },
  { key: 'contact', label: 'Contact', path: '/contact' },
];

export type PhotoSlotDef = {
  key: string;
  /** Admin-facing name, e.g. "Homepage Banner". */
  label: string;
  /** Page where the slot is primarily managed from. */
  page: SitePage;
  /** Section of that page. */
  section: string;
  /** Intended display ratio (width / height) — shown as a cropping hint. */
  ratio: string;
  /** Every place the photo is shown. */
  usedOn: string[];
};

export const PHOTO_SLOT_DEFS: PhotoSlotDef[] = [
  { key: 'hero', label: 'Homepage Banner', page: 'home', section: 'Hero', ratio: '1 / 1.05', usedOn: ['Homepage · Hero'] },
  {
    key: 'wonder',
    label: 'Wonder Photo',
    page: 'home',
    section: 'Introduction',
    ratio: '4 / 4.6',
    usedOn: ['Homepage · Introduction', 'Our Philosophy · Page banner'],
  },
  { key: 'thinking', label: 'Thinking Pillar Photo', page: 'home', section: 'Featured pillar', ratio: '16 / 9', usedOn: ['Homepage · Featured pillar'] },
  { key: 'explore', label: 'Approach — Explore', page: 'home', section: 'Our approach (step 01)', ratio: '4 / 3.4', usedOn: ['Homepage · Our approach'] },
  {
    key: 'create',
    label: 'Approach — Create',
    page: 'home',
    section: 'Our approach (step 02)',
    ratio: '4 / 5',
    usedOn: ['Homepage · Our approach', 'Homepage · Gallery mosaic (Building structures)'],
  },
  {
    key: 'reflect',
    label: 'Approach — Reflect',
    page: 'home',
    section: 'Our approach (step 03)',
    ratio: '4 / 3.4',
    usedOn: ['Homepage · Our approach', 'Learning Approach · Reflect'],
  },
  { key: 'art', label: 'Gallery — Art & construction', page: 'home', section: 'Gallery mosaic', ratio: 'auto', usedOn: ['Homepage · Gallery mosaic'] },
  {
    key: 'partnership',
    label: 'Parent Partnership Photo',
    page: 'home',
    section: 'Partnership',
    ratio: '1 / 1',
    usedOn: ['Homepage · Partnership', 'Contact · Page banner'],
  },
  {
    key: 'admissions',
    label: 'Admissions Photo',
    page: 'admissions',
    section: 'Page banner',
    ratio: '4 / 4.4',
    usedOn: ['Admissions · Page banner', 'Homepage · Admissions call-to-action', 'A Day at School · Arrival and settling in'],
  },
  {
    key: 'classroom',
    label: 'Classroom Photo',
    page: 'programmes',
    section: 'Page banner',
    ratio: '4 / 4.4',
    usedOn: ['Programmes · Page banner', 'Admissions · Classroom'],
  },
  {
    key: 'science',
    label: 'Practical Science Photo',
    page: 'learning-approach',
    section: 'Page banner',
    ratio: '4 / 4.4',
    usedOn: ['Learning Approach · Page banner', 'Homepage · Gallery mosaic'],
  },
  {
    key: 'play',
    label: 'Cooperative Play Photo',
    page: 'learning-approach',
    section: 'Learning through play',
    ratio: '1 / 1',
    usedOn: ['Learning Approach · Learning through play', 'Homepage · Gallery mosaic'],
  },
  {
    key: 'garden',
    label: 'Garden Photo',
    page: 'our-philosophy',
    section: 'Nature and responsibility',
    ratio: '4 / 4.8',
    usedOn: ['Our Philosophy · Nature and responsibility', 'Homepage · Gallery mosaic'],
  },
  {
    key: 'story',
    label: 'Story Time Photo',
    page: 'a-day-at-school',
    section: 'Page banner',
    ratio: '4 / 4.4',
    usedOn: ['A Day at School · Page banner', 'A Day at School · Language and literacy', 'Homepage · Gallery mosaic'],
  },
];

export const PHOTO_SLOT_KEYS = new Set(PHOTO_SLOT_DEFS.map((s) => s.key));

export const IMAGE_CATEGORIES = ['Classroom', 'Activities', 'Events', 'Nature & garden', 'Parents & community', 'General'] as const;
