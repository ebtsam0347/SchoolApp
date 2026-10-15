// Push opt-in UI: a one-line banner in the bell panel (until turned on or
// dismissed) and the full card on /account/notifications.

import { useState } from 'react';
import { Alert, App, Button, Card, Switch, Typography, theme as antdTheme } from 'antd';
import { BellRing, X } from 'lucide-react';

import { getErrorMessage } from '@/lib';

import { usePushNotifications, type PushStatus } from './usePushNotifications';
import { pushSupport } from './web-push';

const { Text } = Typography;
const { useToken } = antdTheme;

const BANNER_DISMISSED_KEY = 'fshrms.push.bannerDismissed';

function useEnableWithFeedback() {
  const { message } = App.useApp();
  const push = usePushNotifications();

  async function turnOn() {
    try {
      const permission = await push.enable();
      if (permission === 'granted') {
        message.success('Notifications are on for this device');
      } else if (permission === 'denied') {
        message.warning('Notifications are blocked for this site. Allow them in your browser settings.');
      }
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  }

  async function turnOff() {
    try {
      await push.disable();
      message.success('Notifications are off for this device');
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  }

  return { ...push, turnOn, turnOff };
}

/** Top of the bell panel. Only shown while push is available but off. */
export function PushBellBanner() {
  const { token } = useToken();
  const push = useEnableWithFeedback();
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(BANNER_DISMISSED_KEY) === '1';
    } catch {
      return false;
    }
  });

  if (push.status !== 'off' || dismissed) return null;

  function dismiss() {
    setDismissed(true);
    try {
      localStorage.setItem(BANNER_DISMISSED_KEY, '1');
    } catch {
      // Private mode: it comes back next session, which is fine.
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px 10px 14px',
        background: token.colorPrimaryBg,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <BellRing size={16} strokeWidth={1.75} color={token.colorPrimary} style={{ flexShrink: 0 }} />
      <Text style={{ flex: 1, fontSize: 12, lineHeight: 1.4 }}>
        Get alerts on this device, even when the portal is closed.
      </Text>
      <Button type="primary" size="small" loading={push.pending} onClick={() => void push.turnOn()}>
        Turn on
      </Button>
      <Button
        type="text"
        size="small"
        aria-label="Dismiss"
        icon={<X size={14} strokeWidth={1.75} />}
        onClick={dismiss}
      />
    </div>
  );
}

/** /account/notifications — device-level on/off plus how to fix a blocked state. */
export function PushSettingsCard() {
  const push = useEnableWithFeedback();
  const browserUnsupported = pushSupport() === 'unsupported';

  // Server not configured: nothing to offer.
  if (push.status === 'unavailable' && !browserUnsupported) return null;

  const canToggle = push.status === 'on' || push.status === 'off';

  return (
    <Card styles={{ body: { padding: 16, display: 'flex', flexDirection: 'column', gap: 12 } }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <BellRing size={18} strokeWidth={1.75} style={{ marginTop: 2, flexShrink: 0 }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text strong>Notifications on this device</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Pop-up alerts on this computer or phone, even when the portal is closed. Your choices
              below decide which ones you get.
            </Text>
          </div>
        </div>
        <Switch
          checked={push.status === 'on'}
          loading={push.pending || push.status === 'loading'}
          disabled={!canToggle}
          onChange={(on) => void (on ? push.turnOn() : push.turnOff())}
          aria-label="Notifications on this device"
        />
      </div>
      <StatusHint status={push.status} browserUnsupported={browserUnsupported} />
    </Card>
  );
}

function StatusHint({
  status,
  browserUnsupported,
}: {
  status: PushStatus;
  browserUnsupported: boolean;
}) {
  if (status === 'denied') {
    return (
      <Alert
        type="warning"
        showIcon
        title="Notifications are blocked for this site"
        description={
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            <li>
              Computer: click the icon to the left of the address bar → Site settings →
              Notifications → Allow, then reload.
            </li>
            <li>Android: Chrome menu ⋮ → Settings → Site settings → Notifications.</li>
          </ul>
        }
      />
    );
  }
  if (status === 'ios-needs-install') {
    return (
      <Alert
        type="info"
        showIcon
        title="On iPhone or iPad, add the portal to your Home Screen first"
        description="In Safari tap Share → Add to Home Screen, open the portal from the new icon, then turn this on. Needs iOS 16.4 or later."
      />
    );
  }
  if (status === 'insecure') {
    return (
      <Alert
        type="info"
        showIcon
        title="Available on the secure (https) site only"
      />
    );
  }
  if (browserUnsupported) {
    return <Alert type="info" showIcon title="This browser doesn't support push notifications" />;
  }
  if (status === 'on') {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        Not seeing them on Windows? Check Settings → System → Notifications: Google Chrome (or
        Edge) must be On and Do not disturb off.
      </Text>
    );
  }
  return null;
}
