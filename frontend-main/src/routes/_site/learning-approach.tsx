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

export const Route = createFileRoute('/_site/learning-approach')({
  component: LearningApproachPage,
});

const CYCLE = [
  { title: 'Wonder', body: 'Something sparks a question.', icon: Sparkles },
  { title: 'Predict', body: 'Children share what they think.', icon: HelpCircle },
  { title: 'Investigate', body: 'Hands-on testing and exploring.', icon: Search },
  { title: 'Observe', body: 'Looking closely and noticing.', icon: Telescope },
  { title: 'Reflect', body: 'What happened, and why?', icon: MessagesSquare },
  { title: 'Share', body: 'Explaining ideas to others.', icon: Share2 },
];

const EXAMPLES = [
  { area: 'Science', title: 'Shadows on the playground', body: 'Children trace their shadows in the morning and again later, then wonder why they moved.', icon: FlaskConical },
  { area: 'Mathematics', title: 'The class market', body: 'Counting, sorting and simple “buying” with real objects turns numbers into something meaningful.', icon: Calculator },
  { area: 'Language', title: 'Story builders', body: 'Children retell a favourite story with puppets, then invent a new ending together.', icon: BookOpen },
  { area: 'Creative', title: 'Design a bird feeder', body: 'Planning, building and testing a feeder for the garden — and watching who visits.', icon: Palette },
];

const TEACHER = [
  { title: 'Observes', body: 'Watches closely to understand what each child is curious about and ready for.', icon: Camera },
  { title: 'Listens', body: 'Takes children’s ideas seriously and builds learning around them.', icon: Ear },
  { title: 'Asks', body: 'Uses open questions — “What do you notice?” “How could we find out?”', icon: HelpCircle },
  { title: 'Guides', body: 'Introduces new words, skills and materials at the right moment.', icon: Sparkles },
];

function LearningApproachPage() {
  usePageMeta({
    title: 'Learning Approach',
    path: '/learning-approach',
    description:
      'How children learn at The Spirit of Islam School: guided discovery, purposeful play, the inquiry cycle, learning zones and gentle, observation-based assessment.',
  });

  return (
    <>
      <PageHero
        eyebrow="Learning approach"
        title={
          <>
            Less Passive Learning. <Hl>More Meaningful Discovery.</Hl>
          </>
        }
        lede="Children learn by doing — investigating, building, talking and reflecting — with teachers who guide rather than lecture."
        photo="science"
      />

      {/* Guided discovery */}
      <section className="s-section" aria-labelledby="gd-title">
        <div className="s-container s-split s-split--wide">
          <div>
            <SectionHead
              eyebrow="Guided discovery & purposeful play"
              title={
                <span id="gd-title">
                  Play With a <Hl>Purpose</Hl>
                </span>
              }
            />
            <Reveal delay={1}>
              <p className="s-lede">
                Play is how young children make sense of the world. In our classrooms, play is carefully planned:
                teachers prepare inviting materials, set gentle challenges and join in to extend thinking.
              </p>
              <p className="s-lede">
                Children have real choices, but they are never left to “just play”. Every activity has a learning
                intention — and room for children’s own ideas to take it somewhere new.
              </p>
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
            eyebrow="The inquiry cycle"
            title={
              <span id="cycle-title">
                Every Investigation <Hl>Follows a Rhythm</Hl>
              </span>
            }
            lede="Each answer leads to a new question — so the cycle starts again."
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
            eyebrow="In practice"
            title={<span id="ex-title">What It Looks Like Across Learning Areas</span>}
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
            eyebrow="A classroom investigation"
            title={
              <span id="inv-title">
                What Do Plants <Hl>Need to Grow?</Hl>
              </span>
            }
            lede="Follow a real early-years investigation from the first question to the final reflection — then run the experiment yourself."
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
              eyebrow="The teacher’s role"
              title={
                <span id="teacher-title">
                  A Guide <Hl>Beside the Child</Hl>
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
            eyebrow="Classroom learning zones"
            title={
              <span id="zones-title">
                A Classroom Designed <Hl>for Discovery</Hl>
              </span>
            }
            lede="Our planned classrooms are arranged into inviting zones, each with materials children can reach and use independently."
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
            eyebrow="Assessment"
            title={
              <span id="assess-title">
                Noticing Progress, <Hl>Not Testing Children</Hl>
              </span>
            }
            lede="Young children show what they know through what they do and say. Our assessment is gentle, ongoing and shared with families."
          />
          <ul className="s-cards-3">
            {[
              { t: 'Observation', b: 'Teachers record what children say and do during everyday activities.', icon: Camera },
              { t: 'Learning journals', b: 'Photos, drawings and quotes build a picture of each child’s growth over time.', icon: NotebookPen },
              { t: 'Conversations', b: 'Regular discussions with families about progress, interests and next steps.', icon: ClipboardList },
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
            Questions About Our <Hl>Approach?</Hl>
          </>
        }
        body="We would love to talk with you about how your child learns best."
      >
        <Link to="/admissions" hash="enquiry" className="s-btn s-btn--light">
          Enquire About Admissions
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
        <Link to="/programmes" className="s-btn s-btn--outline-light">
          Explore Our Programmes
        </Link>
      </CtaBand>
    </>
  );
}
