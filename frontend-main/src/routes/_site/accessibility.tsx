import { createFileRoute, Link } from '@tanstack/react-router';
import { usePageMeta } from '@/site/hooks';
import { Eyebrow } from '@/site/ui';

export const Route = createFileRoute('/_site/accessibility')({
  component: AccessibilityPage,
});

function AccessibilityPage() {
  usePageMeta({
    title: 'Accessibility',
    path: '/accessibility',
    description: 'Our commitment to making The Spirit of Islam School website usable by everyone.',
  });

  return (
    <article className="s-doc s-container">
      <Eyebrow>Accessibility</Eyebrow>
      <h1 className="s-h1">Accessibility Statement</h1>
      <p className="s-lede">
        We want every family to be able to learn about our school, whatever device or assistive technology they use.
      </p>

      <h2 className="s-h3">What we have done</h2>
      <ul>
        <li>Semantic headings and landmarks so screen readers can navigate easily.</li>
        <li>Full keyboard support, with visible focus indicators and a “skip to content” link.</li>
        <li>Text and controls designed to meet WCAG 2.1 AA colour-contrast guidance.</li>
        <li>Animations that switch off when your device asks for reduced motion.</li>
        <li>Descriptive text alternatives for images.</li>
        <li>Layouts that adapt to phones, tablets and large screens, and support zooming.</li>
      </ul>

      <h2 className="s-h3">Tell us about a problem</h2>
      <p>
        If anything on this website is difficult to use, please <Link to="/contact">let us know</Link> and we will do our
        best to help and to fix it.
      </p>
    </article>
  );
}
