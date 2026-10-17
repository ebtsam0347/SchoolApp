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
      tr('See a typical day at The Spirit of Islam School: circle time, hands-on learning, outdoor play and Islamic stories.'),
  });

  return (
    <>
      <PageHero
        eyebrow={tr('A day at school')}
        title={
          <>
            {tr('A Happy Day of')}{' '}<Hl>{tr('Play and Discovery')}</Hl>
          </>
        }
        lede={tr('Calm routines, exciting discoveries and plenty of play — every single day.')}
        photo="story"
      />

      <section className="s-section" aria-labelledby="day-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('The rhythm of the day')}
            title={<span id="day-title">{tr('From Arrival to Home Time')}</span>}
          />
          <ProvisionalNote>
            {tr('This is a sample day. We will share the final timetable with families once it is confirmed.')}</ProvisionalNote>

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
        body={tr('Visit us, see the classrooms and meet the teachers.')}
      >
        <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--light">
          {tr('Book a School Visit')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
