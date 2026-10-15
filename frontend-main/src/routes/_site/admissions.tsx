import { createFileRoute, Link } from '@tanstack/react-router';
import { Check, ChevronDown, Lock } from 'lucide-react';
import { SCHOOL } from '@/site/config';
import { ADMISSIONS_STEPS } from '@/site/content';
import { EnquiryForm, type EnquiryType } from '@/site/EnquiryForm';
import { usePageMeta } from '@/site/hooks';
import { ContactDetails } from '@/site/ContactDetails';
import { Hl, PageHero, PhotoSlot, ProvisionalNote, Reveal, SectionHead } from '@/site/ui';

type Search = { type?: EnquiryType };

export const Route = createFileRoute('/_site/admissions')({
  validateSearch: (s: Record<string, unknown>): Search =>
    s.type === 'visit' || s.type === 'general' || s.type === 'admissions' ? { type: s.type } : {},
  component: AdmissionsPage,
});

const VISIT = [
  'A walk through our learning spaces and outdoor areas',
  'Time to see the materials children will use every day',
  'A conversation about our approach and your child',
  'Plenty of time for your questions',
];

const QUESTIONS = [
  { q: 'How will my child’s day balance play and learning?', a: 'Ask us to walk you through a typical day and how each activity has a learning purpose.' },
  { q: 'How are Islamic values taught to young children?', a: 'Ask how values are woven into routines, stories and relationships — not just separate lessons.' },
  { q: 'How will I know how my child is progressing?', a: 'Ask about observations, learning journals and how often we meet with families.' },
  { q: 'How do you help children settle in?', a: 'Ask about settling-in arrangements and how we support children who find separation difficult.' },
  { q: 'What does the learning environment look like?', a: 'Ask to see the learning zones, outdoor areas and the materials children use.' },
];

function AdmissionsPage() {
  const { type } = Route.useSearch();
  usePageMeta({
    title: 'Admissions',
    path: '/admissions',
    description:
      'Enquire about admissions or arrange a visit to The Spirit of Islam School, a modern Islamic early-years and kindergarten school.',
  });

  return (
    <>
      <PageHero
        eyebrow="Admissions"
        title={
          <>
            We’d Love to <Hl>Meet Your Family</Hl>
          </>
        }
        lede="Whether you are just curious or ready to plan ahead, we’re happy to answer your questions and show you our approach."
        photo="admissions"
      >
        <Link to="/admissions" hash="enquiry" search={{ type }} className="s-btn s-btn--primary">
          {SCHOOL.admissionsOpen ? 'Enquire Now' : 'Register Your Interest'}
        </Link>
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--outline">
          Arrange a Visit
        </Link>
      </PageHero>

      {/* Who for */}
      <section className="s-section" aria-labelledby="who-title">
        <div className="s-container">
          <SectionHead
            eyebrow="Who our programmes are for"
            title={
              <span id="who-title">
                Families Who Want <Hl>Faith and Curiosity</Hl> Together
              </span>
            }
            lede="Our proposed programmes are designed for children from around two to six years old, and for families who want a strong Islamic foundation alongside modern, activity-based early education."
          />
          {!SCHOOL.admissionsOpen && (
            <ProvisionalNote>
              Admissions are not open yet. We are currently welcoming expressions of interest — dates, fees and
              availability will be shared directly with interested families once confirmed.
            </ProvisionalNote>
          )}
        </div>
      </section>

      {/* Process */}
      <section className="s-section s-section--tint" aria-labelledby="process-title">
        <div className="s-container">
          <SectionHead eyebrow="The process" title={<span id="process-title">How Admissions Will Work</span>} />
          <ol className="s-process">
            {ADMISSIONS_STEPS.map((s, i) => {
              const Icon = s.icon;
              return (
                <Reveal as="li" key={s.title} delay={i} className="s-process__item">
                  <span className="s-process__n">{i + 1}</span>
                  <Icon size={22} strokeWidth={1.75} aria-hidden="true" />
                  <h3 className="s-h4">{s.title}</h3>
                  <p>{s.body}</p>
                </Reveal>
              );
            })}
          </ol>
        </div>
      </section>

      {/* Visit + questions */}
      <section className="s-section" aria-labelledby="visit-title">
        <div className="s-container s-split">
          <div>
            <SectionHead
              eyebrow="Visiting the school"
              title={
                <span id="visit-title">
                  What to Expect <Hl>From a Visit</Hl>
                </span>
              }
            />
            <ul className="s-ticks">
              {VISIT.map((v) => (
                <li key={v}>
                  <Check size={18} aria-hidden="true" />
                  {v}
                </li>
              ))}
            </ul>
            <Reveal variant="mask" className="s-mt">
              <PhotoSlot photo="classroom" shape="rounded" ratio="16 / 10" />
            </Reveal>
          </div>
          <div>
            <h3 className="s-h3">Questions you may wish to ask</h3>
            <div className="s-faq">
              {QUESTIONS.map((q) => (
                <details key={q.q} className="s-faq__item">
                  <summary>
                    {q.q}
                    <ChevronDown size={18} aria-hidden="true" />
                  </summary>
                  <p>{q.a}</p>
                </details>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Form */}
      <section className="s-section s-section--tint" id="enquiry" aria-labelledby="enquiry-title">
        <div className="s-container s-form-layout">
          <div>
            <SectionHead
              eyebrow={SCHOOL.admissionsOpen ? 'Admissions enquiry' : 'Expression of interest'}
              title={
                <span id="enquiry-title">
                  {SCHOOL.admissionsOpen ? 'Enquire About Admissions' : 'Register Your Interest'}
                </span>
              }
              lede={
                SCHOOL.admissionsOpen
                  ? 'Send us a few details and we’ll be in touch.'
                  : 'This form is an enquiry or expression of interest, not an application. Registering does not reserve a place.'
              }
            />
            <ContactDetails />
            <p className="s-privacy-mini">
              <Lock size={16} aria-hidden="true" />
              <span>
                We only use your details to respond to your enquiry. See our <Link to="/privacy">privacy notice</Link>.
              </span>
            </p>
          </div>
          <div className="s-form-card">
            <EnquiryForm key={type ?? 'admissions'} defaultType={type ?? 'admissions'} />
          </div>
        </div>
      </section>
    </>
  );
}
