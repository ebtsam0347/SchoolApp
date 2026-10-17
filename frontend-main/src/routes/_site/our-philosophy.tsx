import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ArrowRight,
  BookHeart,
  Compass,
  Eye,
  HandHeart,
  Lightbulb,
  MessageCircle,
  Sparkles,
  Sprout,
  Target,
  Users,
} from 'lucide-react';
import { SCHOOL } from '@/site/config';
import { usePageMeta } from '@/site/hooks';
import {
  CtaBand,
  Eyebrow,
  GeometricPattern,
  Hl,
  PageHero,
  PhotoSlot,
  PillarsGrid,
  Reveal,
  SectionHead,
  Sparkle,
} from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/our-philosophy')({
  component: PhilosophyPage,
});

const BELIEFS = [
  { title: tr('Children are capable'), body: tr('Young children are thinkers and problem-solvers, not empty buckets to fill.'), icon: Lightbulb },
  { title: tr('Curiosity is a gift'), body: tr('Every question is a starting point. We protect children’s natural wonder.'), icon: Sparkles },
  { title: tr('Play is serious learning'), body: tr('Through play, children test ideas, learn new words and learn to share.'), icon: Compass },
  { title: tr('Relationships come first'), body: tr('Children learn best when they feel safe, known and loved.'), icon: HandHeart },
];

const HOPES = [
  tr('A love of learning'),
  tr('Confidence to ask questions'),
  tr('Kindness and empathy'),
  tr('Honesty and responsibility'),
  tr('Gratitude to Allah'),
  tr('Resilience when things are hard'),
  tr('Care for the natural world'),
  tr('Joy in working together'),
];

