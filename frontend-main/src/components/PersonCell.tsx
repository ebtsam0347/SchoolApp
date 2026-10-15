import { Avatar, theme as antdTheme, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import type { CSSProperties, ReactNode } from 'react';

const { useToken } = antdTheme;
const { Text } = Typography;

export type PersonCellPerson = {
  fullName: string;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
  /** Job title. When set, the subtitle becomes `<designation> · <code>`. */
  designation?: string | null;
  /** Override the secondary line entirely. Wins over designation/code. */
  subtitle?: ReactNode;
};

export type PersonCellProps = {
  person: PersonCellPerson;
  /** Sizing preset. compact for tables, default for cards, large for detail-page header strips. */
  size?: 'compact' | 'default' | 'large';
  /** Wrap the cell in a router Link. */
  to?: string;
  /** Show only the avatar, no text. Useful for stacked layouts. */
  avatarOnly?: boolean;
  /** Hide the secondary line entirely. */
  hideSubtitle?: boolean;
  /** Optional trailing slot (chip, count, status pill). */
  trailing?: ReactNode;
  className?: string;
  styles?: {
    root?: CSSProperties;
    primary?: CSSProperties;
    subtitle?: CSSProperties;
    avatar?: CSSProperties;
  };
};

const SIZE_TOKENS: Record<NonNullable<PersonCellProps['size']>, {
  avatar: number;
  primary: number;
  subtitle: number;
  gap: number;
  initialsSize: number;
}> = {
  // Avatar diameters were bumped so an employee photo is actually readable
  // rather than a thumbnail — a 28px circle showed little more than a blur.
  // Text sizes are unchanged; only the avatar, its gap, and the initials
  // fallback grew. Note `compact` is the table-cell preset, so rows are
  // correspondingly taller.
  compact: { avatar: 36, primary: 13, subtitle: 11, gap: 10, initialsSize: 13 },
  default: { avatar: 44, primary: 14, subtitle: 12, gap: 12, initialsSize: 16 },
  large: { avatar: 76, primary: 20, subtitle: 13, gap: 16, initialsSize: 26 },
};

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

function renderDesignationCode(
  designation: string | null | undefined,
  code: string | null | undefined,
  codeColor: string,
): ReactNode {
  if (!designation && !code) return null;
  if (!designation) return code;
  if (!code) return designation;
  return (
    <>
      {designation}
      <span
        style={{
          marginLeft: 6,
          color: codeColor,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        · {code}
      </span>
    </>
  );
}

function hueFromName(name: string) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash) % 360;
}

export function PersonCell({
  person,
  size = 'default',
  to,
  avatarOnly = false,
  hideSubtitle = false,
  trailing,
  className,
  styles: slot,
}: PersonCellProps) {
  const { token } = useToken();
  const tokens = SIZE_TOKENS[size];

  const subtitle =
    person.subtitle ?? renderDesignationCode(person.designation, person.employeeCode, token.colorTextTertiary);

  const avatar = (
    <Avatar
      size={tokens.avatar}
      src={person.profilePictureUrl ?? undefined}
      style={{
        background: `hsl(${hueFromName(person.fullName)}, 55%, 48%)`,
        color: token.colorTextLightSolid,
        fontSize: tokens.initialsSize,
        fontWeight: 600,
        flexShrink: 0,
        ...slot?.avatar,
      }}
    >
      {!person.profilePictureUrl ? initials(person.fullName) : null}
    </Avatar>
  );

  const body = avatarOnly ? (
    avatar
  ) : (
    <div
      className={className}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: tokens.gap,
        minWidth: 0,
        ...slot?.root,
      }}
    >
      {avatar}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div
          style={{
            fontSize: tokens.primary,
            fontWeight: size === 'large' ? 600 : 500,
            color: token.colorTextHeading,
            lineHeight: 1.25,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            ...slot?.primary,
          }}
        >
          {person.fullName}
        </div>
        {!hideSubtitle && subtitle ? (
          <Text
            type="secondary"
            style={{
              fontSize: tokens.subtitle,
              display: 'block',
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              ...slot?.subtitle,
            }}
          >
            {subtitle}
          </Text>
        ) : null}
      </div>
      {trailing ? <div style={{ flexShrink: 0 }}>{trailing}</div> : null}
    </div>
  );

  if (to) {
    return (
      <Link to={to} style={{ color: 'inherit', textDecoration: 'none' }}>
        {body}
      </Link>
    );
  }
  return body;
}
