// Tiny markdown renderer for assistant replies. Real ai-service output is
// plain markdown (qwen2.5 emits **bold**, bullet lists, line breaks); we
// don't want a full library for it, so this handles the four shapes the
// canned replies actually use:
//
//   - Paragraphs separated by blank lines
//   - "• " or "- " bullet lists
//   - **bold** spans
//   - Single newlines as <br>
//
// Anything else falls through as text. Keeps the bundle small and avoids
// the XSS surface of dangerouslySetInnerHTML.

import { Fragment, type ReactNode } from 'react';

interface MessageMarkdownProps {
  text: string;
  /** Render inline (no trailing whitespace block) so a streaming caret can sit flush. */
  inlineCaret?: boolean;
}

export function MessageMarkdown({ text, inlineCaret }: MessageMarkdownProps) {
  const blocks = text.split(/\n{2,}/);
  return (
    <>
      {blocks.map((block, i) => (
        <Fragment key={i}>{renderBlock(block, i === blocks.length - 1 && inlineCaret)}</Fragment>
      ))}
    </>
  );
}

function renderBlock(block: string, lastInline: boolean | undefined): ReactNode {
  const lines = block.split('\n');
  const isBulletBlock = lines.every((l) => /^\s*([•\-*])\s+/.test(l) || l.trim() === '');

  if (isBulletBlock) {
    return (
      <ul style={{ paddingLeft: 18, margin: '0 0 8px' }}>
        {lines
          .filter((l) => l.trim() !== '')
          .map((l, i) => (
            <li key={i} style={{ marginBottom: 2 }}>
              {renderInline(l.replace(/^\s*([•\-*])\s+/, ''))}
            </li>
          ))}
      </ul>
    );
  }

  return (
    <p style={{ margin: lastInline ? 0 : '0 0 8px' }}>
      {lines.map((l, i) => (
        <Fragment key={i}>
          {renderInline(l)}
          {i < lines.length - 1 ? <br /> : null}
        </Fragment>
      ))}
    </p>
  );
}

function renderInline(text: string): ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) => {
    if (/^\*\*[^*]+\*\*$/.test(part)) {
      return <strong key={i}>{part.slice(2, -2)}</strong>;
    }
    return <Fragment key={i}>{part}</Fragment>;
  });
}
