import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, CalendarCheck, Droplets, Lightbulb, Sprout } from 'lucide-react';
import { useEffect } from 'react';
import { SCHOOL, siteUrl } from '@/site/config';
import {
  APPROACH,
  CONTACT_PRINCIPLES,
  DAY_GLIMPSE,
  GALLERY,
  SEQUENCE,
  VALUES,
} from '@/site/content';
import { FloatOrSink } from '@/site/FloatOrSink';
import { usePageMeta } from '@/site/hooks';
import {
  ArrowLink,
  Eyebrow,
  GeometricPattern,
  Hl,
  LeafMark,
  PhotoSlot,
  PillarsGrid,
  Reveal,
  SectionHead,
  Sparkle,
} from '@/site/ui';

import { isUrdu, tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/home')({
  component: HomePage,
});

function HomePage() {
  usePageMeta({
    title: null,
    path: '/home',
    description:
      tr('A modern Islamic early-years and kindergarten school where children explore, create, ask questions and grow in character through activity-based learning.'),
  });
  useSchoolJsonLd();

  return (
    <>
      <Hero />
      <Philosophy />
      <Pillars />
      <Discovery />
      <Approach />
      <Values />
      <Gallery />
      <DayGlimpse />
      <Partnership />
      <AdmissionsCta />
    </>
  );
}

/** Structured data limited to verified facts: name, logo, URL. */
function useSchoolJsonLd() {
  useEffect(() => {
    const el = document.createElement('script');
    el.type = 'application/ld+json';
    el.text = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'School',
      name: SCHOOL.name,
      slogan: SCHOOL.tagline,
      url: `${siteUrl()}/home`,
      logo: siteUrl() + SCHOOL.logo,
    });
    document.head.appendChild(el);
    return () => el.remove();
  }, []);
}

// --- A. Hero -----------------------------------------------------------------

function Hero() {
  return (
    <section className="s-hero" aria-labelledby="hero-title">
      <GeometricPattern className="s-hero__pattern" size={88} />
      <div className="s-hero__glow" aria-hidden="true" />
      <div className="s-container s-hero__grid">
        <div className="s-hero__copy">
          <div className="s-enter" style={{ ['--d' as string]: 0 }}>
            <Eyebrow>{tr('Modern Islamic Early Learning')}</Eyebrow>
          </div>
          <h1 id="hero-title" className="s-display s-enter" style={{ ['--d' as string]: 1 }}>
            {tr('Little Questions.')}{' '}<Hl>{tr('Big Discoveries.')}</Hl>
          </h1>
          <p className="s-lede s-lede--lg s-enter" style={{ ['--d' as string]: 2 }}>
            {tr('At')}{' '}{SCHOOL.name}{tr(', children explore, create, ask questions, and grow in character through meaningful experiences rooted in Islamic values.')}</p>
          <div className="s-actions s-enter" style={{ ['--d' as string]: 3 }}>
            <Link to="/learning-approach" className="s-btn s-btn--light">
              {tr('Discover Our Approach')}<ArrowRight size={18} aria-hidden="true" />
            </Link>
            <Link to="/admissions" hash="enquiry" className="s-btn s-btn--outline-light">
              {tr('Enquire About Admissions')}</Link>
          </div>
          <p className="s-hero__motto s-enter" style={{ ['--d' as string]: 4 }}>
            <span className="s-hero__rule" aria-hidden="true" />
            {SCHOOL.tagline}
            {isUrdu ? '۔' : '.'}
          </p>
        </div>

        <div className="s-hero__media">
          <PhotoSlot photo="hero" shape="blob" ratio="1 / 1.05" priority tone="dark" className="s-enter-mask" />
          <div className="s-float-label s-float-label--a">
            <Droplets size={16} aria-hidden="true" />
            {tr('Why does it float?')}</div>
          <div className="s-float-label s-float-label--b">
            <Sprout size={16} aria-hidden="true" />
            {tr('What do seeds need?')}</div>
          <div className="s-float-label s-float-label--c">
            <Lightbulb size={16} aria-hidden="true" />
            {tr('I have an idea!')}</div>
          <Sparkle className="s-hero__spark s-hero__spark--1" size={22} />
          <Sparkle className="s-hero__spark s-hero__spark--2" size={14} />
          <LeafMark className="s-hero__leaf" />
        </div>
      </div>
      <svg className="s-hero__curve" viewBox="0 0 1440 80" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 80V40C240 0 480 0 720 24s480 56 720 16v40z" fill="currentColor" />
      </svg>
    </section>
  );
}

// --- B. Philosophy statement ---------------------------------------------------

