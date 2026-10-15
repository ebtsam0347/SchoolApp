// One result row in the semantic search list. The score badge shows the
// cosine-similarity match (0..1, rendered as a percentage). Highlight is
// the LLM-composed reason this person matches the query.

import { Avatar, Card, Tag, Typography, theme as antdTheme } from 'antd';
import { Building2, MapPin, User } from 'lucide-react';

import { OrgPath } from '@/components';
import { formatDateShort } from '@/lib/dates';

import type { EmployeeMatch } from '../mock/data';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

interface EmployeeMatchCardProps {
  match: EmployeeMatch;
  rank: number;
}

export function EmployeeMatchCard({ match, rank }: EmployeeMatchCardProps) {
  const { token } = useToken();

  const scorePct = Math.round(match.score * 100);
  const scoreColor =
    match.score >= 0.85 ? token.colorSuccess : match.score >= 0.7 ? token.colorPrimary : token.colorWarning;

  return (
    <Card
      hoverable
      styles={{
        body: {
          padding: 16,
          display: 'flex',
          gap: 14,
          alignItems: 'flex-start',
        },
      }}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 6,
          flexShrink: 0,
        }}
      >
        <Avatar
          size={44}
          icon={<User size={20} strokeWidth={1.75} />}
          style={{ background: token.colorFillSecondary, color: token.colorPrimary }}
        />
        <Tag
          style={{
            margin: 0,
            borderColor: 'transparent',
            background: scoreColor,
            color: token.colorTextLightSolid,
            fontSize: 10,
            fontWeight: 700,
            padding: '0 6px',
          }}
        >
          {scorePct}%
        </Tag>
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 8,
            marginBottom: 4,
            flexWrap: 'wrap',
          }}
        >
          <Title level={5} style={{ margin: 0 }}>
            #{rank}. {match.name}
          </Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {match.code}
          </Text>
        </div>

        <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 6 }}>
          {match.designation}
        </Text>

        <div
          style={{
            display: 'flex',
            gap: 14,
            flexWrap: 'wrap',
            fontSize: 12,
            color: token.colorTextSecondary,
            marginBottom: 8,
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <Building2 size={12} strokeWidth={1.75} />
            <OrgPath segments={[{ label: match.department }]} />
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <MapPin size={12} strokeWidth={1.75} />
            {match.city}
          </span>
          <span>Reports to {match.manager}</span>
          <span>Joined {formatDateShort(match.joiningDate)}</span>
        </div>

        <Text style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>
          {match.highlight}
        </Text>

        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {match.skills.map((s) => (
            <Tag key={s} style={{ margin: 0, fontSize: 11 }}>
              {s}
            </Tag>
          ))}
        </div>
      </div>
    </Card>
  );
}
