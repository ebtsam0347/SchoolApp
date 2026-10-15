// Step: optional self-service-login (ESS) provisioning. When the toggle
// is on, capture username / email / temp password; the saga's
// PROVISION_AUTH_USER step uses these to create the auth-service user.

import { Alert, Button, Form, Input, Space, Switch, type FormInstance } from 'antd';
import { Sparkles } from 'lucide-react';
import { Section } from '@/components';
import type { WizardForm } from './types';

export type EssStepProps = {
  canProvisionEss: boolean;
  form: FormInstance<WizardForm>;
};

// Memorable adjective-noun-NN passwords. Always 12+ chars (passes the
// `min: 10` rule) and easy to read aloud over the phone or in person.
const ADJECTIVES = [
  'Brave', 'Calm', 'Bright', 'Quick', 'Lucky', 'Royal', 'Swift', 'Bold',
  'Happy', 'Quiet', 'Sunny', 'Mighty', 'Eager', 'Noble', 'Clever',
];
const NOUNS = [
  'Tiger', 'River', 'Maple', 'Falcon', 'Mountain', 'Sparrow', 'Cedar',
  'Comet', 'Lantern', 'Harbor', 'Meadow', 'Forest', 'Glacier', 'Marble',
  'Otter', 'Robin',
];

function generateMemorablePassword(): string {
  const pick = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)];
  const num = Math.floor(Math.random() * 90) + 10; // 10–99
  return `${pick(ADJECTIVES)}-${pick(NOUNS)}-${num}`;
}

export function EssStep({ canProvisionEss, form }: EssStepProps) {
  const provisionEss = Form.useWatch('provisionEss', form);
  const workEmail = Form.useWatch('workEmail', form);

  function handleGenerate() {
    const pwd = generateMemorablePassword();
    form.setFieldValue('temporaryPassword', pwd);
    form.validateFields(['temporaryPassword']).catch(() => {});
  }

  return (
    <Section
      title="Self-service login"
      subtitle={
        canProvisionEss
          ? "Optional. Turn on to give the new employee a login so they can view their payslips, request leave, and check attendance. You can also do this later from their profile."
          : "You don't have permission to grant a login. The employee will be added without one; an admin can grant access later."
      }
    >
      <Form.Item
        name="provisionEss"
        label="Give the new employee a login now?"
        valuePropName="checked"
      >
        <Switch disabled={!canProvisionEss} />
      </Form.Item>
      {provisionEss ? (
        <>
          {!workEmail ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              title="No work email on the Identity step"
              description="Username defaults to the work-email local part — go back and add a work email, or set a username explicitly below."
            />
          ) : null}
          <Space.Compact block>
            <Form.Item
              name="username"
              label="Username"
              style={{ flex: 1, marginRight: 8 }}
            >
              <Input placeholder="Defaults to the work-email local part" />
            </Form.Item>
            <Form.Item
              name="email"
              label="Email"
              rules={[{ type: 'email', message: 'Looks invalid' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="@forwardsports.com" />
            </Form.Item>
          </Space.Compact>
          <Form.Item
            name="temporaryPassword"
            label={
              <Space>
                <span>Temporary password</span>
                <Button
                  type="link"
                  size="small"
                  icon={<Sparkles size={12} strokeWidth={1.75} />}
                  onClick={handleGenerate}
                  style={{ padding: 0, height: 'auto' }}
                >
                  Generate
                </Button>
              </Space>
            }
            rules={[
              { required: true, message: 'Required when granting a login' },
              { min: 10, message: 'Minimum 10 characters' },
            ]}
            extra="Share this with the employee securely. They'll be forced to change it on first login."
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </>
      ) : null}
    </Section>
  );
}