function Philosophy() {
  return (
    <section className="s-section" aria-labelledby="philosophy-title">
      <div className="s-container">
        <div className="s-split s-split--wide">
          <div>
            <Reveal>
              <Eyebrow>{tr('Our belief')}</Eyebrow>
              <h2 id="philosophy-title" className="s-h2 s-h2--xl">
                {tr('Education Is More Than')}{' '}<Hl>{tr('Memorising Answers.')}</Hl>
              </h2>
            </Reveal>
            <Reveal delay={1}>
              <p className="s-lede">
                {tr('We believe early childhood is a time to wonder, explore, build, imagine, and discover. Our approach combines Islamic values with meaningful experiences that help children develop knowledge, character, confidence, and curiosity.')}</p>
              <Link to="/our-philosophy" className="s-text-link">
                <ArrowLink>{tr('Read our philosophy')}</ArrowLink>
              </Link>
            </Reveal>
          </div>
          <Reveal variant="mask">
            <PhotoSlot photo="wonder" shape="arch" ratio="4 / 4.6" />
          </Reveal>
        </div>

        <ol className="s-sequence" aria-label={tr('How learning grows')}>
          {SEQUENCE.map((s, i) => {
            const Icon = s.icon;
            return (
              <Reveal as="li" key={s.label} delay={i} className="s-sequence__item">
                <span className="s-sequence__icon" aria-hidden="true">
                  <Icon size={22} strokeWidth={1.75} />
                </span>
                <span className="s-sequence__label">{s.label}</span>
              </Reveal>
            );
          })}
        </ol>
      </div>
    </section>
  );
}

// --- C. Pillars ------------------------------------------------------------

function Pillars() {
  return (
    <section className="s-section s-section--tint" aria-labelledby="pillars-title">
      <div className="s-container">
        <SectionHead
          eyebrow={tr('Six learning pillars')}
          title={
            <span id="pillars-title">
              {tr('A Whole-Child Approach to')}{' '}<Hl>{tr('Early Learning')}</Hl>
            </span>
          }
          lede={tr('Every experience is designed to grow the mind, the heart and the hands together.')}
        />
        <PillarsGrid />
      </div>
    </section>
  );
}

// --- D. Interactive discovery --------------------------------------------------

function Discovery() {
  return (
    <section className="s-section" id="discover" aria-labelledby="discover-title">
      <div className="s-container">
        <SectionHead
          eyebrow={tr('Try it yourself')}
          title={
            <span id="discover-title">
              {tr('Learning Begins With a')}{' '}<Hl>{tr('Question.')}</Hl>
            </span>
          }
          lede={tr('This is how a simple investigation unfolds in an early-years classroom. Choose an object, make a prediction and test it — just as our children will.')}
        />
        <Reveal variant="scale">
          <FloatOrSink />
        </Reveal>
      </div>
    </section>
  );
}

// --- E. Approach -------------------------------------------------------------

