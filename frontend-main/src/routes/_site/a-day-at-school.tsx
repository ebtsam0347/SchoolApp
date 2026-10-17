import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, Baby, GraduationCap } from 'lucide-react';
import { DAY_DETAIL } from '@/site/content';
import { usePageMeta } from '@/site/hooks';
import { CtaBand, Hl, PageHero, PhotoSlot, ProvisionalNote, Reveal, SectionHead } from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/a-day-at-school')({
  component: DayPage,
});

function DayPage() {
  usePageMeta({
    title: tr('A Day at School'),
    path: '/a-day-at-school',
    description:
      tr('Picture your child’s day at The Spirit of Islam School: morning circle, hands-on discovery, outdoor play, creative centres, Islamic stories and reflection.'),
  });

  return (
    <>
      <PageHero
        eyebrow={tr('A day at school')}
        title={
          <>
            {tr('A Day of Wonder,')}{' '}<Hl>{tr('Warmth and Discovery')}</Hl>
          </>
        }
        lede={tr('Every day balances calm routines with exciting discoveries, active play with quiet reflection.')}
        photo="story"
      />

      <section className="s-section" aria-labelledby="day-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('The rhythm of the day')}
            title={<span id="day-title">{tr('From Arrival to Home Time')}</span>}
          />
          <ProvisionalNote>
            {tr('This is an example rhythm. The exact schedule will depend on your child’s age and the school’s operating model, and will be shared with families once confirmed.')}</ProvisionalNote>

          <ol className="s-daylong">
            {DAY_DETAIL.map((d, i) => {
              const Icon = d.icon;
              return (
                <li key={d.title} className={`s-daylong__item ${d.photo ? 'has-photo' : ''}`}>
                  <span className="s-daylong__marker" aria-hidden="true">
                    <Icon size={20} strokeWidth={1.75} />
                  </span>
                  <Reveal className="s-daylong__card">
                    <span className="s-step-num">{String(i + 1).padStart(2, '0')}</span>
                    <h3 className="s-h3">{d.title}</h3>
                    <p>{d.body}</p>
                    <dl className="s-daylong__ages">
                      <div>
                        <dt>
                          <Baby size={16} aria-hidden="true" /> {tr('Younger children')}</dt>
                        <dd>{d.younger}</dd>
                      </div>
                      <div>
                        <dt>
                          <GraduationCap size={16} aria-hidden="true" /> {tr('Older children')}</dt>
                        <dd>{d.older}</dd>
                      </div>
                    </dl>
                  </Reveal>
                  {d.photo && (
                    <Reveal variant="mask" className="s-daylong__photo">
                      <PhotoSlot photo={d.photo} shape="rounded" ratio="4 / 3" />
                    </Reveal>
                  )}
                </li>
              );
            })}
          </ol>
        </div>
      </section>

      <CtaBand
        title={
          <>
            {tr('See It')}{' '}<Hl>{tr('for Yourself')}</Hl>
          </>
        }
        body={tr('Arrange a visit to experience our learning spaces and meet the team.')}
      >
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--light">
          {tr('Arrange a School Visit')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
