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

export const Route = createFileRoute('/_site/our-philosophy')({
  component: PhilosophyPage,
});

const BELIEFS = [
  { title: 'Children are capable', body: 'Young children are thinkers and problem-solvers, not empty vessels waiting to be filled.', icon: Lightbulb },
  { title: 'Curiosity is a gift', body: 'Every question is a starting point. We protect children’s natural wonder rather than rushing to answers.', icon: Sparkles },
  { title: 'Play is serious learning', body: 'Purposeful play is how young children test ideas, build language and learn to cooperate.', icon: Compass },
  { title: 'Relationships come first', body: 'Children learn best when they feel safe, known and loved by the adults around them.', icon: HandHeart },
];

const HOPES = [
  'A love of learning',
  'Curiosity and wonder',
  'Confidence to ask questions',
  'Kindness and empathy',
  'Honesty and responsibility',
  'Gratitude to Allah',
  'Independence',
  'Resilience when things are hard',
  'Clear communication',
  'Creative problem-solving',
  'Care for the natural world',
  'Joy in working together',
];

function PhilosophyPage() {
  usePageMeta({
    title: 'Our Philosophy',
    path: '/our-philosophy',
    description:
      'Why The Spirit of Islam School exists: Islamic values and modern early-years education working together so children grow in knowledge, character and curiosity.',
  });

  return (
    <>
      <PageHero
        eyebrow="Our philosophy"
        title={
          <>
            Growing Minds. <Hl>Nurturing Hearts.</Hl>
          </>
        }
        lede="We are building a school where faith and curiosity grow side by side — where children learn to think deeply, act kindly and discover the world with wonder."
        photo="wonder"
      />

      {/* Vision & mission */}
      <section className="s-section" aria-labelledby="vm-title">
        <div className="s-container">
          <SectionHead
            eyebrow="Why we exist"
            title={<span id="vm-title">Our Proposed Vision and Mission</span>}
            lede="As a new school, these statements describe what we are working towards."
          />
          <div className="s-duo">
            <Reveal as="article" className="s-duo__card">
              <span className="s-duo__icon" aria-hidden="true">
                <Eye size={22} />
              </span>
              <h3 className="s-h3">Vision</h3>
              <p>
                A generation of children who love learning, think for themselves and carry Islamic values into
                everything they do.
              </p>
            </Reveal>
            <Reveal as="article" delay={1} className="s-duo__card s-duo__card--dark">
              <span className="s-duo__icon" aria-hidden="true">
                <Target size={22} />
              </span>
              <h3 className="s-h3">Mission</h3>
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
            <Eyebrow>Our tagline</Eyebrow>
            <h2 id="tioc-title" className="s-display s-display--md">
              “Thinking <Hl>Is Our Curriculum.</Hl>”
            </h2>
          </Reveal>
          <div className="s-statement__grid">
            {[
              { t: 'Thinking before telling', b: 'Before giving an answer, teachers ask “What do you notice?” and “What do you think?”' },
              { t: 'Questions over worksheets', b: 'Children spend their time investigating, building and talking — not filling in pages.' },
              { t: 'Understanding over memorising', b: 'Knowledge sticks when children have discovered it, explained it and used it.' },
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
              eyebrow="Faith and modern education"
              title={
                <span id="faith-title">
                  Rooted in Faith. <Hl>Growing Through Discovery.</Hl>
                </span>
              }
            />
            <Reveal delay={1}>
              <p className="s-lede">
                We don’t see Islamic values and modern education as two separate subjects. Wonder at the natural world
                is a path to recognising Allah’s creation. Honesty, patience and kindness are practised in the
                block corner as much as in story time.
              </p>
              <ul className="s-ticks">
                <li>
                  <BookHeart size={18} aria-hidden="true" /> Islamic stories, duas and manners woven into daily routines
                </li>
                <li>
                  <Sprout size={18} aria-hidden="true" /> Nature and science explored with gratitude and awe
                </li>
                <li>
                  <Users size={18} aria-hidden="true" /> Character built through real relationships, not lectures
                </li>
                <li>
                  <MessageCircle size={18} aria-hidden="true" /> Developmentally appropriate methods from early-childhood
                  research
                </li>
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
            eyebrow="Six learning pillars"
            title={<span id="pillars-title">What Learning Looks Like Here</span>}
          />
          <PillarsGrid />
        </div>
      </section>

      {/* Childhood */}
      <section className="s-section" aria-labelledby="child-title">
        <div className="s-container">
          <SectionHead
            eyebrow="How we see childhood"
            title={
              <span id="child-title">
                Childhood Is Not a Waiting Room. <Hl>It Matters Now.</Hl>
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
            eyebrow="Our hopes for every child"
            title={<span id="hopes-title">What We Hope Children Will Develop</span>}
            lede="We can’t promise outcomes for any child — but these are the qualities everything we do is designed to nurture."
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
            See How It Works <Hl>in Practice</Hl>
          </>
        }
        body="Explore the inquiry cycle, learning zones and a classroom investigation you can try yourself."
      >
        <Link to="/learning-approach" className="s-btn s-btn--light">
          Explore Our Learning Approach
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
      </CtaBand>
    </>
  );
}
