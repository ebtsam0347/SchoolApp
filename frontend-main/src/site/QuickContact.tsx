// Sticky quick-contact bar for phones: the shortest path from reading to
// reaching the school. "Book a Visit" always; WhatsApp too once a number is set
// in config.ts (opens a chat with a ready-made greeting). Hidden on the pages
// that already show the enquiry form.

import { Link, useRouterState } from '@tanstack/react-router';
import { CalendarCheck, MessageCircle } from 'lucide-react';
import { SCHOOL } from './config';
import { tr } from './i18n';

const FORM_PAGES = new Set(['/admissions', '/contact']);

export function QuickContact() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  if (FORM_PAGES.has(pathname)) return null;

  const wa = SCHOOL.contact.whatsapp?.replace(/[^\d]/g, '');
  const greeting = tr('Assalamu alaikum! I would like to know more about admissions at The Spirit of Islam School.');

  return (
    <>
      <div className="s-quick__spacer" aria-hidden="true" />
      <nav className="s-quick" aria-label={tr('Quick contact')}>
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--primary s-quick__btn">
          <CalendarCheck size={18} aria-hidden="true" />
          {tr('Book a Visit')}
        </Link>
        {wa && (
          <a
            href={`https://wa.me/${wa}?text=${encodeURIComponent(greeting)}`}
            target="_blank"
            rel="noopener noreferrer"
            className="s-btn s-quick__btn s-quick__wa"
          >
            <MessageCircle size={18} aria-hidden="true" />
            {tr('WhatsApp')}
          </a>
        )}
      </nav>
    </>
  );
}
