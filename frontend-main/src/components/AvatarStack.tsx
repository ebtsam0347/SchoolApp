import { Avatar, theme as antdTheme, Tooltip } from 'antd';
import type { CSSProperties } from 'react';

const { useToken } = antdTheme;

export type AvatarStackPerson = {
  name: string;
  /** Optional photo URL. */
  src?: string;
  /** Optional explicit avatar background color. If omitted, derived from `name`. */
  color?: string;
};

export type AvatarStackProps = {
  people: AvatarStackPerson[];
  /** Max avatars visible before collapsing into a `+N` chip. Default 4. */
  max?: number;
  /** Avatar diameter in px or AntD shorthand. Default 32. */
  size?: number | 'small' | 'default' | 'large';
  /** Tooltip shown on hover. Default true. */
  showTooltip?: boolean;
  className?: string;
  style?: CSSProperties;
};

// Stable hue per name so the same person always gets the same color across views.
function hueFromName(name: string) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash) % 360;
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

export function AvatarStack({
  people,
  max = 4,
  // Bumped from 28 so faces in a stack are recognisable, matching the
  // PersonCell avatar bump. Call sites passing an explicit size are
  // unaffected.
  size = 32,
  showTooltip = true,
  className,
  style,
}: AvatarStackProps) {
  const { token } = useToken();

  const renderAvatar = (person: AvatarStackPerson, key: string | number) => {
    const bg =
      person.color ?? `hsl(${hueFromName(person.name)}, 55%, 48%)`;
    const node = (
      <Avatar
        key={key}
        size={size}
        src={person.src}
        style={{
          background: bg,
          color: token.colorTextLightSolid,
          fontSize: typeof size === 'number' && size <= 24 ? 11 : 12,
          fontWeight: 600,
        }}
      >
        {!person.src ? initials(person.name) : null}
      </Avatar>
    );
    return showTooltip ? (
      <Tooltip key={key} title={person.name}>
        {node}
      </Tooltip>
    ) : (
      node
    );
  };

  return (
    <Avatar.Group
      className={className}
      max={{
        count: max,
        style: {
          background: token.colorFillTertiary,
          color: token.colorTextSecondary,
          fontSize: 12,
          fontWeight: 600,
        },
      }}
      size={size}
      style={style}
    >
      {people.map((p, i) => renderAvatar(p, `${p.name}-${i}`))}
    </Avatar.Group>
  );
}
