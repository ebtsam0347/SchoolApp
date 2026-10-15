import {
  App as AntdApp,
  Button,
  Card,
  Popconfirm,
  Space,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Trash2 } from 'lucide-react';
import { useState } from 'react';

import { Can } from '@/auth';
import { StatusPill } from '@/components';
import { getErrorMessage } from '@/lib';
import { holidaysApi } from '../api';
import { HOLIDAY_TYPE_LABEL, HOLIDAY_TYPE_TONE } from '../format';
import { toIsoDate } from '../format';
import type { HolidayDto } from '../types';
import { DateBadge } from './DateBadge';
import { HolidayFormDrawer } from './HolidayFormDrawer';
import { weekdayOf } from './helpers';

const { Text } = Typography;
const { useToken } = antdTheme;

export function HolidayCard({ holiday }: { holiday: HolidayDto }) {
  const { token } = useToken();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const today = toIsoDate(new Date());
  const past = holiday.holidayDate < today;
  const [openEdit, setOpenEdit] = useState(false);

  const deleteMutation = useMutation({
    mutationFn: () => holidaysApi.delete(holiday.id),
    onSuccess: () => {
      message.success(`Removed "${holiday.name}"`);
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <Card
      size="small"
      styles={{
        body: { padding: 14, opacity: past ? 0.65 : 1 },
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <DateBadge iso={holiday.holidayDate} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: 14,
              fontWeight: 600,
              color: token.colorText,
              lineHeight: 1.3,
            }}
          >
            {holiday.name}
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              marginTop: 4,
            }}
          >
            <StatusPill tone={HOLIDAY_TYPE_TONE[holiday.type]}>
              {HOLIDAY_TYPE_LABEL[holiday.type]}
            </StatusPill>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {weekdayOf(holiday.holidayDate)}
            </Text>
          </div>
          {holiday.description ? (
            <Text
              type="secondary"
              style={{ fontSize: 12, display: 'block', marginTop: 6 }}
            >
              {holiday.description}
            </Text>
          ) : null}
        </div>
        <Can I="manage" a="attendance">
          <Space size={2}>
            <Button
              type="text"
              size="small"
              icon={<Pencil size={14} />}
              onClick={() => setOpenEdit(true)}
              aria-label="Edit holiday"
            />
            <Popconfirm
              title="Remove this holiday?"
              description="Future attendance will treat this date as a normal working day. Past attendance is unaffected."
              okText="Remove"
              okButtonProps={{ danger: true }}
              onConfirm={() => deleteMutation.mutate()}
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<Trash2 size={14} />}
                loading={deleteMutation.isPending}
              />
            </Popconfirm>
          </Space>
        </Can>
      </div>
      <HolidayFormDrawer
        holiday={holiday}
        open={openEdit}
        onClose={() => setOpenEdit(false)}
      />
    </Card>
  );
}
