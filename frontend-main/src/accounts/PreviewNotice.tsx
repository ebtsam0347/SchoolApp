// Persistent "this is a preview" reassurance — shown on every Accounts
// page so a stakeholder shoulder-surfing during a demo doesn't mistake
// fixture data for live numbers.

import { Alert } from 'antd';
import { Sparkles } from 'lucide-react';

export function PreviewNotice() {
  return (
    <Alert
      type="info"
      showIcon
      icon={<Sparkles size={14} strokeWidth={1.75} />}
      style={{ borderRadius: 'var(--app-radius-md)' }}
      title="Preview workspace — figures are illustrative, not live."
    />
  );
}
