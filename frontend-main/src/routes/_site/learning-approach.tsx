import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ArrowRight,
  BookOpen,
  Calculator,
  Camera,
  ClipboardList,
  Ear,
  FlaskConical,
  HelpCircle,
  MessagesSquare,
  NotebookPen,
  Palette,
  Search,
  Share2,
  Sparkles,
  Telescope,
} from 'lucide-react';
import { LEARNING_ZONES } from '@/site/content';
import { usePageMeta } from '@/site/hooks';
import { PlantInvestigation } from '@/site/PlantInvestigation';
import { CtaBand, Hl, PageHero, PhotoSlot, Reveal, SectionHead } from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/learning-approach')({
  component: LearningApproachPage,
});

const CYCLE = [
  { title: tr('Wonder'), body: tr('Something sparks a question.'), icon: Sparkles },
  { title: tr('Predict'), body: tr('Children share what they think.'), icon: HelpCircle },
  { title: tr('Investigate'), body: tr('Hands-on testing and exploring.'), icon: Search },
  { title: tr('Observe'), body: tr('Looking closely and noticing.'), icon: Telescope },
  { title: tr('Reflect'), body: tr('What happened, and why?'), icon: MessagesSquare },
  { title: tr('Share'), body: tr('Explaining ideas to others.'), icon: Share2 },
];

const EXAMPLES = [
  { area: tr('Science'), title: tr('Shadows on the playground'), body: tr('Children draw around their shadows, then come back later to see how they moved.'), icon: FlaskConical },
  { area: tr('Mathematics'), title: tr('The class market'), body: tr('Counting and “buying” with real objects makes numbers real.'), icon: Calculator },
  { area: tr('Language'), title: tr('Story builders'), body: tr('Children retell a story with puppets, then make up a new ending.'), icon: BookOpen },
  { area: tr('Creative'), title: tr('Design a bird feeder'), body: tr('Children plan, build and test a feeder — then watch which birds visit.'), icon: Palette },
];

const TEACHER = [
  { title: tr('Observes'), body: tr('Notices what each child enjoys and is ready to learn.'), icon: Camera },
  { title: tr('Listens'), body: tr('Takes children’s ideas seriously.'), icon: Ear },
  { title: tr('Asks'), body: tr('Uses open questions — “What do you notice?” “How could we find out?”'), icon: HelpCircle },
  { title: tr('Guides'), body: tr('Adds new words and skills at the right time.'), icon: Sparkles },
];

