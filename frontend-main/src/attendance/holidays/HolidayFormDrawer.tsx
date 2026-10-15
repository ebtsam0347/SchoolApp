import {
  App as AntdApp,
  DatePicker,
  Form,
  Input,
  Select,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useEffect } from 'react';

import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { holidaysApi } from '../api';
import { HOLIDAY_TYPE_LABEL } from '../format';
import type {
  CreateHolidayRequest,
  HolidayDto,
  HolidayType,
  UpdateHolidayRequest,
} from '../types';
import { HOLIDAY_TYPE_OPTIONS } from './helpers';

export type HolidayFormDrawerProps = {
  /** When set, the drawer is in edit mode. */
  holiday?: HolidayDto;
  /** Used as the default year for create mode. */
  defaultYear?: number;
  open: boolean;
  onClose: () => void;
};

type Values = {
  holidayDate: dayjs.Dayjs;
  name: string;
  type: HolidayType;
  description?: string;
};

export function HolidayFormDrawer({
  holiday,
  defaultYear,
  open,
  onClose,
}: HolidayFormDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const isEdit = !!holiday;

  useEffect(() => {
    if (!open) return;
    if (holiday) {
      form.setFieldsValue({
        holidayDate: dayjs(holiday.holidayDate),
        name: holiday.name,
        type: holiday.type,
        description: holiday.description ?? undefined,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        type: 'NATIONAL',
        holidayDate: dayjs(`${defaultYear ?? new Date().getFullYear()}-01-01`),
      });
    }
  }, [open, holiday, defaultYear, form]);

  const createMutation = useMutation({
    mutationFn: (body: CreateHolidayRequest) => holidaysApi.create(body),
    onSuccess: (h) => {
      message.success(`Added "${h.name}"`);
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const updateMutation = useMutation({
    mutationFn: (body: UpdateHolidayRequest) =>
      holidaysApi.update(holiday?.id ?? 0, body),
    onSuccess: (h) => {
      message.success(`Updated "${h.name}"`);
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <DrawerForm<Values>
      title={isEdit ? `Edit "${holiday!.name}"` : 'Add holiday'}
      open={open}
      onClose={onClose}
      form={form}
      size={420}
      submitText={isEdit ? 'Save changes' : 'Add holiday'}
      submitting={submitting}
      onFinish={(v) => {
        const body = {
          holidayDate: v.holidayDate.format('YYYY-MM-DD'),
          name: v.name,
          type: v.type,
          description: v.description,
        };
        if (isEdit) updateMutation.mutate(body);
        else createMutation.mutate(body);
      }}
    >
      <Form.Item
        label="Date"
        name="holidayDate"
        rules={[{ required: true, message: 'Pick the date' }]}
      >
        <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
      </Form.Item>

      <Form.Item
        label="Name"
        name="name"
        rules={[{ required: true, min: 2, message: 'Name the holiday' }]}
      >
        <Input placeholder="e.g. Pakistan Day" />
      </Form.Item>

      <Form.Item label="Type" name="type" rules={[{ required: true }]}>
        <Select
          options={HOLIDAY_TYPE_OPTIONS.map((t) => ({
            value: t,
            label: HOLIDAY_TYPE_LABEL[t],
          }))}
        />
      </Form.Item>

      <Form.Item label="Description" name="description">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Optional — anything employees should know."
        />
      </Form.Item>
    </DrawerForm>
  );
}
