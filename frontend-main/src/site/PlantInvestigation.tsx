// Classroom investigation example for /learning-approach: "What do plants
// need to grow?" Visitors change the conditions for a bean plant, fast-forward
// a week and compare. Mirrors a real early-years fair-test investigation.

import { CalendarClock, Droplets, RotateCcw, Sun } from 'lucide-react';
import { useState } from 'react';

type Result = 'healthy' | 'pale' | 'dry' | 'none';

const RESULTS: Record<Result, { title: string; body: string; question: string }> = {
  healthy: {
    title: 'Tall, green and growing well',
    body: 'With water and light, the bean plant has grown strong stems and new leaves.',
    question: 'Which of the two do you think it needed most?',
  },
  pale: {
    title: 'Pale, thin and stretching',
    body: 'Without light, the plant grew tall and spindly with pale leaves — it is stretching to find the light.',
    question: 'What could we change so the leaves turn green again?',
  },
  dry: {
    title: 'Drooping and dry',
    body: 'With light but no water, the leaves are wilting and the soil has dried and cracked.',
    question: 'How can we tell when a plant is thirsty?',
  },
  none: {
    title: 'Hardly any growth',
    body: 'Without water or light, the seed has barely started to grow.',
    question: 'Is a seed alive before it starts to grow? How could we find out?',
  },
};

const STEPS = [
  { title: 'Question', child: '“Why did our plant on the cupboard go floppy?”', teacher: 'Notices the question and invites the group to wonder together.' },
  { title: 'Predict', child: 'Children guess what plants need and draw their ideas.', teacher: 'Accepts every idea and asks “What makes you think that?”' },
  { title: 'Plan', child: 'Children decide to change one thing at a time.', teacher: 'Introduces the idea of a fair test using simple language.' },
  { title: 'Test & observe', child: 'Children care for each pot and check it every day.', teacher: 'Photographs changes and models careful observation.' },
  { title: 'Reflect & share', child: 'Children compare pots and explain what they found.', teacher: 'Connects findings to gratitude for Allah’s creation and caring for living things.' },
];

function Plant({ result, grown }: { result: Result; grown: boolean }) {
  const r = grown ? result : 'none';
  const leaf = r === 'pale' ? 'var(--s-text-4)' : 'var(--s-royal)';
  return (
    <svg viewBox="0 0 160 200" className={`s-plant is-${r}`} role="img" aria-label={grown ? RESULTS[result].title : 'A pot with a newly planted bean seed'}>
      {/* stem + leaves */}
      {r === 'healthy' && (
        <g className="s-plant__grow" fill={leaf} stroke="var(--s-navy)" strokeWidth="2">
          <path d="M80 140V48" fill="none" strokeWidth="4" stroke="var(--s-royal)" />
          <path d="M80 96c-20-2-34-14-36-30 18 0 32 10 36 30z" />
          <path d="M80 80c18-2 32-14 34-30-18 0-30 10-34 30z" />
          <path d="M80 56c-12-2-20-10-22-22 12 0 20 8 22 22z" />
          <path d="M80 118c16 0 28-8 32-22-16-2-28 6-32 22z" />
        </g>
      )}
      {r === 'pale' && (
        <g className="s-plant__grow" fill={leaf} stroke="var(--s-text-3)" strokeWidth="1.5">
          <path d="M80 140C78 100 86 70 76 22" fill="none" strokeWidth="2.5" />
          <path d="M77 34c-8-1-13-6-14-12 8 0 12 5 14 12z" />
          <path d="M80 70c7-1 12-5 13-11-7 0-11 4-13 11z" />
        </g>
      )}
      {r === 'dry' && (
        <g className="s-plant__grow" fill="var(--s-text-4)" stroke="var(--s-navy)" strokeWidth="2">
          <path d="M80 140c0-22 4-34 16-44" fill="none" strokeWidth="3.5" stroke="var(--s-text-3)" />
          <path d="M96 96c12 6 16 18 12 30-10-6-14-16-12-30z" />
          <path d="M88 110c-14 4-22 14-20 26 12-4 18-14 20-26z" />
        </g>
      )}
      {r === 'none' && (
        <g fill="var(--s-royal)" stroke="var(--s-navy)" strokeWidth="2">
          <path d="M80 140v-10" fill="none" strokeWidth="3" />
          <path d="M80 132c-6 0-10-4-10-8 6 0 10 3 10 8z" />
        </g>
      )}
      {/* soil + pot */}
      <path d="M40 140h80" stroke="var(--s-navy)" strokeWidth="3" strokeLinecap="round" />
      <path d="M36 140h88l-10 50H46z" fill="var(--s-card)" stroke="var(--s-navy)" strokeWidth="3" strokeLinejoin="round" />
      <path d="M40 152h80" stroke="var(--s-gold)" strokeWidth="3" />
      {r === 'dry' && grown && <path d="M60 140l6-6 6 6M92 140l5-5 5 5" stroke="var(--s-text-3)" strokeWidth="2" fill="none" />}
    </svg>
  );
}

