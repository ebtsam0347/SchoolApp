import { createFileRoute, Link } from '@tanstack/react-router';
import { SCHOOL } from '@/site/config';
import { usePageMeta } from '@/site/hooks';
import { Eyebrow, ProvisionalNote } from '@/site/ui';

import { tr } from '@/site/i18n';
export const Route = createFileRoute('/_site/privacy')({
  component: PrivacyPage,
});

function PrivacyPage() {
  usePageMeta({
    title: tr('Privacy Notice'),
    path: '/privacy',
    description: tr('How The Spirit of Islam School handles information submitted through this website.'),
  });

  return (
    <article className="s-doc s-container">
      <Eyebrow>{tr('Privacy')}</Eyebrow>
      <h1 className="s-h1">{tr('Privacy Notice')}</h1>
      <ProvisionalNote>
        {tr('This notice explains how this website uses your information. The school will review it as it opens, in line with local data-protection rules.')}</ProvisionalNote>

      <h2 className="s-h3">{tr('What we collect')}</h2>
      <p>
        {tr('When you send an enquiry, we collect only what you give us: your name, phone, email, how you would like us to reply, your child’s age group and your message. Please don’t share sensitive details about your child.')}</p>

      <h2 className="s-h3">{tr('How we use it')}</h2>
      <p>
        {tr('We use your details only to reply, arrange a visit or send admissions news you asked for. We never sell your data or use it for adverts.')}</p>

      <h2 className="s-h3">{tr('How it is protected')}</h2>
      <p>
        {tr('Enquiries travel over a secure (HTTPS) connection straight to the school. They are never published on this website.')}</p>

      <h2 className="s-h3">{tr('Cookies and tracking')}</h2>
      <p>
        {tr('We don’t use advertising cookies or tracking. Your browser may save small settings, such as your language choice.')}</p>

      <h2 className="s-h3">{tr('Your choices')}</h2>
      <p>
        {tr('You can ask us to update or delete your information at any time by')}{' '}
        <Link to="/contact">{tr('contacting the school')}</Link>
        {SCHOOL.contact.email ? (
          <>
            {' '}
            {tr('or emailing')}{' '}<a href={`mailto:${SCHOOL.contact.email}`}>{SCHOOL.contact.email}</a>
          </>
        ) : null}
        .
      </p>

      <h2 className="s-h3">{tr('Children’s photographs')}</h2>
      <p>
        {tr('We only publish photos of children with their parents’ permission, and never in a way that identifies a child.')}</p>
    </article>
  );
}
