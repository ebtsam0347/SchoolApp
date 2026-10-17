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
        {tr('This notice describes how this website handles your information. It will be reviewed and updated by the school as it is established, in line with applicable local data-protection requirements.')}</ProvisionalNote>

      <h2 className="s-h3">{tr('What we collect')}</h2>
      <p>
        {tr('When you send an enquiry, we collect only what you choose to give us: your name, email address, phone number, preferred contact method, your child’s approximate age group or intended programme, and your message. We do not ask for sensitive information about your child, and we ask you not to include it.')}</p>

      <h2 className="s-h3">{tr('How we use it')}</h2>
      <p>
        {tr('We use your details only to respond to your enquiry, arrange a visit you have requested, or share admissions information you have asked for. We do not sell your information or use it for advertising.')}</p>

      <h2 className="s-h3">{tr('How it is protected')}</h2>
      <p>
        {tr('Enquiries are sent over an encrypted (HTTPS) connection directly to the school’s enquiry system. They are never published on this website.')}</p>

      <h2 className="s-h3">{tr('Cookies and tracking')}</h2>
      <p>
        {tr('This website does not use advertising cookies or third-party tracking. Your browser may store simple preferences locally to make the site work.')}</p>

      <h2 className="s-h3">{tr('Your choices')}</h2>
      <p>
        {tr('You can ask us to update or delete the information you sent us at any time by')}{' '}
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
        {tr('We will only publish photographs of children with appropriate permission from their parents or guardians, and we will never publish information that identifies an individual child.')}</p>
    </article>
  );
}
