// Sticky composer at the bottom of the transcript. Autogrowing textarea
// (capped at ~6 lines), Enter to send / Shift+Enter for newline,
// Stop button while streaming. Mirrors the ChatGPT-style "input bar"
// rather than the AntD MessageInput because we want full control over
// the keystroke handling and the Stop affordance.

import { Button, Tooltip, theme as antdTheme } from 'antd';
import { ArrowUp, Square } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { KbdHint } from '@/components';

const { useToken } = antdTheme;

interface ComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  streaming: boolean;
  disabled?: boolean;
  placeholder?: string;
}

export function Composer({
  value,
  onChange,
  onSend,
  onStop,
  streaming,
  disabled,
  placeholder = 'Ask anything — policy, people, summaries…',
}: ComposerProps) {
  const { token } = useToken();
  const ref = useRef<HTMLTextAreaElement>(null);

  // Autogrow: reset height to 0 first so it shrinks back down when text is
  // deleted, then size to scrollHeight (capped).
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = '0px';
    el.style.height = `${Math.min(el.scrollHeight, 168)}px`;
  }, [value]);

  const canSend = !streaming && !disabled && value.trim().length > 0;

  return (
    <div
      style={{
        padding: '12px 16px 16px',
        borderTop: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorBgContainer,
      }}
    >
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'flex-end',
          padding: 8,
          borderRadius: 12,
          border: `1px solid ${token.colorBorder}`,
          background: token.colorBgElevated,
          boxShadow: '0 1px 0 rgba(0,0,0,0.02)',
        }}
      >
        <textarea
          ref={ref}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              if (canSend) onSend();
            }
          }}
          placeholder={placeholder}
          rows={1}
          disabled={disabled}
          style={{
            flex: 1,
            border: 'none',
            outline: 'none',
            resize: 'none',
            background: 'transparent',
            color: token.colorText,
            fontSize: 14,
            lineHeight: 1.55,
            maxHeight: 168,
            padding: '6px 8px',
            fontFamily: 'inherit',
          }}
        />
        {streaming ? (
          <Tooltip title="Stop generating">
            <Button
              type="primary"
              danger
              shape="circle"
              icon={<Square size={14} strokeWidth={2} fill="currentColor" />}
              onClick={onStop}
            />
          </Tooltip>
        ) : (
          <Tooltip title={canSend ? 'Send (Enter)' : 'Type a message'}>
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowUp size={16} strokeWidth={2} />}
              disabled={!canSend}
              onClick={onSend}
            />
          </Tooltip>
        )}
      </div>
      <div
        style={{
          marginTop: 6,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: 11,
          color: token.colorTextTertiary,
        }}
      >
        <span>
          <KbdHint keys="Enter" /> send · <KbdHint keys={['Shift', 'Enter']} /> newline
        </span>
        <span>Replies are mocked for the preview workspace.</span>
      </div>
    </div>
  );
}
