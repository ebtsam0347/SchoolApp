import { createFileRoute, Link } from '@tanstack/react-router';
import { SCHOOL } from '@/site/config';
import { usePageMeta } from '@/site/hooks';
import { Eyebrow, ProvisionalNote } from '@/site/ui';

export const Route = createFileRoute('/_site/privacy')({
  component: PrivacyPage,
});

function PrivacyPage() {
  usePageMeta({
    title: 'Privacy Notice',
    path: '/privacy',
    description: 'How The Spirit of Islam School handles information submitted through this website.',
  });

  return (
    <article className="s-doc s-container">
      <Eyebrow>Privacy</Eyebrow>
      <h1 className="s-h1">Privacy Notice</h1>
      <ProvisionalNote>
        This notice describes how this website handles your information. It will be reviewed and updated by the school
        as it is established, in line with applicable local data-protection requirements.
      </ProvisionalNote>

      <h2 className="s-h3">What we collect</h2>
      <p>
        When you send an enquiry, we collect only what you choose to give us: your name, email address, phone number,
        preferred contact method, your child’s approximate age group or intended programme, and your message. We do not
        ask for sensitive information about your child, and we ask you not to include it.
      </p>

      <h2 className="s-h3">How we use it</h2>
      <p>
        We use your details only to respond to your enquiry, arrange a visit you have requested, or share admissions
        information you have asked for. We do not sell your information or use it for advertising.
      </p>

      <h2 className="s-h3">How it is protected</h2>
      <p>
        Enquiries are sent over an encrypted (HTTPS) connection directly to the school’s enquiry system. They are never
        published on this website.
      </p>

      <h2 className="s-h3">Cookies and tracking</h2>
      <p>
        This website does not use advertising cookies or third-party tracking. Your browser may store simple preferences
        locally to make the site work.
      </p>

      <h2 className="s-h3">Your choices</h2>
      <p>
        You can ask us to update or delete the information you sent us at any time by{' '}
        <Link to="/contact">contacting the school</Link>
        {SCHOOL.contact.email ? (
          <>
            {' '}
            or emailing <a href={`mailto:${SCHOOL.contact.email}`}>{SCHOOL.contact.email}</a>
          </>
        ) : null}
        .
      </p>

      <h2 className="s-h3">Children’s photographs</h2>
      <p>
        We will only publish photographs of children with appropriate permission from their parents or guardians, and we
        will never publish information that identifies an individual child.
      </p>
    </article>
  );
}