function Approach() {
  return (
    <section className="s-section s-section--tint" aria-labelledby="approach-title">
      <div className="s-container">
        <SectionHead
          eyebrow={tr('Our approach')}
          title={
            <span id="approach-title">
              {tr('Children Learn Best When They')}{' '}<Hl>{tr('Experience the World.')}</Hl>
            </span>
          }
        />
        <div className="s-approach">
          {APPROACH.map((a, i) => (
            <Reveal as="article" key={a.title} delay={i} className="s-approach__item">
              <PhotoSlot photo={a.photo} shape={i === 1 ? 'arch' : 'rounded'} ratio={i === 1 ? '4 / 5' : '4 / 3.4'} />
              <div className="s-approach__text">
                <span className="s-step-num">{a.step}</span>
                <h3 className="s-h3">{a.title}</h3>
                <p>{a.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
        <Reveal className="s-center">
          <Link to="/our-philosophy" className="s-btn s-btn--outline">
            {tr('Explore Our Learning Philosophy')}<ArrowRight size={18} aria-hidden="true" />
          </Link>
        </Reveal>
      </div>
    </section>
  );
}

// --- F. Values ----------------------------------------------------------------

function Values() {
  return (
    <section className="s-section s-section--dark" aria-labelledby="values-title">
      <GeometricPattern className="s-section__pattern" size={96} />
      <div className="s-container">
        <div className="s-values-head">
          <SectionHead
            eyebrow={tr('Islamic values')}
            title={
              <span id="values-title">
                {tr('Faith That')}{' '}<Hl>{tr('Shapes How We Live.')}</Hl>
              </span>
            }
          />
          <Reveal delay={1}>
            <p className="s-lede">
              {tr('Islamic learning is not a single lesson on the timetable. It is part of how children greet one another, care for their classroom, share, wait, and say thank you — every day.')}</p>
            <p className="s-lede">
              {tr('Children practise gratitude by noticing blessings, kindness by helping classmates, and responsibility by caring for shared materials and plants.')}</p>
          </Reveal>
        </div>
        <ul className="s-values">
          {VALUES.map((v, i) => {
            const Icon = v.icon;
            return (
              <Reveal as="li" key={v.translit} delay={i} className="s-value">
                <span className="s-value__icon" aria-hidden="true">
                  <Icon size={20} strokeWidth={1.75} />
                </span>
                <p className="s-value__ar" lang="ar" dir="rtl">
                  {v.arabic}
                </p>
                <h3 className="s-h3">
                  {v.english} <span className="s-value__tr">— {v.translit}</span>
                </h3>
                <p>{v.example}</p>
              </Reveal>
            );
          })}
        </ul>
      </div>
    </section>
  );
}

// --- G. Gallery --------------------------------------------------------------

function Gallery() {
  return (
    <section className="s-section" aria-labelledby="gallery-title">
      <div className="s-container">
        <SectionHead
          eyebrow={tr('Learning through real experiences')}
          title={
            <span id="gallery-title">
              {tr('Everyday Moments Become')}{' '}<Hl>{tr('Learning Opportunities.')}</Hl>
            </span>
          }
          align="center"
        />
        <ul className="s-mosaic">
          {GALLERY.map((g, i) => (
            <Reveal as="li" key={g.title} delay={i % 3} variant="mask" className={`s-mosaic__item s-mosaic__item--${i + 1}`}>
              <PhotoSlot photo={g.photo} shape="rounded" ratio="auto" />
              <div className="s-mosaic__cap">
                <h3>{g.title}</h3>
                <p>{g.caption}</p>
              </div>
            </Reveal>
          ))}
        </ul>
        <p className="s-center s-muted-note">{tr('Images illustrate our planned activities and do not show enrolled students.')}</p>
      </div>
    </section>
  );
}

// --- H. Day glimpse -----------------------------------------------------------

function DayGlimpse() {
  return (
    <section className="s-section s-section--tint" aria-labelledby="day-title">
      <div className="s-container">
        <SectionHead
          eyebrow={tr('A glimpse into the school day')}
          title={
            <span id="day-title">
              {tr('A Day Full of')}{' '}<Hl>{tr('Wonder')}</Hl>
            </span>
          }
          lede={tr('The actual schedule will vary by age group and programme, but every day follows a rhythm like this.')}
        />
        <ol className="s-timeline">
          {DAY_GLIMPSE.map((d, i) => {
            const Icon = d.icon;
            return (
              <Reveal as="li" key={d.title} delay={i} className="s-timeline__item">
                <span className="s-timeline__dot" aria-hidden="true">
                  <Icon size={20} strokeWidth={1.75} />
                </span>
                <div>
                  <h3 className="s-h4">{d.title}</h3>
                  <p>{d.body}</p>
                </div>
              </Reveal>
            );
          })}
        </ol>
        <Reveal className="s-center">
          <Link to="/a-day-at-school" className="s-text-link">
            <ArrowLink>{tr('See a day at school in detail')}</ArrowLink>
          </Link>
        </Reveal>
      </div>
    </section>
  );
}

// --- I. Partnership --------------------------------------------------------

function Partnership() {
  return (
    <section className="s-section" aria-labelledby="partner-title">
      <div className="s-container s-split">
        <Reveal variant="mask">
          <PhotoSlot photo="partnership" shape="blob" ratio="1 / 1" />
        </Reveal>
        <div>
          <SectionHead
            eyebrow={tr('Parent partnership')}
            title={
              <span id="partner-title">
                {tr('Growing Together,')}{' '}<Hl>{tr('Every Step of the Way.')}</Hl>
              </span>
            }
            lede={tr('Children thrive when families and educators work together. We aim to keep parents informed, celebrate progress, and build a supportive partnership around each child’s development.')}
          />
          <ul className="s-principles">
            {CONTACT_PRINCIPLES.map((p, i) => {
              const Icon = p.icon;
              return (
                <Reveal as="li" key={p.title} delay={i}>
                  <span className="s-principles__icon" aria-hidden="true">
                    <Icon size={20} strokeWidth={1.75} />
                  </span>
                  <div>
                    <h3 className="s-h4">{p.title}</h3>
                    <p>{p.body}</p>
                  </div>
                </Reveal>
              );
            })}
          </ul>
        </div>
      </div>
    </section>
  );
}

// --- J. Admissions CTA -----------------------------------------------------

function AdmissionsCta() {
  return (
    <section className="s-section s-section--tight" aria-labelledby="cta-title">
      <div className="s-container">
        <Reveal className="s-admit" variant="scale">
          <GeometricPattern className="s-admit__pattern" size={80} />
          <div className="s-admit__copy">
            <Sparkle size={20} />
            <h2 id="cta-title" className="s-h2 s-h2--light">
              {tr('Your Child’s Journey of Discovery')}{' '}<Hl>{tr('Starts Here.')}</Hl>
            </h2>
            <p>
              {tr('Learn more about our educational approach and explore whether')}{' '}{SCHOOL.name} {tr('is the right fit for your family.')}</p>
            <div className="s-actions">
              <Link to="/admissions" hash="enquiry" className="s-btn s-btn--light">
                {tr('Enquire About Admissions')}<ArrowRight size={18} aria-hidden="true" />
              </Link>
              <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--outline-light">
                <CalendarCheck size={18} aria-hidden="true" />
                {tr('Arrange a School Visit')}</Link>
            </div>
          </div>
          <div className="s-admit__media">
            <PhotoSlot photo="admissions" shape="arch" ratio="4 / 4.6" tone="dark" />
          </div>
        </Reveal>
      </div>
    </section>
  );
}
