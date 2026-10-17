import { createFileRoute, Link } from '@tanstack/react-router';
import { usePageMeta } from '@/site/hooks';
import { Eyebrow } from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/accessibility')({
  component: AccessibilityPage,
});

function AccessibilityPage() {
  usePageMeta({
    title: tr('Accessibility'),
    path: '/accessibility',
    description: tr('Our commitment to making The Spirit of Islam School website usable by everyone.'),
  });

  return (
    <article className="s-doc s-container">
      <Eyebrow>{tr('Accessibility')}</Eyebrow>
      <h1 className="s-h1">{tr('Accessibility Statement')}</h1>
      <p className="s-lede">
        {tr('We want every family to use this website easily, on any device.')}</p>

      <h2 className="s-h3">{tr('What we have done')}</h2>
      <ul>
        <li>{tr('Clear headings, so screen readers can find their way.')}</li>
        <li>{tr('Works fully with a keyboard, with a “skip to content” link.')}</li>
        <li>{tr('Text colours chosen to be easy to read (WCAG 2.1 AA contrast).')}</li>
        <li>{tr('Animations stop if your device is set to reduce motion.')}</li>
        <li>{tr('Images have text descriptions.')}</li>
        <li>{tr('Pages fit phones, tablets and computers, and can be zoomed.')}</li>
      </ul>

      <h2 className="s-h3">{tr('Tell us about a problem')}</h2>
      <p>
        {tr('Something hard to use? Please')}{' '}<Link to="/contact">{tr('let us know')}</Link> {tr('and we will fix it.')}</p>
    </article>
  );
}
