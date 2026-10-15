import { Link, useRouterState } from '@tanstack/react-router';
import { CalendarCheck, LogIn, Menu, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { NAV, SCHOOL } from './config';

/**
 * Transparent over the hero, solid once the page scrolls. `overDark` is set
 * on pages whose hero is deep navy (the home page) so the links start white.
 */
export function SiteHeader({ overDark }: { overDark: boolean }) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Close the mobile menu after any navigation.
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    panelRef.current?.querySelector<HTMLElement>('a')?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        toggleRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const dark = overDark && !scrolled && !open;

  return (
    <header
      className={`s-header ${scrolled || open ? 'is-solid' : ''} ${dark ? 'is-dark' : ''}`}
    >
      <div className="s-container s-header__bar">
        <Link to="/home" className="s-brand" aria-label={`${SCHOOL.name} — home`}>
          <img src={SCHOOL.logo} alt="" width={44} height={44} />
          <span className="s-brand__text">
            <span className="s-brand__name">{SCHOOL.name}</span>
            <span className="s-brand__tag">{SCHOOL.tagline}</span>
          </span>
        </Link>

        <nav className="s-nav" aria-label="Main">
          <ul>
            {NAV.map((item) => (
              <li key={item.to}>
                <Link to={item.to} className="s-nav__link" activeProps={{ className: 'is-active', 'aria-current': 'page' }}>
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <Link
          to="/admissions"
          search={{ type: 'visit' }}
          hash="enquiry"
          className="s-btn s-btn--primary s-btn--sm s-header__cta"
        >
          <CalendarCheck size={16} strokeWidth={2} aria-hidden="true" />
          Book a School Visit
        </Link>

        <button
          ref={toggleRef}
          type="button"
          className="s-burger"
          aria-expanded={open}
          aria-controls="site-mobile-menu"
          aria-label={open ? 'Close menu' : 'Open menu'}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>

      <div
        id="site-mobile-menu"
        ref={panelRef}
        className={`s-mobile ${open ? 'is-open' : ''}`}
        hidden={!open}
      >
        <nav aria-label="Mobile">
          <ul>
            {NAV.map((item, i) => (
              <li key={item.to} style={{ ['--i' as string]: i }}>
                <Link to={item.to} className="s-mobile__link" activeProps={{ className: 'is-active', 'aria-current': 'page' }}>
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className="s-mobile__foot">
          <Link to="/admissions" search={{ type: 'visit' }} hash="enquiry" className="s-btn s-btn--primary">
            <CalendarCheck size={18} strokeWidth={2} aria-hidden="true" />
            Book a School Visit
          </Link>
          <Link to="/login" className="s-mobile__staff">
            <LogIn size={16} strokeWidth={2} aria-hidden="true" />
            Staff portal
          </Link>
        </div>
      </div>
    </header>
  );
}
