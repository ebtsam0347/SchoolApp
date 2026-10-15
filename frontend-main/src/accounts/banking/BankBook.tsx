import { Card, Col, Row, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Banknote } from 'lucide-react';

import { KpiGrid, MoneyCell, PageHeader, Section, StatCard } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { bankAccounts, type BankAccount } from '../mock/data';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

export function BankBook() {
  const { token } = useToken();
  const totalPkr = bankAccounts.reduce((s, a) => s + a.pkrEquivalent, 0);

  const columns: ColumnsType<BankAccount> = [
    {
      title: 'Account',
      dataIndex: 'name',
      key: 'name',
      render: (v: string, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{v}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.bank} · {row.number}
          </Text>
        </Space>
      ),
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 110, render: (c) => <Tag>{c}</Tag> },
    {
      title: 'Balance',
      dataIndex: 'balance',
      key: 'balance',
      width: 200,
      align: 'right',
      render: (v: number, row) =>
        row.currency === 'PKR' ? (
          <MoneyCell amount={v} compact />
        ) : (
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>
            {row.currency} {v.toLocaleString('en-US', { maximumFractionDigits: 2 })}
          </span>
        ),
    },
    {
      title: 'PKR equivalent',
      dataIndex: 'pkrEquivalent',
      key: 'pkrEquivalent',
      width: 200,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="BANKING"
        title="Bank book"
        description="Live balance across every operating, payroll, and export account."
      />
      <PreviewNotice />

      <KpiGrid minColumnWidth={240}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={Banknote}
          label="Total cash (PKR equivalent)"
          value={<MoneyCell amount={totalPkr} compact />}
          delta={`Across ${bankAccounts.length} accounts`}
        />
      </KpiGrid>

      <Section title="Accounts">
        <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
          {bankAccounts.map((a) => (
            <Col xs={24} sm={12} lg={6} key={a.id}>
              <Card
                styles={{
                  body: { padding: 16, display: 'flex', flexDirection: 'column', gap: 6 },
                }}
              >
                <Text type="secondary" style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                  {a.bank}
                </Text>
                <Title level={5} style={{ margin: 0 }}>{a.name}</Title>
                <Text type="secondary" style={{ fontSize: 12 }}>{a.number}</Text>
                <div style={{ marginTop: 6 }}>
                  <MoneyCell
                    amount={a.pkrEquivalent}
                    compact
                    style={{ fontSize: 18, fontWeight: 700, color: token.colorText }}
                  />
                  {a.currency !== 'PKR' ? (
                    <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
                      {a.currency} {a.balance.toLocaleString('en-US')}
                    </Text>
                  ) : null}
                </div>
              </Card>
            </Col>
          ))}
        </Row>

        <Table<BankAccount>
          rowKey="id"
          columns={columns}
          dataSource={bankAccounts}
          pagination={false}
          size="middle"
        />
      </Section>
    </Space>
  );
}
