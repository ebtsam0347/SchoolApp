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
        {tr('We want every family to be able to learn about our school, whatever device or assistive technology they use.')}</p>

      <h2 className="s-h3">{tr('What we have done')}</h2>
      <ul>
        <li>{tr('Semantic headings and landmarks so screen readers can navigate easily.')}</li>
        <li>{tr('Full keyboard support, with visible focus indicators and a “skip to content” link.')}</li>
        <li>{tr('Text and controls designed to meet WCAG 2.1 AA colour-contrast guidance.')}</li>
        <li>{tr('Animations that switch off when your device asks for reduced motion.')}</li>
        <li>{tr('Descriptive text alternatives for images.')}</li>
        <li>{tr('Layouts that adapt to phones, tablets and large screens, and support zooming.')}</li>
      </ul>

      <h2 className="s-h3">{tr('Tell us about a problem')}</h2>
      <p>
        {tr('If anything on this website is difficult to use, please')}{' '}<Link to="/contact">{tr('let us know')}</Link> {tr('and we will do our best to help and to fix it.')}</p>
    </article>
  );
}
