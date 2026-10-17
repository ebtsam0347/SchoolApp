// "Learning Begins With a Question" — a small float-or-sink investigation
// that demonstrates the Predict → Test → Observe → Discuss → Discover cycle.
// Pure frontend state; works with mouse, touch and keyboard.

import { FlaskConical, RotateCcw } from 'lucide-react';
import { useState } from 'react';

import { tr, trf } from '@/site/i18n';
type ObjectId = 'leaf' | 'stone' | 'block' | 'duck';
type Outcome = 'float' | 'sink';

const OBJECTS: Record<
  ObjectId,
  { label: string; outcome: Outcome; observe: string; discuss: string; discover: string }
> = {
  leaf: {
    label: tr('Leaf'),
    outcome: 'float',
    observe: tr('The leaf rests gently on the surface of the water.'),
    discuss: tr('What would happen if we folded the leaf into a tiny boat and added a pebble?'),
    discover: tr('Light, flat things spread their weight out, so the water can hold them up.'),
  },
  stone: {
    label: tr('Stone'),
    outcome: 'sink',
    observe: tr('The stone drops straight down to the bottom.'),
    discuss: tr('The stone is small — so why did it sink? Can we find a heavy thing that floats?'),
    discover: tr('Size is not the whole story. The stone is heavy for its size, so it sinks.'),
  },
  block: {
    label: tr('Wooden block'),
    outcome: 'float',
    observe: tr('The wooden block bobs on top, even though it looks solid.'),
    discuss: tr('Would a much bigger wooden block float too? How could we test it?'),
    discover: tr('Wood is lighter than the same amount of water, so it floats.'),
  },
  duck: {
    label: tr('Plastic duck'),
    outcome: 'float',
    observe: tr('The plastic duck floats and wobbles on the surface.'),
    discuss: tr('What do you think would happen if we filled the duck with water?'),
    discover: tr('The duck is hollow. The air trapped inside helps it float.'),
  },
};

const STEPS = [tr('Predict'), tr('Test'), tr('Observe'), tr('Discuss'), tr('Discover')] as const;

