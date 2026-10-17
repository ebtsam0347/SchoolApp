// Language choice for the public website (see i18n.ts): a one-time prompt on
// the first visit, and a switch in the header for changing it later.

import { Languages } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { SCHOOL } from './config';
import { chooseLang, chosenLang, isUrdu } from './i18n';

/** Asks "English or Urdu?" until the visitor picks one. */
export function LanguagePrompt() {
  const [open, setOpen] = useState(chosenLang === null);
  const firstRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    firstRef.current?.focus();
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    // Escape closes for now; the question comes back on the next visit.
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!open) return null;

  const pick = (l: 'en' | 'ur') => {
    chooseLang(l); // reloads when switching to Urdu
    setOpen(false);
  };

  return (
    <div className="s-lang-prompt" role="presentation">
      <div className="s-lang-prompt__card" role="dialog" aria-modal="true" aria-labelledby="lang-prompt-title">
        <img src={SCHOOL.logo} alt="" width={56} height={56} />
        <h2 id="lang-prompt-title" className="s-lang-prompt__title">
          Choose your language
        </h2>
        <p className="s-lang-prompt__title s-lang-prompt__title--ur" lang="ur" dir="rtl">
          اپنی زبان چُنیں
        </p>
        <div className="s-lang-prompt__choices">
          <button ref={firstRef} type="button" className="s-lang-choice" lang="en" onClick={() => pick('en')}>
            English
            <small>View in English</small>
          </button>
          <button type="button" className="s-lang-choice s-lang-choice--ur" lang="ur" dir="rtl" onClick={() => pick('ur')}>
            اردو
            <small>اردو میں دیکھیں</small>
          </button>
        </div>
        <p className="s-lang-prompt__note">You can change this anytime from the top of the page.</p>
        <p className="s-lang-prompt__note s-lang-prompt__note--ur" lang="ur" dir="rtl">
          آپ یہ کسی بھی وقت صفحے کے اوپر سے بدل سکتے ہیں۔
        </p>
      </div>
    </div>
  );
}

/** Header button that switches to the other language. */
export function LanguageSwitch({ className }: { className?: string }) {
  return (
    <button
      type="button"
      className={`s-lang-switch ${className ?? ''}`}
      onClick={() => chooseLang(isUrdu ? 'en' : 'ur')}
      aria-label={isUrdu ? 'Switch to English — انگریزی میں دیکھیں' : 'اردو میں دیکھیں — Switch to Urdu'}
    >
      <Languages size={16} aria-hidden="true" />
      {isUrdu ? <span lang="en">English</span> : <span lang="ur">اردو</span>}
    </button>
  );
}
