// `/visit` — public visitor page. No auth guard: anyone at the gate or on the
// website can open it. Two halves: a welcome/landing section about the school,
// and a reception check-in form that issues an on-screen visitor pass.
//
// There is no visitor endpoint in the backend yet, so check-in is UI-only —
// the pass is generated client-side and nothing is persisted. Wire `onFinish`
// to the API once a visitor service exists.

import { createFileRoute, Link } from '@tanstack/react-router';
import { Button, Form, Input, Select, theme as antdTheme } from 'antd';
import {
  ArrowRight,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  Clock,
  HeartHandshake,
  IdCard,
  LogIn,
  MapPin,
  Phone,
  Printer,
  ShieldCheck,
  Sparkles,
  UserPlus,
  Users,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { ThemeSwitcher } from '@/theme/ThemeSwitcher';
import './visit.css';

const { useToken } = antdTheme;

// Edit these to match the school's real details.
const SCHOOL_INFO = {
  name: 'The Spirit of Islam School',
  motto: 'Thinking is our curriculum',
  hours: 'Mon – Sat · 8:00 AM – 2:00 PM',
  address: 'Main Campus — Reception at the front gate',
  phone: 'School office',
};

const PURPOSES = [
  'Parent meeting',
  'Admission inquiry',
  'Fee / accounts',
  'Job interview',
  'Delivery / vendor',
  'Official visit',
  'Other',
];

const DEPARTMENTS = [
  'Principal office',
  'Admin office',
  'Admissions',
  'Accounts',
  'Teacher / class',
  'HR',
  'Other',
];

type VisitorForm = {
  fullName: string;
  phone: string;
  cnic?: string;
  purpose: string;
  department: string;
  personToMeet?: string;
  visitors: number;
  notes?: string;
};

type VisitorPass = VisitorForm & { passNo: string; issuedAt: Date };

export const Route = createFileRoute('/visit')({
  component: VisitPage,
});

function VisitPage() {
  const { token } = useToken();
  return (
    <div
      className="visit-page"
      style={{ background: token.colorBgLayout, color: token.colorText }}
    >
      <Header />
      <Hero />
      <Highlights />
      <CheckInSection />
      <footer className="visit-footer" style={{ color: token.colorTextSecondary }}>
        © {new Date().getFullYear()} {SCHOOL_INFO.name} — {SCHOOL_INFO.motto}
      </footer>
    </div>
  );
}

function Header() {
  return (
    <header className="visit-header">
      <a href="#top" className="visit-brand">
        <img src="/icons/school-logo.png" alt="" />
        <span>{SCHOOL_INFO.name}</span>
      </a>
      <nav className="visit-nav">
        <a href="#about">About</a>
        <a href="#checkin">Check in</a>
        <ThemeSwitcher />
        <Link to="/login" className="visit-nav-login">
          <LogIn size={15} strokeWidth={1.75} />
          Staff login
        </Link>
      </nav>
    </header>
  );
}

function Hero() {
  return (
    <section className="visit-hero" id="top">
      <div className="visit-hero-glow" aria-hidden="true" />
      <div className="visit-hero-inner">
        <span className="visit-pill">
          <Sparkles size={14} strokeWidth={1.75} />
          Welcome, visitors
        </span>
        <h1>
          Assalam-o-Alaikum,
          <br />
          <span className="visit-hero-accent">welcome to our campus.</span>
        </h1>
        <p>
          We're glad you're here. Please check in at reception so we can greet you,
          guide you to the right office and keep our students safe.
        </p>
        <div className="visit-hero-cta">
          <a href="#checkin" className="visit-btn visit-btn-gold">
            <UserPlus size={18} strokeWidth={1.75} />
            Check in now
          </a>
          <a href="#about" className="visit-btn visit-btn-ghost">
            Learn more
            <ArrowRight size={18} strokeWidth={1.75} />
          </a>
        </div>

        <div className="visit-hero-info">
          <InfoChip icon={<Clock size={16} />} label="Visiting hours" value={SCHOOL_INFO.hours} />
          <InfoChip icon={<MapPin size={16} />} label="Where" value={SCHOOL_INFO.address} />
          <InfoChip icon={<Phone size={16} />} label="Contact" value={SCHOOL_INFO.phone} />
        </div>
      </div>
      <img className="visit-hero-crest" src="/icons/school-logo.png" alt="" aria-hidden="true" />
    </section>
  );
}

function InfoChip({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="visit-chip">
      <span className="visit-chip-icon">{icon}</span>
      <span>
        <small>{label}</small>
        <strong>{value}</strong>
      </span>
    </div>
  );
}

function Highlights() {
  const { token } = useToken();
  const items = [
    {
      icon: <BookOpen size={22} strokeWidth={1.75} />,
      title: 'Thoughtful learning',
      body: 'A curriculum built around curiosity, character and critical thinking.',
    },
    {
      icon: <HeartHandshake size={22} strokeWidth={1.75} />,
      title: 'Islamic values',
      body: 'Faith, respect and service woven into everyday school life.',
    },
    {
      icon: <ShieldCheck size={22} strokeWidth={1.75} />,
      title: 'Safe campus',
      body: 'Every visitor is registered and issued a pass before entering.',
    },
    {
      icon: <Users size={22} strokeWidth={1.75} />,
      title: 'Caring staff',
      body: 'Dedicated teachers and staff who know every child by name.',
    },
  ];
  return (
    <section className="visit-section" id="about">
      <div className="visit-section-head">
        <span className="visit-eyebrow">About the school</span>
        <h2>A place to grow in knowledge and character</h2>
        <p style={{ color: token.colorTextSecondary }}>
          {SCHOOL_INFO.name} — {SCHOOL_INFO.motto.toLowerCase()}.
        </p>
      </div>
      <div className="visit-cards">
        {items.map((it) => (
          <article
            key={it.title}
            className="visit-card"
            style={{ background: token.colorBgContainer, borderColor: token.colorBorderSecondary }}
          >
            <span className="visit-card-icon">{it.icon}</span>
            <h3>{it.title}</h3>
            <p style={{ color: token.colorTextSecondary }}>{it.body}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function CheckInSection() {
  const { token } = useToken();
  const [form] = Form.useForm<VisitorForm>();
  const [pass, setPass] = useState<VisitorPass | null>(null);

  function onFinish(values: VisitorForm) {
    const issuedAt = new Date();
    const stamp = issuedAt.toISOString().slice(2, 10).replace(/-/g, '');
    const rand = Math.floor(1000 + Math.random() * 9000);
    setPass({ ...values, passNo: `V-${stamp}-${rand}`, issuedAt });
  }

  function reset() {
    form.resetFields();
    setPass(null);
  }

  return (
    <section className="visit-section" id="checkin">
      <div className="visit-checkin">
        <aside className="visit-checkin-side">
          <span className="visit-eyebrow visit-eyebrow-light">Reception</span>
          <h2>Visitor check-in</h2>
          <p>Takes less than a minute. Show your pass to the guard at the gate.</p>
          <ol className="visit-steps">
            <li>
              <span>1</span>Fill in your details
            </li>
            <li>
              <span>2</span>Get your visitor pass
            </li>
            <li>
              <span>3</span>Show it at reception
            </li>
          </ol>
          <div className="visit-side-note">
            <IdCard size={16} strokeWidth={1.75} />
            Please keep your CNIC with you during the visit.
          </div>
        </aside>

        <div
          className="visit-checkin-main"
          style={{ background: token.colorBgContainer }}
        >
          {pass ? (
            <PassCard pass={pass} onNew={reset} />
          ) : (
            <Form<VisitorForm>
              form={form}
              layout="vertical"
              requiredMark={false}
              size="large"
              onFinish={onFinish}
              initialValues={{ visitors: 1 }}
            >
              <div className="visit-form-grid">
                <Form.Item
                  label="Full name"
                  name="fullName"
                  rules={[{ required: true, message: 'Please enter your name.' }]}
                >
                  <Input placeholder="e.g. Ahmed Khan" autoComplete="name" />
                </Form.Item>
                <Form.Item
                  label="Phone number"
                  name="phone"
                  rules={[
                    { required: true, message: 'Please enter your phone number.' },
                    { pattern: /^[0-9+\-\s]{10,15}$/, message: 'Enter a valid phone number.' },
                  ]}
                >
                  <Input placeholder="03xx-xxxxxxx" inputMode="tel" autoComplete="tel" />
                </Form.Item>
                <Form.Item
                  label="CNIC (optional)"
                  name="cnic"
                  rules={[
                    { pattern: /^\d{5}-?\d{7}-?\d$/, message: 'Format: 12345-1234567-1' },
                  ]}
                >
                  <Input placeholder="12345-1234567-1" />
                </Form.Item>
                <Form.Item
                  label="Number of visitors"
                  name="visitors"
                  rules={[{ required: true }]}
                >
                  <Select
                    options={[1, 2, 3, 4, 5].map((n) => ({ value: n, label: `${n}` }))}
                  />
                </Form.Item>
                <Form.Item
                  label="Purpose of visit"
                  name="purpose"
                  rules={[{ required: true, message: 'Please choose a purpose.' }]}
                >
                  <Select
                    placeholder="Select purpose"
                    options={PURPOSES.map((p) => ({ value: p, label: p }))}
                  />
                </Form.Item>
                <Form.Item
                  label="Department"
                  name="department"
                  rules={[{ required: true, message: 'Please choose a department.' }]}
                >
                  <Select
                    placeholder="Who are you visiting?"
                    options={DEPARTMENTS.map((d) => ({ value: d, label: d }))}
                  />
                </Form.Item>
                <Form.Item label="Person to meet (optional)" name="personToMeet">
                  <Input placeholder="e.g. Ms. Fatima — Grade 5 teacher" />
                </Form.Item>
                <Form.Item label="Notes (optional)" name="notes">
                  <Input placeholder="Anything reception should know" />
                </Form.Item>
              </div>
              <Button
                type="primary"
                htmlType="submit"
                block
                icon={<CheckCircle2 size={18} strokeWidth={1.75} />}
              >
                Get visitor pass
              </Button>
            </Form>
          )}
        </div>
      </div>
    </section>
  );
}

function PassCard({ pass, onNew }: { pass: VisitorPass; onNew: () => void }) {
  const time = pass.issuedAt.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  return (
    <div className="visit-pass-wrap">
      <div className="visit-pass" id="visitor-pass">
        <div className="visit-pass-top">
          <img src="/icons/school-logo.png" alt="" />
          <div>
            <strong>{SCHOOL_INFO.name}</strong>
            <small>Visitor pass</small>
          </div>
          <span className="visit-pass-badge">VISITOR</span>
        </div>
        <div className="visit-pass-name">{pass.fullName}</div>
        <div className="visit-pass-no">{pass.passNo}</div>
        <dl className="visit-pass-grid">
          <div>
            <dt>Purpose</dt>
            <dd>{pass.purpose}</dd>
          </div>
          <div>
            <dt>Visiting</dt>
            <dd>{pass.personToMeet || pass.department}</dd>
          </div>
          <div>
            <dt>Phone</dt>
            <dd>{pass.phone}</dd>
          </div>
          <div>
            <dt>Persons</dt>
            <dd>{pass.visitors}</dd>
          </div>
        </dl>
        <div className="visit-pass-foot">
          <CalendarClock size={14} strokeWidth={1.75} />
          Issued {time}
        </div>
      </div>
      <div className="visit-pass-actions">
        <Button icon={<Printer size={16} strokeWidth={1.75} />} onClick={() => window.print()}>
          Print pass
        </Button>
        <Button type="primary" icon={<UserPlus size={16} strokeWidth={1.75} />} onClick={onNew}>
          New check-in
        </Button>
      </div>
    </div>
  );
}