function PhilosophyPage() {
  usePageMeta({
    title: tr('Our Philosophy'),
    path: '/our-philosophy',
    description:
      tr('Why we started The Spirit of Islam School: Islamic values and modern early learning, working together.'),
  });

  return (
    <>
      <PageHero
        eyebrow={tr('Our philosophy')}
        title={
          <>
            {tr('Growing Minds.')}{' '}<Hl>{tr('Nurturing Hearts.')}</Hl>
          </>
        }
        lede={tr('A school where faith and curiosity grow side by side. Children learn to think deeply, act kindly and explore with wonder.')}
        photo="wonder"
      />

      {/* Vision & mission */}
      <section className="s-section" aria-labelledby="vm-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Why we exist')}
            title={<span id="vm-title">{tr('Our Vision and Mission')}</span>}
            lede={tr('As a new school, this is what we are working towards.')}
          />
          <div className="s-duo">
            <Reveal as="article" className="s-duo__card">
              <span className="s-duo__icon" aria-hidden="true">
                <Eye size={22} />
              </span>
              <h3 className="s-h3">{tr('Vision')}</h3>
              <p>
                {tr('Children who love learning, think for themselves and live by Islamic values.')}</p>
            </Reveal>
            <Reveal as="article" delay={1} className="s-duo__card s-duo__card--dark">
              <span className="s-duo__icon" aria-hidden="true">
                <Target size={22} />
              </span>
              <h3 className="s-h3">{tr('Mission')}</h3>
              <p>{SCHOOL.mission}</p>
            </Reveal>
          </div>
        </div>
      </section>

      {/* Thinking is our curriculum */}
      <section className="s-section s-section--dark" aria-labelledby="tioc-title">
        <GeometricPattern className="s-section__pattern" size={96} />
        <div className="s-container s-statement">
          <Reveal>
            <Eyebrow>{tr('Our tagline')}</Eyebrow>
            <h2 id="tioc-title" className="s-display s-display--md">
              {tr('“Thinking')}{' '}<Hl>{tr('Is Our Curriculum.')}</Hl>”
            </h2>
          </Reveal>
          <div className="s-statement__grid">
            {[
              { t: tr('Thinking before telling'), b: tr('Teachers ask “What do you notice?” before giving answers.') },
              { t: tr('Questions over worksheets'), b: tr('Children investigate, build and talk — instead of filling in pages.') },
              { t: tr('Understanding over memorising'), b: tr('Children remember what they discover and explain for themselves.') },
            ].map((x, i) => (
              <Reveal key={x.t} delay={i} className="s-statement__item">
                <Sparkle size={16} />
                <h3 className="s-h4">{x.t}</h3>
                <p>{x.b}</p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Faith + modern education */}
      <section className="s-section" aria-labelledby="faith-title">
        <div className="s-container s-split s-split--wide">
          <div>
            <SectionHead
              eyebrow={tr('Faith and modern education')}
              title={
                <span id="faith-title">
                  {tr('Rooted in Faith.')}{' '}<Hl>{tr('Growing Through Discovery.')}</Hl>
                </span>
              }
            />
            <Reveal delay={1}>
              <p className="s-lede">
                {tr('We don’t treat Islam and modern learning as separate subjects. Wonder at nature leads to thanking Allah for His creation. Honesty, patience and kindness are practised all day long.')}</p>
              <ul className="s-ticks">
                <li>
                  <BookHeart size={18} aria-hidden="true" /> {tr('Duas, Islamic stories and good manners in daily routines')}</li>
                <li>
                  <Sprout size={18} aria-hidden="true" /> {tr('Nature and science, explored with gratitude')}</li>
                <li>
                  <Users size={18} aria-hidden="true" /> {tr('Character built through real friendships, not lectures')}</li>
                <li>
                  <MessageCircle size={18} aria-hidden="true" /> {tr('Teaching methods based on early-childhood research')}</li>
              </ul>
            </Reveal>
          </div>
          <Reveal variant="mask">
            <PhotoSlot photo="garden" shape="arch" ratio="4 / 4.8" />
          </Reveal>
        </div>
      </section>

      {/* Pillars */}
      <section className="s-section s-section--tint" aria-labelledby="pillars-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('What your child will gain')}
            title={<span id="pillars-title">{tr('What Learning Looks Like Here')}</span>}
          />
          <PillarsGrid />
        </div>
      </section>

      {/* Childhood */}
      <section className="s-section" aria-labelledby="child-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('How we see childhood')}
            title={
              <span id="child-title">
                {tr('Childhood Is Not a Waiting Room.')}{' '}<Hl>{tr('It Matters Now.')}</Hl>
              </span>
            }
            align="center"
          />
          <ul className="s-cards-4">
            {BELIEFS.map((b, i) => {
              const Icon = b.icon;
              return (
                <Reveal as="li" key={b.title} delay={i} className="s-card">
                  <span className="s-card__icon" aria-hidden="true">
                    <Icon size={22} strokeWidth={1.75} />
                  </span>
                  <h3 className="s-h4">{b.title}</h3>
                  <p>{b.body}</p>
                </Reveal>
              );
            })}
          </ul>
        </div>
      </section>

      {/* Hopes */}
      <section className="s-section s-section--tint" aria-labelledby="hopes-title">
        <div className="s-container s-narrow">
          <SectionHead
            eyebrow={tr('Our hopes for every child')}
            title={<span id="hopes-title">{tr('What We Hope Your Child Will Gain')}</span>}
            lede={tr('We can’t promise results for any child, but everything we do aims to build these qualities.')}
            align="center"
          />
          <ul className="s-chips">
            {HOPES.map((h, i) => (
              <Reveal as="li" key={h} delay={i % 6} variant="fade" className="s-chip">
                {h}
              </Reveal>
            ))}
          </ul>
        </div>
      </section>

      <CtaBand
        title={
          <>
            {tr('See How It Works')}{' '}<Hl>{tr('in Practice')}</Hl>
          </>
        }
        body={tr('See the learning cycle, our classrooms and an activity you can try yourself.')}
      >
        <Link to="/learning-approach" className="s-btn s-btn--light">
          {tr('Explore Our Learning Approach')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