export function PlantInvestigation() {
  const [water, setWater] = useState(true);
  const [light, setLight] = useState(true);
  const [grown, setGrown] = useState(false);

  const result: Result = water && light ? 'healthy' : water ? 'pale' : light ? 'dry' : 'none';

  function toggle(set: (v: boolean) => void, value: boolean) {
    set(value);
    setGrown(false);
  }

  return (
    <div className="s-investigation">
      <ol className="s-investigation__steps">
        {STEPS.map((s, i) => (
          <li key={s.title}>
            <span className="s-investigation__n">{i + 1}</span>
            <div>
              <h4>{s.title}</h4>
              <p>
                <strong>Children: </strong>
                {s.child}
              </p>
              <p>
                <strong>Teacher: </strong>
                {s.teacher}
              </p>
            </div>
          </li>
        ))}
      </ol>

      <div className="s-investigation__lab">
        <p className="s-fos__q">What do plants need to grow?</p>
        <p className="s-investigation__intro">Change one condition, then fast-forward a week.</p>

        <div className="s-switches">
          <label className="s-switch">
            <input type="checkbox" role="switch" checked={water} onChange={(e) => toggle(setWater, e.target.checked)} />
            <span className="s-switch__track" aria-hidden="true" />
            <Droplets size={18} aria-hidden="true" />
            Water every day
          </label>
          <label className="s-switch">
            <input type="checkbox" role="switch" checked={light} onChange={(e) => toggle(setLight, e.target.checked)} />
            <span className="s-switch__track" aria-hidden="true" />
            <Sun size={18} aria-hidden="true" />
            Sunny windowsill
          </label>
        </div>

        <div className="s-investigation__stage">
          <Plant result={result} grown={grown} />
        </div>

        <div className="s-fos__actions">
          {!grown ? (
            <button type="button" className="s-btn s-btn--primary" onClick={() => setGrown(true)}>
              <CalendarClock size={18} aria-hidden="true" />
              Fast-forward one week
            </button>
          ) : (
            <button type="button" className="s-btn s-btn--ghost" onClick={() => setGrown(false)}>
              <RotateCcw size={16} aria-hidden="true" />
              Plant again
            </button>
          )}
        </div>

        <div className="s-fos__result" aria-live="polite">
          {grown ? (
            <>
              <p className="s-fos__verdict is-match">{RESULTS[result].title}</p>
              <p>{RESULTS[result].body}</p>
              <p className="s-investigation__q">
                <strong>Teacher asks: </strong>
                {RESULTS[result].question}
              </p>
            </>
          ) : (
            <p className="s-fos__wait">A bean seed has just been planted. What do you think will happen?</p>
          )}
        </div>
      </div>
    </div>
  );
}
