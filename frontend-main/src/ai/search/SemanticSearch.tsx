// Semantic employee search. The query embeds via nomic-embed-text on the
// real backend and scores against the per-employee profile cards stored
// in pgvector. The mock filters the fixture set against keyword overlap
// and rolls in a small simulated latency so the spinner is realistic.

import { Button, Input, Space, Typography } from 'antd';
import { CalendarClock, Gauge, ScanSearch, Search, Sparkles } from 'lucide-react';
import { useState } from 'react';

import { EmptyState, KpiGrid, PageHeader, Section, StatCard } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { employeeMatches, type EmployeeMatch } from '../mock/data';
import { EmployeeMatchCard } from './EmployeeMatchCard';

const { Text } = Typography;

const EXAMPLE_QUERIES = [
  'backend engineer in Lahore',
  'tech lead with mentoring experience',
  'engineer who knows kafka and postgres',
  'frontend engineer reporting to Bilal',
];

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

function rerank(query: string, all: EmployeeMatch[]): EmployeeMatch[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const tokens = q.split(/\s+/).filter(Boolean);

  return all
    .map((m) => {
      const haystack = [
        m.name,
        m.designation,
        m.department,
        m.city,
        m.manager,
        m.skills.join(' '),
        m.highlight,
      ]
        .join(' ')
        .toLowerCase();
      const overlap = tokens.filter((t) => haystack.includes(t)).length / tokens.length;
      // Blend the seed score with the overlap so the ordering is stable
      // across queries but reflects keyword presence.
      const score = Math.min(1, m.score * 0.6 + overlap * 0.5);
      return { ...m, score };
    })
    .filter((m) => m.score >= 0.4)
    .sort((a, b) => b.score - a.score);
}

export function SemanticSearch() {
  const [value, setValue] = useState('');
  const [committed, setCommitted] = useState<string | null>(null);
  const [results, setResults] = useState<EmployeeMatch[] | null>(null);
  const [searching, setSearching] = useState(false);

  const runSearch = async (q: string) => {
    setSearching(true);
    setCommitted(q);
    await sleep(420 + Math.random() * 300);
    setResults(rerank(q, employeeMatches));
    setSearching(false);
  };

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="ASSISTANT · DISCOVER"
        title="Semantic employee search"
        description="Describe who you're looking for in plain language — the assistant matches against employee profile cards, not exact keywords. Works for skills, location, role shape, manager, anything HR can read."
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-corner"
          tone="primary"
          icon={Gauge}
          label="Index size"
          value="1,224"
          delta="employee profile cards"
        />
        <StatCard
          variant="accent-corner"
          tone="info"
          icon={ScanSearch}
          label="Vector dim"
          value="768"
          delta="nomic-embed-text"
        />
        <StatCard
          variant="accent-corner"
          tone="success"
          icon={CalendarClock}
          label="Last embedded"
          value="04 May 02:08"
          delta="nightly run"
        />
        <StatCard
          variant="accent-corner"
          tone="warning"
          icon={Search}
          label="Median latency"
          value="142 ms"
          delta="HNSW pgvector"
        />
      </KpiGrid>

      <Section
        title="Search"
        subtitle="Type a phrase and press Enter — or pick one of the examples below."
      >
        <Space.Compact style={{ width: '100%' }}>
          <Input
            size="large"
            autoFocus
            prefix={<ScanSearch size={16} strokeWidth={1.75} />}
            placeholder="e.g. backend engineer in Lahore who knows Java and Postgres"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onPressEnter={() => value.trim() && runSearch(value.trim())}
          />
          <Button
            size="large"
            type="primary"
            disabled={!value.trim()}
            onClick={() => runSearch(value.trim())}
            loading={searching}
          >
            Search
          </Button>
        </Space.Compact>

        <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <Text type="secondary" style={{ fontSize: 12, alignSelf: 'center' }}>
            Try:
          </Text>
          {EXAMPLE_QUERIES.map((q) => (
            <Button
              key={q}
              size="small"
              type="text"
              icon={<Sparkles size={12} strokeWidth={1.75} />}
              onClick={() => {
                setValue(q);
                runSearch(q);
              }}
              style={{ fontSize: 12 }}
            >
              {q}
            </Button>
          ))}
        </div>
      </Section>

      {committed === null ? (
        <Section title="No query yet">
          <EmptyState
            title="Search the directory like a colleague"
            description="The assistant matches the meaning of your phrase, not exact words. So &quot;people who can mentor juniors&quot; returns leads with mentoring history, even if the word &quot;mentor&quot; isn't on the profile."
          />
        </Section>
      ) : searching ? (
        <Section title="Matching the index">
          <Text type="secondary">Embedding the query and scanning {employeeMatches.length} profiles…</Text>
        </Section>
      ) : results && results.length === 0 ? (
        <Section title={`No matches for "${committed}"`}>
          <EmptyState
            title="Nothing scored above 40%"
            description="Try broadening the query — the index has profile cards for every employee, but a very narrow phrase may not match anyone."
          />
        </Section>
      ) : (
        results && (
          <Section
            title={`${results.length} ${results.length === 1 ? 'match' : 'matches'}`}
            subtitle={
              <span>
                For <Text code>{committed}</Text> · sorted by relevance
              </span>
            }
          >
            <Space orientation="vertical" size={12} style={{ width: '100%' }}>
              {results.map((m, i) => (
                <EmployeeMatchCard key={m.employeeId} match={m} rank={i + 1} />
              ))}
            </Space>
          </Section>
        )
      )}
    </Space>
  );
}
