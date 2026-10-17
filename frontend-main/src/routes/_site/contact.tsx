import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight } from 'lucide-react';
import { SCHOOL } from '@/site/config';
import { ContactDetails } from '@/site/ContactDetails';
import { EnquiryForm } from '@/site/EnquiryForm';
import { usePageMeta } from '@/site/hooks';
import { CtaBand, Hl, PageHero, SectionHead } from '@/site/ui';

import { tr, trf } from '@/site/i18n';
export const Route = createFileRoute('/_site/contact')({
  component: ContactPage,
});

function ContactPage() {
  usePageMeta({
    title: tr('Contact'),
    path: '/contact',
    description: tr('Contact The Spirit of Islam School with a question, or book a school visit.'),
  });
  const { mapEmbedUrl, address } = SCHOOL.contact;

  return (
    <>
      <PageHero
        eyebrow={tr('Contact')}
        title={
          <>
            {tr('Let’s')}{' '}<Hl>{tr('Talk')}</Hl>
          </>
        }
        lede={tr('Have a question? Send us a message and we will reply soon.')}
        photo="partnership"
      />

      <section className="s-section" aria-labelledby="contact-title">
        <div className="s-container s-form-layout">
          <div>
            <SectionHead eyebrow={tr('Get in touch')} title={<span id="contact-title">{tr('Contact Details')}</span>} />
            <ContactDetails />
            {SCHOOL.social.length > 0 && (
              <ul className="s-social">
                {SCHOOL.social.map((s) => (
                  <li key={s.href}>
                    <a href={s.href} target="_blank" rel="noopener noreferrer" className="s-chip">
                      {s.label}
                    </a>
                  </li>
                ))}
              </ul>
            )}
            {mapEmbedUrl && (
              <iframe
                className="s-map"
                src={mapEmbedUrl}
                title={address ? trf('Map showing {address}', { address }) : tr('School location map')}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            )}
          </div>
          <div className="s-form-card">
            <h2 className="s-h3">{tr('Send us a message')}</h2>
            <EnquiryForm defaultType="general" />
          </div>
        </div>
      </section>

      <CtaBand
        title={
          <>
            {tr('Prefer to See Us')}{' '}<Hl>{tr('in Person?')}</Hl>
          </>
        }
        body={tr('Book a visit and see our classrooms for yourself.')}
      >
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--light">
          {tr('Book a School Visit')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