function ObjectArt({ id, size = 56 }: { id: ObjectId; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 64 64', 'aria-hidden': true } as const;
  switch (id) {
    case 'leaf':
      return (
        <svg {...common}>
          <path d="M8 50C10 24 28 10 56 10c-2 26-20 42-48 40z" fill="var(--s-bright)" opacity=".28" />
          <path d="M8 50C10 24 28 10 56 10c-2 26-20 42-48 40zM8 50l34-28" fill="none" stroke="var(--s-navy)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'stone':
      return (
        <svg {...common}>
          <path d="M10 40c0-12 10-22 24-22s22 8 22 18-8 16-24 16S10 50 10 40z" fill="var(--s-text-4)" />
          <path d="M10 40c0-12 10-22 24-22s22 8 22 18-8 16-24 16S10 50 10 40z" fill="none" stroke="var(--s-navy)" strokeWidth="2.4" />
          <path d="M22 32c4-3 9-4 14-3" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" opacity=".7" fill="none" />
        </svg>
      );
    case 'block':
      return (
        <svg {...common}>
          <path d="M12 22l20-10 20 10v22L32 54 12 44z" fill="var(--s-gold-light)" opacity=".55" />
          <path d="M12 22l20-10 20 10v22L32 54 12 44zM12 22l20 10 20-10M32 32v22" fill="none" stroke="var(--s-navy)" strokeWidth="2.4" strokeLinejoin="round" />
        </svg>
      );
    case 'duck':
      return (
        <svg {...common}>
          <path d="M14 38c0-4 4-6 8-6h4c-4-4-3-14 6-15 7-1 11 5 9 11l7-1c-1 4-4 6-8 6 6 2 10 6 10 11 0 7-8 10-18 10s-18-6-18-16z" fill="var(--s-gold-light)" />
          <path d="M14 38c0-4 4-6 8-6h4c-4-4-3-14 6-15 7-1 11 5 9 11l7-1c-1 4-4 6-8 6 6 2 10 6 10 11 0 7-8 10-18 10s-18-6-18-16z" fill="none" stroke="var(--s-navy)" strokeWidth="2.4" strokeLinejoin="round" />
          <circle cx="35" cy="23" r="2" fill="var(--s-navy)" />
        </svg>
      );
  }
}

export function FloatOrSink() {
  const [selected, setSelected] = useState<ObjectId | null>(null);
  const [prediction, setPrediction] = useState<Outcome | null>(null);
  const [tested, setTested] = useState(false);

  const obj = selected ? OBJECTS[selected] : null;
  const activeStep = !tested ? (prediction ? 1 : 0) : 4;

  function choose(id: ObjectId) {
    setSelected(id);
    setPrediction(null);
    setTested(false);
  }

  function reset() {
    setSelected(null);
    setPrediction(null);
    setTested(false);
  }

  const correct = obj && prediction === obj.outcome;

  return (
    <div className="s-fos">
      <ol className="s-fos__steps" aria-label={tr('Investigation steps')}>
        {STEPS.map((s, i) => (
          <li
            key={s}
            className={i <= activeStep ? 'is-done' : ''}
            aria-current={i === activeStep ? 'step' : undefined}
          >
            <span>{i + 1}</span>
            {s}
          </li>
        ))}
      </ol>

      <div className="s-fos__body">
        <div className="s-fos__panel">
          <p className="s-fos__q">{tr('Will it float or sink?')}</p>

          <fieldset className="s-fos__group">
            <legend>{tr('1. Choose an object')}</legend>
            <div className="s-fos__objects">
              {(Object.keys(OBJECTS) as ObjectId[]).map((id) => (
                <button
                  key={id}
                  type="button"
                  className="s-fos__obj"
                  aria-pressed={selected === id}
                  onClick={() => choose(id)}
                >
                  <ObjectArt id={id} size={44} />
                  <span>{OBJECTS[id].label}</span>
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className="s-fos__group" disabled={!selected || tested}>
            <legend>{tr('2. What do you predict?')}</legend>
            <div className="s-fos__predict">
              {(['float', 'sink'] as Outcome[]).map((o) => (
                <button
                  key={o}
                  type="button"
                  className="s-chip-btn"
                  aria-pressed={prediction === o}
                  onClick={() => setPrediction(o)}
                >
                  {tr('It will')}{' '}{tr(o)}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="s-fos__actions">
            <button
              type="button"
              className="s-btn s-btn--primary"
              disabled={!prediction || tested}
              onClick={() => setTested(true)}
            >
              <FlaskConical size={18} aria-hidden="true" />
              {tr('Test it')}</button>
            {selected && (
              <button type="button" className="s-btn s-btn--ghost" onClick={reset}>
                <RotateCcw size={16} aria-hidden="true" />
                {tr('Try another')}</button>
            )}
          </div>
        </div>

        <div className="s-fos__stage">
          <div className={`s-tank ${tested && obj ? `is-${obj.outcome}` : ''} ${selected ? 'has-object' : ''}`}>
            <div className="s-tank__water" aria-hidden="true">
              <svg className="s-tank__wave" viewBox="0 0 200 12" preserveAspectRatio="none">
                <path d="M0 6c20-8 30 8 50 0s30 8 50 0 30 8 50 0 30 8 50 0V12H0z" fill="currentColor" />
              </svg>
            </div>
            {selected && (
              <div className="s-tank__obj">
                <ObjectArt id={selected} size={64} />
              </div>
            )}
            {!selected && <p className="s-tank__hint">{tr('Pick an object to begin')}</p>}
          </div>

          <div className="s-fos__result" aria-live="polite">
            {tested && obj ? (
              <>
                <p className={`s-fos__verdict ${correct ? 'is-match' : ''}`}>
                  {correct
                    ? trf('You predicted it would {prediction} — and it did!', { prediction: tr(prediction ?? '') })
                    : trf('A surprise! You predicted {prediction}, but it {result}. Surprises are where scientists learn the most.', {
                        prediction: tr(prediction ?? ''),
                        result: obj.outcome === 'float' ? tr('floated') : tr('sank'),
                      })}
                </p>
                <dl>
                  <div>
                    <dt>{tr('Observe')}</dt>
                    <dd>{obj.observe}</dd>
                  </div>
                  <div>
                    <dt>{tr('Discuss')}</dt>
                    <dd>{obj.discuss}</dd>
                  </div>
                  <div>
                    <dt>{tr('Discover')}</dt>
                    <dd>{obj.discover}</dd>
                  </div>
                </dl>
              </>
            ) : (
              <p className="s-fos__wait">
                {!selected
                  ? tr('Children start by choosing what to test.')
                  : !prediction
                    ? trf('Before testing, children make a prediction. Will the {object} float or sink?', { object: obj?.label.toLowerCase() ?? '' })
                    : tr('Now test the prediction and watch carefully.')}
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
