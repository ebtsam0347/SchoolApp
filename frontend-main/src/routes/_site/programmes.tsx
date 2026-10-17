import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, Check, Puzzle, Target } from 'lucide-react';
import { useRef, useState, type KeyboardEvent } from 'react';
import { PROGRAMMES } from '@/site/content';
import { usePageMeta } from '@/site/hooks';
import { CtaBand, Hl, PageHero, ProvisionalNote, Reveal, SectionHead } from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/programmes')({
  component: ProgrammesPage,
});

function ProgrammesPage() {
  usePageMeta({
    title: tr('Our Programmes'),
    path: '/programmes',
    description:
      tr('Our planned programmes for ages 2–6: Early Years, Nursery, Kindergarten and School Readiness.'),
  });

  const [active, setActive] = useState(0);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const p = PROGRAMMES[active];

  function onKey(e: KeyboardEvent<HTMLButtonElement>) {
    const last = PROGRAMMES.length - 1;
    let next = active;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = active === last ? 0 : active + 1;
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = active === 0 ? last : active - 1;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = last;
    else return;
    e.preventDefault();
    setActive(next);
    tabs.current[next]?.focus();
  }

  return (
    <>
      <PageHero
        eyebrow={tr('Our programmes')}
        title={
          <>
            {tr('The Right Experience')}{' '}<Hl>{tr('for Every Stage')}</Hl>
          </>
        }
        lede={tr('Our programmes grow with your child — from first steps in learning to being ready for big school.')}
        photo="classroom"
      />

      <section className="s-section" aria-labelledby="prog-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Choose a stage')}
            title={<span id="prog-title">{tr('Explore Each Programme')}</span>}
          />
          <ProvisionalNote>
            {tr('These programmes are still being planned. Ages and details may change before admissions open.')}</ProvisionalNote>

          <div className="s-tabs" role="tablist" aria-label={tr('Programmes')}>
            {PROGRAMMES.map((prog, i) => (
              <button
                key={prog.id}
                ref={(el) => {
                  tabs.current[i] = el;
                }}
                id={`tab-${prog.id}`}
                type="button"
                role="tab"
                aria-selected={i === active}
                aria-controls={`panel-${prog.id}`}
                tabIndex={i === active ? 0 : -1}
                className="s-tab"
                onClick={() => setActive(i)}
                onKeyDown={onKey}
              >
                <span className="s-tab__name">{prog.name}</span>
                <span className="s-tab__age">{prog.ageBand}</span>
              </button>
            ))}
          </div>

          <div
            key={p.id}
            id={`panel-${p.id}`}
            role="tabpanel"
            aria-labelledby={`tab-${p.id}`}
            tabIndex={0}
            className="s-prog"
          >
            <div className="s-prog__intro">
              <h3 className="s-h2">{p.name}</h3>
              <p className="s-prog__age">{tr('Proposed age band:')}{' '}{p.ageBand} {tr('(to be confirmed)')}</p>
              <p className="s-lede">{p.stage}</p>
            </div>

            <div className="s-prog__cols">
              <div className="s-prog__box">
                <h4 className="s-h4">
                  <Target size={18} aria-hidden="true" /> {tr('Learning objectives')}</h4>
                <ul className="s-ticks s-ticks--sm">
                  {p.objectives.map((o) => (
                    <li key={o}>
                      <Check size={16} aria-hidden="true" />
                      {o}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="s-prog__box s-prog__box--tint">
                <h4 className="s-h4">
                  <Puzzle size={18} aria-hidden="true" /> {tr('Example activities')}</h4>
                <ul className="s-ticks s-ticks--sm">
                  {p.activities.map((a) => (
                    <li key={a}>
                      <Check size={16} aria-hidden="true" />
                      {a}
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            <dl className="s-prog__areas">
              {p.areas.map((a) => (
                <div key={a.label}>
                  <dt>{a.label}</dt>
                  <dd>{a.body}</dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      </section>

      <section className="s-section s-section--tint" aria-labelledby="common-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Across every programme')}
            title={
              <span id="common-title">
                {tr('One Philosophy,')}{' '}<Hl>{tr('Growing With Your Child')}</Hl>
              </span>
            }
          />
          <ul className="s-cards-3">
            {[
              { t: tr('Activity-based'), b: tr('Hands-on activities, play and projects at every stage.') },
              { t: tr('Values-centred'), b: tr('Islamic values guide daily routines and friendships.') },
              { t: tr('Child-paced'), b: tr('Teachers watch each child and adjust the challenge.') },
            ].map((x, i) => (
              <Reveal as="li" key={x.t} delay={i} className="s-card">
                <h3 className="s-h4">{x.t}</h3>
                <p>{x.b}</p>
              </Reveal>
            ))}
          </ul>
        </div>
      </section>

      <CtaBand
        title={
          <>
            {tr('Not Sure Which Stage')}{' '}<Hl>{tr('Fits?')}</Hl>
          </>
        }
        body={tr('Tell us your child’s age and we will suggest the right programme.')}
      >
        <Link to="/admissions" hash="enquiry" className="s-btn s-btn--light">
          {tr('Enquire About Admissions')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
        <Link to="/a-day-at-school" className="s-btn s-btn--outline-light">
          {tr('See a Day at School')}</Link>
      </CtaBand>
    </>
  );
}