function LearningApproachPage() {
  usePageMeta({
    title: tr('Learning Approach'),
    path: '/learning-approach',
    description:
      tr('How children learn at The Spirit of Islam School: through play, questions, hands-on activities and gentle progress checks.'),
  });

  return (
    <>
      <PageHero
        eyebrow={tr('Learning approach')}
        title={
          <>
            {tr('Less Sitting Still.')}{' '}<Hl>{tr('More Real Discovery.')}</Hl>
          </>
        }
        lede={tr('Children learn by doing: exploring, building, talking and thinking. Teachers guide them instead of lecturing.')}
        photo="science"
      />

      {/* Guided discovery */}
      <section className="s-section" aria-labelledby="gd-title">
        <div className="s-container s-split s-split--wide">
          <div>
            <SectionHead
              eyebrow={tr('Learning through play')}
              title={
                <span id="gd-title">
                  {tr('Play With a')}{' '}<Hl>{tr('Purpose')}</Hl>
                </span>
              }
            />
            <Reveal delay={1}>
              <p className="s-lede">
                {tr('Play is how young children understand the world. Our teachers plan it carefully, set small challenges and join in to stretch thinking.')}</p>
              <p className="s-lede">
                {tr('Children make real choices, but play always has a learning goal.')}</p>
            </Reveal>
          </div>
          <Reveal variant="mask">
            <PhotoSlot photo="play" shape="blob" ratio="1 / 1" />
          </Reveal>
        </div>
      </section>

      {/* Inquiry cycle */}
      <section className="s-section s-section--tint" aria-labelledby="cycle-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('The learning cycle')}
            title={
              <span id="cycle-title">
                {tr('Every Investigation')}{' '}<Hl>{tr('Follows a Rhythm')}</Hl>
              </span>
            }
            lede={tr('Each answer leads to a new question — so the cycle starts again.')}
            align="center"
          />
          <ol className="s-cycle">
            {CYCLE.map((c, i) => {
              const Icon = c.icon;
              return (
                <Reveal as="li" key={c.title} delay={i} variant="scale" className="s-cycle__item" style={{ ['--i' as string]: i }}>
                  <span className="s-cycle__icon" aria-hidden="true">
                    <Icon size={22} strokeWidth={1.75} />
                  </span>
                  <span className="s-cycle__n">{i + 1}</span>
                  <h3 className="s-h4">{c.title}</h3>
                  <p>{c.body}</p>
                </Reveal>
              );
            })}
          </ol>
        </div>
      </section>

      {/* Examples */}
      <section className="s-section" aria-labelledby="ex-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('In the classroom')}
            title={<span id="ex-title">{tr('Real Examples')}</span>}
          />
          <ul className="s-cards-4">
            {EXAMPLES.map((e, i) => {
              const Icon = e.icon;
              return (
                <Reveal as="li" key={e.title} delay={i} className="s-card s-card--lift">
                  <span className="s-card__icon" aria-hidden="true">
                    <Icon size={22} strokeWidth={1.75} />
                  </span>
                  <span className="s-card__tag">{e.area}</span>
                  <h3 className="s-h4">{e.title}</h3>
                  <p>{e.body}</p>
                </Reveal>
              );
            })}
          </ul>
        </div>
      </section>

      {/* Interactive investigation */}
      <section className="s-section s-section--tint" id="investigation" aria-labelledby="inv-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('A classroom investigation')}
            title={
              <span id="inv-title">
                {tr('What Do Plants')}{' '}<Hl>{tr('Need to Grow?')}</Hl>
              </span>
            }
            lede={tr('Follow a real classroom investigation, then try it yourself.')}
          />
          <Reveal variant="scale">
            <PlantInvestigation />
          </Reveal>
        </div>
      </section>

      {/* Teacher role */}
      <section className="s-section" aria-labelledby="teacher-title">
        <div className="s-container s-split">
          <Reveal variant="mask">
            <PhotoSlot photo="reflect" shape="arch" ratio="4 / 4.6" />
          </Reveal>
          <div>
            <SectionHead
              eyebrow={tr('The teacher’s role')}
              title={
                <span id="teacher-title">
                  {tr('A Guide')}{' '}<Hl>{tr('Beside the Child')}</Hl>
                </span>
              }
            />
            <ul className="s-principles">
              {TEACHER.map((t, i) => {
                const Icon = t.icon;
                return (
                  <Reveal as="li" key={t.title} delay={i}>
                    <span className="s-principles__icon" aria-hidden="true">
                      <Icon size={20} strokeWidth={1.75} />
                    </span>
                    <div>
                      <h3 className="s-h4">{t.title}</h3>
                      <p>{t.body}</p>
                    </div>
                  </Reveal>
                );
              })}
            </ul>
          </div>
        </div>
      </section>

      {/* Learning zones */}
      <section className="s-section s-section--tint" aria-labelledby="zones-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Classroom learning zones')}
            title={
              <span id="zones-title">
                {tr('A Classroom Designed')}{' '}<Hl>{tr('for Discovery')}</Hl>
              </span>
            }
            lede={tr('Each classroom has inviting corners, with materials children can reach and use on their own.')}
          />
          <ul className="s-zones">
            {LEARNING_ZONES.map((z, i) => {
              const Icon = z.icon;
              return (
                <Reveal as="li" key={z.title} delay={i % 4} className="s-zone">
                  <Icon size={22} strokeWidth={1.75} aria-hidden="true" />
                  <div>
                    <h3 className="s-h4">{z.title}</h3>
                    <p>{z.body}</p>
                  </div>
                </Reveal>
              );
            })}
          </ul>
        </div>
      </section>

      {/* Assessment */}
      <section className="s-section" aria-labelledby="assess-title">
        <div className="s-container">
          <SectionHead
            eyebrow={tr('Tracking progress')}
            title={
              <span id="assess-title">
                {tr('Noticing Progress,')}{' '}<Hl>{tr('Not Testing Children')}</Hl>
              </span>
            }
            lede={tr('Young children show what they know through what they do and say. We watch, take notes and share them with you — no stressful tests.')}
          />
          <ul className="s-cards-3">
            {[
              { t: tr('Observation'), b: tr('Teachers note what children say and do each day.'), icon: Camera },
              { t: tr('Learning journals'), b: tr('Photos, drawings and children’s own words show growth over time.'), icon: NotebookPen },
              { t: tr('Conversations'), b: tr('Regular chats with parents about progress and next steps.'), icon: ClipboardList },
            ].map((a, i) => {
              const Icon = a.icon;
              return (
                <Reveal as="li" key={a.t} delay={i} className="s-card">
                  <span className="s-card__icon" aria-hidden="true">
                    <Icon size={22} strokeWidth={1.75} />
                  </span>
                  <h3 className="s-h4">{a.t}</h3>
                  <p>{a.b}</p>
                </Reveal>
              );
            })}
          </ul>
        </div>
      </section>

      <CtaBand
        title={
          <>
            {tr('Questions About Our')}{' '}<Hl>{tr('Approach?')}</Hl>
          </>
        }
        body={tr('We would love to talk about how your child learns best.')}
      >
        <Link to="/admissions" hash="enquiry" className="s-btn s-btn--light">
          {tr('Enquire About Admissions')}<ArrowRight size={18} aria-hidden="true" />
        </Link>
        <Link to="/programmes" className="s-btn s-btn--outline-light">
          {tr('Explore Our Programmes')}</Link>
      </CtaBand>
    </>
  );
}
