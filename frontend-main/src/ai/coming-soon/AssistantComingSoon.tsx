// "Coming soon" landing for the AI Assistant, reached from the topbar
// launcher. Purely presentational — no backend calls — so it can ship
// ahead of the assistant itself and be swapped for the real chat later.

import { useNavigate } from '@tanstack/react-router';
import { Button, theme as antdTheme } from 'antd';
import {
  ArrowLeft,
  BookOpenText,
  CalendarCheck,
  Compass,
  Send,
  Sparkles,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import type { CSSProperties } from 'react';
import './AssistantComingSoon.css';

const { useToken } = antdTheme;

const FEATURES: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: BookOpenText,
    title: 'Policy answers',
    body: 'Ask about leave rules, travel allowances or company policies and get clear answers in seconds.',
  },
  {
    icon: CalendarCheck,
    title: 'Leave & attendance',
    body: 'Check balances, late arrivals and short leaves without digging through reports.',
  },
  {
    icon: Wallet,
    title: 'Payroll insights',
    body: 'Understand your payslip, deductions and advances in plain language.',
  },
  {
    icon: Compass,
    title: 'Smart navigation',
    body: 'Tell the assistant what you need and it takes you straight to the right screen.',
  },
];

const STEPS = [
  { title: 'Ask in plain language', body: 'Type a question the way you would ask a colleague.' },
  { title: 'Grounded in your data', body: 'Answers respect your role and permissions.' },
  { title: 'Act right away', body: 'Jump to the related request, report or approval.' },
];

export function AssistantComingSoon() {
  const { token } = useToken();
  const navigate = useNavigate();

  const vars = {
    '--assistant-primary': token.colorPrimary,
    '--assistant-surface': token.colorBgContainer,
    '--assistant-border': token.colorBorderSecondary,
    '--assistant-muted': token.colorTextSecondary,
    color: token.colorText,
  } as CSSProperties;

  return (
    <div className="assistant-soon" style={vars}>
      <section className="assistant-soon__hero">
        <div className="assistant-soon__orb" aria-hidden="true">
          <Sparkles size={38} strokeWidth={1.75} />
        </div>
        <span className="assistant-soon__badge">
          <span className="assistant-soon__badge-dot" />
          Coming soon
        </span>
        <h1 className="assistant-soon__title">Meet your AI Assistant</h1>
        <p className="assistant-soon__lead">
          An intelligent assistant built into HRMS that understands your workplace — answering
          questions about leaves, attendance, payroll and policies, and guiding you to what you
          need. We're putting the finishing touches on it.
        </p>
        <div className="assistant-soon__actions">
          <Button
            size="large"
            icon={<ArrowLeft size={16} />}
            onClick={() => void navigate({ to: '/' })}
          >
            Back to dashboard
          </Button>
        </div>
      </section>

      <section>
        <h2 className="assistant-soon__section-title">What it will help you with</h2>
        <div className="assistant-soon__grid">
          {FEATURES.map(({ icon: Icon, title, body }) => (
            <div key={title} className="assistant-soon__feature">
              <div className="assistant-soon__feature-icon">
                <Icon size={20} strokeWidth={1.75} />
              </div>
              <h3>{title}</h3>
              <p>{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="assistant-soon__preview">
        <div className="assistant-soon__chat" aria-label="Assistant preview">
          <div className="assistant-soon__bubble assistant-soon__bubble--user">
            How many annual leaves do I have left this year?
          </div>
          <div className="assistant-soon__bubble assistant-soon__bubble--bot">
            You have 9 annual leaves remaining. Would you like me to open a new leave request?
          </div>
          <div className="assistant-soon__typing" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <div className="assistant-soon__input">
            <span style={{ flex: 1 }}>Ask anything about your workplace…</span>
            <Send size={16} strokeWidth={1.75} />
          </div>
        </div>
        <div>
          <h2 className="assistant-soon__section-title">How it will work</h2>
          <ol className="assistant-soon__steps">
            {STEPS.map((s, i) => (
              <li key={s.title}>
                <span className="assistant-soon__step-num">{i + 1}</span>
                <div>
                  <strong>{s.title}</strong>
                  <span>{s.body}</span>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </div>
  );
}
