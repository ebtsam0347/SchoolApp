import { Link } from '@tanstack/react-router';
import { Clock, LogIn, Mail, MapPin, Phone } from 'lucide-react';
import { NAV, SCHOOL } from './config';
import { GeometricPattern, Sparkle } from './ui';

import { tr } from '@/site/i18n';
export function SiteFooter() {
  const { contact } = SCHOOL;
  const hasContact = contact.phone || contact.email || contact.address;
  return (
    <footer className="s-footer">
      <GeometricPattern className="s-footer__pattern" size={96} />
      <div className="s-container">
        <div className="s-footer__grid">
          <div className="s-footer__brand">
            <img src={SCHOOL.logo} alt={SCHOOL.logoAlt} width={72} height={72} loading="lazy" />
            <p className="s-footer__name">{SCHOOL.name}</p>
            <p className="s-footer__tag">
              <Sparkle size={12} />
              {SCHOOL.tagline}
            </p>
            <p className="s-footer__mission">{SCHOOL.mission}</p>
          </div>

          <nav aria-label={tr('Footer')}>
            <h2 className="s-footer__h">{tr('Explore')}</h2>
            <ul>
              {NAV.map((n) => (
                <li key={n.to}>
                  <Link to={n.to}>{n.label}</Link>
                </li>
              ))}
            </ul>
          </nav>

          <div>
            <h2 className="s-footer__h">{tr('Admissions')}</h2>
            <ul>
              <li>
                <Link to="/admissions" hash="enquiry">
                  {tr('Enquire about admissions')}</Link>
              </li>
              <li>
                <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry">
                  {tr('Arrange a school visit')}</Link>
              </li>
              <li>
                <Link to="/privacy">{tr('Privacy notice')}</Link>
              </li>
              <li>
                <Link to="/accessibility">{tr('Accessibility')}</Link>
              </li>
            </ul>
          </div>

          <div>
            <h2 className="s-footer__h">{tr('Contact')}</h2>
            <ul className="s-footer__contact">
              {contact.phone && (
                <li>
                  <Phone size={15} aria-hidden="true" />
                  <a href={`tel:${contact.phone.replace(/\s/g, '')}`}>{contact.phone}</a>
                </li>
              )}
              {contact.email && (
                <li>
                  <Mail size={15} aria-hidden="true" />
                  <a href={`mailto:${contact.email}`}>{contact.email}</a>
                </li>
              )}
              {contact.address && (
                <li>
                  <MapPin size={15} aria-hidden="true" />
                  <span>{contact.address}</span>
                </li>
              )}
              {contact.officeHours && (
                <li>
                  <Clock size={15} aria-hidden="true" />
                  <span>{contact.officeHours}</span>
                </li>
              )}
              {!hasContact && <li className="s-footer__muted">{tr('Contact details will be published soon.')}</li>}
              <li>
                <Link to="/contact">{tr('Send us a message')}</Link>
              </li>
            </ul>
            {SCHOOL.social.length > 0 && (
              <ul className="s-footer__social" aria-label={tr('Social media')}>
                {SCHOOL.social.map((s) => (
                  <li key={s.href}>
                    <a href={s.href} target="_blank" rel="noopener noreferrer">
                      {s.label}
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="s-footer__bottom">
          <p>
            © {new Date().getFullYear()} {SCHOOL.name}. {SCHOOL.essence}
          </p>
          <Link to="/login" className="s-footer__staff">
            <LogIn size={14} aria-hidden="true" />
            {tr('Staff portal')}</Link>
        </div>
      </div>
    </footer>
  );
}
