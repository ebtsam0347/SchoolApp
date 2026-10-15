import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight } from 'lucide-react';
import { SCHOOL } from '@/site/config';
import { ContactDetails } from '@/site/ContactDetails';
import { EnquiryForm } from '@/site/EnquiryForm';
import { usePageMeta } from '@/site/hooks';
import { CtaBand, Hl, PageHero, SectionHead } from '@/site/ui';

export const Route = createFileRoute('/_site/contact')({
  component: ContactPage,
});

function ContactPage() {
  usePageMeta({
    title: 'Contact',
    path: '/contact',
    description: 'Get in touch with The Spirit of Islam School with a question or to arrange a school visit.',
  });
  const { mapEmbedUrl, address } = SCHOOL.contact;

  return (
    <>
      <PageHero
        eyebrow="Contact"
        title={
          <>
            Let’s <Hl>Talk</Hl>
          </>
        }
        lede="Have a question about our approach, programmes or admissions? Send us a message and we’ll get back to you."
        photo="partnership"
      />

      <section className="s-section" aria-labelledby="contact-title">
        <div className="s-container s-form-layout">
          <div>
            <SectionHead eyebrow="Get in touch" title={<span id="contact-title">Contact Details</span>} />
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
                title={address ? `Map showing ${address}` : 'School location map'}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            )}
          </div>
          <div className="s-form-card">
            <h2 className="s-h3">Send us a message</h2>
            <EnquiryForm defaultType="general" />
          </div>
        </div>
      </section>

      <CtaBand
        title={
          <>
            Prefer to See Us <Hl>in Person?</Hl>
          </>
        }
        body="Arrange a school visit and experience our approach first-hand."
      >
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--light">
          Arrange a School Visit
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
