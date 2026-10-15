// Renders a string with a tasteful blinking-caret tail while the parent
// is still appending tokens. Once the stream finishes the caret drops
// and the same text is rendered as static markdown via MessageMarkdown.
//
// Caret is built from CSS keyframes (no JS animation loop) and uses the
// AntD primary token for the colour so it changes with the active theme.

import { theme as antdTheme } from 'antd';

import { MessageMarkdown } from './MessageMarkdown';

const { useToken } = antdTheme;

interface TypewriterTextProps {
  text: string;
  streaming: boolean;
}

export function TypewriterText({ text, streaming }: TypewriterTextProps) {
  const { token } = useToken();

  if (!streaming) {
    return <MessageMarkdown text={text} />;
  }

  return (
    <span style={{ display: 'block' }}>
      <MessageMarkdown text={text} inlineCaret />
      <span
        aria-hidden
        style={{
          display: 'inline-block',
          width: 8,
          height: 14,
          marginLeft: 2,
          background: token.colorPrimary,
          verticalAlign: '-2px',
          animation: 'fshrms-ai-caret 1s steps(1, end) infinite',
          borderRadius: 1,
        }}
      />
      <style>{`
        @keyframes fshrms-ai-caret {
          0%, 50% { opacity: 1; }
          51%, 100% { opacity: 0; }
        }
      `}</style>
    </span>
  );
}
