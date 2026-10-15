import { Clock, Mail, MapPin, MessageCircle, Phone } from 'lucide-react';
import { SCHOOL } from './config';

/** Verified contact details from config; a clear "to be announced" otherwise. */
export function ContactDetails() {
  const c = SCHOOL.contact;
  const rows = [
    c.phone && { icon: Phone, label: 'Phone', value: c.phone, href: `tel:${c.phone.replace(/\s/g, '')}` },
    c.whatsapp && {
      icon: MessageCircle,
      label: 'WhatsApp',
      value: c.whatsapp,
      href: `https://wa.me/${c.whatsapp.replace(/[^\d]/g, '')}`,
    },
    c.email && { icon: Mail, label: 'Email', value: c.email, href: `mailto:${c.email}` },
    c.address && { icon: MapPin, label: 'Address', value: c.address },
    c.officeHours && { icon: Clock, label: 'Office hours', value: c.officeHours },
  ].filter(Boolean) as { icon: typeof Phone; label: string; value: string; href?: string }[];

  if (rows.length === 0) {
    return (
      <p className="s-contact-tba">
        Our phone number, email and address will be published here once confirmed. In the meantime, the form is the
        best way to reach us.
      </p>
    );
  }

  return (
    <ul className="s-contact-list">
      {rows.map((r) => {
        const Icon = r.icon;
        return (
          <li key={r.label}>
            <span className="s-contact-list__icon" aria-hidden="true">
              <Icon size={18} />
            </span>
            <span>
              <small>{r.label}</small>
              {r.href ? (
                <a href={r.href} target={r.href.startsWith('https') ? '_blank' : undefined} rel="noopener noreferrer">
                  {r.value}
                </a>
              ) : (
                <strong>{r.value}</strong>
              )}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
