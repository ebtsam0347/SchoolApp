import { createFileRoute, Link } from '@tanstack/react-router';
import { Check, ChevronDown, Lock } from 'lucide-react';
import { SCHOOL } from '@/site/config';
import { ADMISSIONS_STEPS } from '@/site/content';
import { EnquiryForm, type EnquiryType } from '@/site/EnquiryForm';
import { usePageMeta } from '@/site/hooks';
import { ContactDetails } from '@/site/ContactDetails';
import { Hl, PageHero, PhotoSlot, ProvisionalNote, Reveal, SectionHead } from '@/site/ui';

import { tr } from '@/site/i18n';
type Search = { type?: EnquiryType };

export const Route = createFileRoute('/_site/admissions')({
  validateSearch: (s: Record<string, unknown>): Search =>
    s.type === 'visit' || s.type === 'general' || s.type === 'admissions' ? { type: s.type } : {},
  component: AdmissionsPage,
});

const VISIT = [
  tr('A tour of our classrooms and play areas'),
  tr('A look at the toys and materials children use'),
  tr('A friendly chat about your child'),
  tr('Time for all your questions'),
];

const QUESTIONS = [
  { q: tr('Will my child just play all day?'), a: tr('Play is how young children learn best. Every activity is planned with a clear learning goal.') },
  { q: tr('How do you teach Islamic values?'), a: tr('Through daily duas, short stories and good manners — practised every day, not in one lesson only.') },
  { q: tr('How will I know how my child is doing?'), a: tr('Teachers share notes, photos and regular updates, and meet parents during the year.') },
  { q: tr('My child has never been away from me. Will they settle in?'), a: tr('We start slowly, with short days and a familiar teacher, until your child feels safe and happy.') },
  { q: tr('What are the fees and school timings?'), a: tr('We will share fees and timings as soon as they are confirmed. Register your interest and you will hear first.') },
];

function AdmissionsPage() {
  const { type } = Route.useSearch();
  usePageMeta({
    title: tr('Admissions'),
    path: '/admissions',
    description:
      tr('Admissions at The Spirit of Islam School: register your interest or book a visit. For children aged about 2 to 6.'),
  });

  return (
    <>
      <PageHero
        eyebrow={tr('Admissions')}
        title={
          <>
            {tr('We’d Love to')}{' '}<Hl>{tr('Meet Your Family')}</Hl>
          </>
        }
        lede={tr('Just curious, or ready to plan ahead? Either way, we would love to show you around.')}
        photo="admissions"
      >
        <Link to="/admissions" hash="enquiry" search={{ type }} className="s-btn s-btn--primary">
          {SCHOOL.admissionsOpen ? tr('Enquire Now') : tr('Register Your Interest')}
        </Link>
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--outline">
          {tr('Book a Visit')}</Link>
      </PageHero>

      {/* Who for */}
      <section className="s-section" aria-labelledby="who-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Who we are for')}
            title={
              <span id="who-title">
                {tr('Families Who Want')}{' '}<Hl>{tr('Faith and Curiosity')}</Hl> {tr('Together')}</span>
            }
            lede={tr('For children aged about 2 to 6, and for families who want strong Islamic values with modern, hands-on learning.')}
          />
          {!SCHOOL.admissionsOpen && (
            <ProvisionalNote>
              {tr('Admissions are not open yet. Register your interest now and we will send you dates and fees as soon as they are confirmed.')}</ProvisionalNote>
          )}
        </div>
      </section>

      {/* Process */}
      <section className="s-section s-section--tint" aria-labelledby="process-title">
        <div className="s-container">
          <SectionHead eyebrow={tr('How it works')} title={<span id="process-title">{tr('Four Simple Steps')}</span>} />
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
              eyebrow={tr('Visiting the school')}
              title={
                <span id="visit-title">
                  {tr('What to Expect')}{' '}<Hl>{tr('From a Visit')}</Hl>
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
            <h3 className="s-h3">{tr('Parents often ask')}</h3>
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
              eyebrow={SCHOOL.admissionsOpen ? tr('Admissions enquiry') : tr('Register your interest')}
              title={
                <span id="enquiry-title">
                  {SCHOOL.admissionsOpen ? tr('Enquire About Admissions') : tr('Register Your Interest')}
                </span>
              }
              lede={
                SCHOOL.admissionsOpen
                  ? tr('Send a few details. We will get back to you soon.')
                  : tr('This is not an application, and it does not reserve a place. It simply lets us contact you first.')
              }
            />
            <ContactDetails />
            <p className="s-privacy-mini">
              <Lock size={16} aria-hidden="true" />
              <span>
                {tr('We only use your details to reply to you. Read our')}{' '}<Link to="/privacy">{tr('privacy notice')}</Link>.
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
