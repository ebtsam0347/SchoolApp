// Skills panel — catalog-driven add modal; ProficiencyDots encode level
// visually so the page scans without reading text.
// Used by /people/$id/background; surfaced as one of four sub-sections.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import { Trash2 } from 'lucide-react';

import { ProficiencyDots, RequestState, type ProficiencyLevel } from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  skillsApi,
  type AddEmployeeSkillRequest,
  type EmployeeSkillDto,
  type ProficiencyLevelEnum,
} from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

const PROFICIENCY: ProficiencyLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];

export type SkillsPanelProps = {
  employeeId: number;
  addOpen?: boolean;
  onAddOpenChange?: (open: boolean) => void;
};

export function SkillsPanel({
  employeeId,
  addOpen = false,
  onAddOpenChange,
}: SkillsPanelProps) {
  const { token } = useToken();

  const listQuery = useQuery({
    queryKey: ['people', 'skills', employeeId],
    queryFn: ({ signal }) => skillsApi.perEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const skills = listQuery.data ?? [];
  const grouped = groupByCategory(skills);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        empty={skills.length === 0}
        loadingFallback={<Skeleton active paragraph={{ rows: 4 }} />}
        errorTitle="Couldn't load skills"
        emptyTitle="No skills recorded"
        emptyDescription="Add a skill to start building this employee's capability profile."
      >
        {Object.entries(grouped).map(([category, list]) => (
          <div key={category} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div
              style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: 0.6,
                textTransform: 'uppercase',
                color: token.colorTextTertiary,
              }}
            >
              {category}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                gap: 10,
              }}
            >
              {list.map((s) => (
                <Card key={s.id} size="small" styles={{ body: { padding: 14 } }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      gap: 12,
                    }}
                  >
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div style={{ fontSize: 14, fontWeight: 600, color: token.colorTextHeading }}>
                        {s.skillName ?? `#${s.skillId}`}
                      </div>
                      <Tooltip title={s.notes ?? undefined}>
                        <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          {s.proficiency ? <ProficiencyDots level={s.proficiency} /> : null}
                          {s.proficiency ? (
                            <Text type="secondary" style={{ fontSize: 11 }}>
                              {s.proficiency.toLowerCase()}
                            </Text>
                          ) : null}
                          {s.years ? <Tag style={{ margin: 0 }}>{s.years}y</Tag> : null}
                        </div>
                      </Tooltip>
                    </div>
                    <DeleteSkillButton employeeId={employeeId} skill={s} />
                  </div>
                </Card>
              ))}
            </div>
          </div>
        ))}
      </RequestState>

      {addOpen ? (
        <AddSkillModal
          employeeId={employeeId}
          existingSkillIds={new Set(skills.map((s) => s.skillId).filter(Boolean) as number[])}
          onClose={() => onAddOpenChange?.(false)}
        />
      ) : null}
    </div>
  );
}

function DeleteSkillButton({
  employeeId,
  skill,
}: {
  employeeId: number;
  skill: EmployeeSkillDto;
}) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => skillsApi.remove(employeeId, skill.id!),
    onSuccess: () => {
      message.success('Skill removed.');
      void qc.invalidateQueries({ queryKey: ['people', 'skills', employeeId] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });
  return (
    <Popconfirm
      title="Remove skill?"
      okType="danger"
      onConfirm={() => mutation.mutate()}
    >
      <Button
        type="text"
        size="small"
        danger
        loading={mutation.isPending}
        icon={<Trash2 size={14} />}
      />
    </Popconfirm>
  );
}

function AddSkillModal({
  employeeId,
  existingSkillIds,
  onClose,
}: {
  employeeId: number;
  existingSkillIds: Set<number>;
  onClose: () => void;
}) {
  type SkillForm = {
    skillId: number;
    proficiency: ProficiencyLevelEnum;
    years?: number;
    notes?: string;
  };

  const [form] = Form.useForm<SkillForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const catalogQuery = useQuery({
    queryKey: ['catalog', 'skills'],
    queryFn: ({ signal }) => skillsApi.catalog(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  const mutation = useMutation({
    mutationFn: (body: AddEmployeeSkillRequest) => skillsApi.add(employeeId, body),
    onSuccess: () => {
      message.success('Skill added.');
      void qc.invalidateQueries({ queryKey: ['people', 'skills', employeeId] });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  const options = (catalogQuery.data ?? [])
    .filter((s) => s.active !== false && s.id && !existingSkillIds.has(s.id))
    .map((s) => ({
      value: s.id!,
      label: `${s.name}${s.category ? ` · ${s.category.toLowerCase()}` : ''}`,
    }));

  return (
    <Modal
      open
      title="Add skill"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Add skill"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ proficiency: 'INTERMEDIATE' }}
        onFinish={(values) => {
          setTopLevel(null);
          mutation.mutate({
            skillId: values.skillId,
            proficiency: values.proficiency,
            years: values.years ?? undefined,
            notes: values.notes?.trim() || undefined,
          });
        }}
      >
        <Form.Item
          name="skillId"
          label="Skill"
          rules={[{ required: true, message: 'Pick a skill' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            loading={catalogQuery.isLoading}
            options={options}
            placeholder="Search the catalog…"
          />
        </Form.Item>
        <Space.Compact block>
          <Form.Item
            name="proficiency"
            label="Proficiency"
            rules={[{ required: true }]}
            style={{ flex: 1, marginRight: 8 }}
          >
            <Select
              options={PROFICIENCY.map((p) => ({
                value: p,
                label: p[0] + p.slice(1).toLowerCase(),
              }))}
            />
          </Form.Item>
          <Form.Item name="years" label="Years (optional)" style={{ flex: 1 }}>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              max={50}
              step={0.5}
              placeholder="3.5"
            />
          </Form.Item>
        </Space.Compact>
        <Form.Item name="notes" label="Notes (optional)">
          <Input.TextArea rows={2} placeholder="Specifics, projects, certifications…" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- helpers --------------------- */

function groupByCategory(skills: EmployeeSkillDto[]): Record<string, EmployeeSkillDto[]> {
  // We don't have skill.category on EmployeeSkillDto — only via the catalog
  // join. For v1 group by code prefix or "Skills" if absent.
  const out: Record<string, EmployeeSkillDto[]> = {};
  for (const s of skills) {
    const bucket = bucketName(s);
    out[bucket] ??= [];
    out[bucket].push(s);
  }
  return out;
}

function bucketName(s: EmployeeSkillDto): string {
  const code = s.skillCode ?? '';
  if (code.startsWith('TECH_')) return 'Tech';
  if (code.startsWith('LANG_')) return 'Languages';
  if (code.startsWith('TRADE_')) return 'Trades';
  if (code.startsWith('SPORT_')) return 'Sport';
  return 'Skills';
}
