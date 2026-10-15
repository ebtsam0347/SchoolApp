// Create / edit modal for a Factory Tour (travel/vehicle) request —
// self-service, always raised by the signed-in employee for themselves.
// A centered Modal rather than a side drawer: the form is wide enough to
// pair fields two-up, so it reads without scrolling on a normal screen.
//
// Writes go to the Travel API (POST /travel/requests, PATCH
// /travel/requests/{id}), matching the reads on the list page. That API
// models a request as a header plus legs[] and participants[], so the flat
// form below is expanded into a single leg on submit — see buildBody.
//
// The form only persists the record — it does not call POST /{id}/submit.
// Moving the request into the approval workflow is the API's job, so the
// drawer never fires a second submit call. Passing `tour` switches the
// drawer into edit mode (PATCH).

import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  App as AntdApp,
  Button,
  Checkbox,
  Col,
  Collapse,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Globe, MapPin, Plus, Trash2, type LucideIcon } from 'lucide-react';

import { getErrorMessage } from '@/lib';
import { employeePickerApi } from '@/people';
import {
  travelRequestApi,
  type CreateTravelRequest,
  type TravelVehicleSourceCode,
} from '@/travel';
import { COUNTRY_OPTIONS, CURRENCY_OPTIONS, currencyForCountry } from '../countries';
import { VEHICLE_SOURCE_LABEL } from '../format';
import type {
  FactoryTourDto,
  TravelPerson,
  TravelType,
  VehicleSource,
} from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export type FactoryTourFormDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** Present → edit an existing DRAFT (PATCH). Absent → create a new one. */
  tour?: FactoryTourDto | null;
};

/** One hop of the trip — maps 1:1 onto an entry of the API's `legs[]`. */
type LegValues = {
  origin: string;
  /** Domestic picks a city off the list; abroad it's free text. */
  destination: string;
  vehicleSource?: VehicleSource;
  transportationType?: string;
  vehicleNo?: string;
  totalDistanceKm?: number;
};

type Values = {
  localTourStart: dayjs.Dayjs;
  localTourEnd: dayjs.Dayjs;
  internationalTourStart: dayjs.Dayjs;
  internationalTourEnd: dayjs.Dayjs;
  country?: string;
  currency?: string;
  purpose?: string;
  legs: LegValues[];
};

const TRAVEL_TABS: { key: TravelType; label: string; icon: LucideIcon }[] = [
  { key: 'LOCAL', label: 'Domestic', icon: MapPin },
  { key: 'INTERNATIONAL', label: 'International', icon: Globe },
];
const VEHICLE_SOURCE_OPTIONS: VehicleSource[] = [
  'COMPANY_VEHICLE',
  'PERSONAL_VEHICLE',
  'RENTED_VEHICLE',
  'PUBLIC_TRANSPORT',
];

// ---------------------------------------------------------------------------
// Wire codes for POST /travel/requests. The employee service validates these
// against its own reference tables but exposes no catalog endpoint for them,
// so they are derived from the form here. If the service rejects a code, this
// block is the only place to correct — nothing else hardcodes them.
// ---------------------------------------------------------------------------

/** travelTypeCode — the tour form only distinguishes domestic vs. abroad. */
const TRAVEL_TYPE_CODE: Record<TravelType, string> = {
  LOCAL: 'LOCAL',
  INTERNATIONAL: 'INTERNATIONAL',
};

/** vehicleSourceCode — the leg enum the service accepts. */
const VEHICLE_SOURCE_CODE: Record<VehicleSource, TravelVehicleSourceCode> = {
  COMPANY_VEHICLE: 'COMPANY',
  PERSONAL_VEHICLE: 'EMPLOYEE',
  RENTED_VEHICLE: 'RENTAL',
  PUBLIC_TRANSPORT: 'PUBLIC',
};

/** Local tours default to PKR — the form only asks for a currency abroad. */
const DEFAULT_CURRENCY_CODE = 'PKR';

/**
 * transportationTypeCode is required per leg but the form collects it as free
 * text ("Car", "Van"), so it is normalised to a code shape; an international
 * tour with nothing entered flies.
 */
function transportationTypeCode(raw: string | undefined, travelType: TravelType): string {
  const normalised = raw?.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_');
  if (normalised) return normalised;
  return travelType === 'INTERNATIONAL' ? 'AIR' : 'OTHER';
}

// Placeholder — no locations/sites catalog exists yet. Swap for a real
// catalogsApi.locations() once one ships.
const ORIGIN_OPTIONS = ['The Spirit of Islam School', 'From Home', 'Other'];

// Major Pakistani cities, used for Origin/Destination when Travel Type is
// Local — local tours move between cities within Pakistan.
const PAKISTAN_CITIES = [
  'Karachi', 'Lahore', 'Faisalabad', 'Rawalpindi', 'Gujranwala', 'Peshawar',
  'Multan', 'Hyderabad', 'Islamabad', 'Quetta', 'Bahawalpur', 'Sargodha',
  'Sialkot', 'Sukkur', 'Larkana', 'Sheikhupura', 'Rahim Yar Khan', 'Jhang',
  'Gujrat', 'Mardan', 'Kasur', 'Dera Ghazi Khan', 'Sahiwal', 'Nawabshah',
  'Okara', 'Mingora', 'Mirpur Khas', 'Chiniot', 'Kamoke', 'Mandi Bahauddin',
  'Jhelum', 'Sadiqabad', 'Jacobabad', 'Shikarpur', 'Khanewal', 'Hafizabad',
  'Kohat', 'Kotri', 'Muzaffargarh', 'Khanpur', 'Gojra', 'Bahawalnagar',
  'Muridke', 'Pakpattan', 'Abottabad', 'Tando Adam', 'Jaranwala', 'Chishtian',
  'Attock', 'Vehari', 'Dera Ismail Khan', 'Nowshera', 'Mianwali',
  'Charsadda', 'Kot Addu', 'Khairpur', 'Mirpur (AJK)', 'Muzaffarabad',
  'Gwadar', 'Turbat', 'Chaman', 'Zhob', 'Sibi', 'Loralai', 'Khuzdar',
  'Ghotki', 'Badin', 'Thatta', 'Umerkot', 'Skardu', 'Gilgit', 'Chitral',
  'Swabi', 'Bannu', 'Hangu', 'Karak', 'Toba Tek Singh', 'Narowal',
  'Nankana Sahib', 'Wazirabad', 'Layyah', 'Rajanpur', 'Taxila',
];

export function FactoryTourFormDrawer({ open, onClose, tour }: FactoryTourFormDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();
  const { token } = useToken();
  const isEdit = !!tour;

  const [travelType, setTravelType] = useState<TravelType>('LOCAL');
  // Legs are an accordion: adding a hop collapses the previous one so a
  // multi-stop trip doesn't turn the form into an endless scroll.
  const [activeLeg, setActiveLeg] = useState('0');
  const [addingPersons, setAddingPersons] = useState(false);
  const [personCardNo, setPersonCardNo] = useState<string | undefined>();
  const [personSearch, setPersonSearch] = useState('');
  const [additionalPersons, setAdditionalPersons] = useState<TravelPerson[]>([]);

  // Sync local (non-Form) state to the row being edited each time the
  // modal opens — the Modal itself remounts the Form (destroyOnHidden)
  // so `initialValues` below handles the Form-bound fields on its own.
  useEffect(() => {
    if (!open) return;
    setTravelType(tour?.travelType ?? 'LOCAL');
    setAdditionalPersons(tour?.additionalTravelPersons ?? []);
    setAddingPersons((tour?.additionalTravelPersons.length ?? 0) > 0);
    setPersonCardNo(undefined);
    setActiveLeg('0');
  }, [open, tour]);

  // Drives the collapsed hop headers ("Route 2 · Lahore → Karachi").
  const legValues = Form.useWatch('legs', form) as LegValues[] | undefined;

  // A trip is one continuous chain: you leave from wherever the previous hop
  // dropped you. Only the first hop picks its own origin — the rest mirror the
  // preceding destination, and follow it when that destination is edited.
  useEffect(() => {
    if (!legValues || legValues.length < 2) return;
    legValues.forEach((leg, index) => {
      if (index === 0) return;
      const inherited = legValues[index - 1]?.destination;
      if (leg?.origin !== inherited) {
        form.setFieldValue(['legs', index, 'origin'], inherited);
      }
    });
  }, [legValues, form]);

  // Colleague options for "Person CardNo" — org-wide, via the permission-free
  // /employees/picker search (GET /employees is HR-only and 403s for an
  // ordinary self-service employee).
  const colleaguesQuery = useQuery({
    queryKey: ['employees', 'picker', 'factory-tour-companions', personSearch],
    queryFn: ({ signal }) => employeePickerApi.search({ q: personSearch, limit: 50 }, signal),
    enabled: open && addingPersons,
    staleTime: 30_000,
  });

  const resetLocalState = () => {
    setAddingPersons(false);
    setAdditionalPersons([]);
    setPersonCardNo(undefined);
  };

  // Persists only — no follow-up submit call is made from the client.
  const submitMutation = useMutation({
    mutationFn: (body: CreateTravelRequest) =>
      isEdit
        ? travelRequestApi.update(tour?.id ?? 0, body)
        : travelRequestApi.create(body),
    onSuccess: () => {
      message.success('Travel request submitted for approval');
      queryClient.invalidateQueries({ queryKey: ['travel-requests'] });
      queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
      form.resetFields();
      resetLocalState();
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const addPerson = (cardNo?: string) => {
    if (!cardNo) return;
    if (additionalPersons.some((p) => p.cardNo === cardNo)) {
      setPersonCardNo(undefined);
      return;
    }
    const candidate = colleaguesQuery.data?.items.find(
      (e) => e.employeeCode === cardNo,
    );
    setAdditionalPersons((prev) => [
      ...prev,
      { cardNo, name: candidate?.fullName },
    ]);
    setPersonCardNo(undefined);
  };

  const removePerson = (cardNo: string) =>
    setAdditionalPersons((prev) => prev.filter((p) => p.cardNo !== cardNo));

  // Expands the flat form into the Travel API's header + legs + participants
  // shape. The form describes one hop, so exactly one leg is sent; the caller
  // is the requester server-side, so participants carries only co-travellers.
  const buildBody = (v: Values): CreateTravelRequest => {
    const isLocal = travelType === 'LOCAL';
    const start = isLocal ? v.localTourStart : v.internationalTourStart;
    const end = isLocal ? v.localTourEnd : v.internationalTourEnd;

    return {
      travelTypeCode: TRAVEL_TYPE_CODE[travelType],
      currencyCode: (isLocal ? DEFAULT_CURRENCY_CODE : v.currency) ?? DEFAULT_CURRENCY_CODE,
      purpose: v.purpose?.trim() ?? '',
      startDatetime: start.toISOString(),
      endDatetime: end.toISOString(),
      participants: additionalPersons.map((p) => ({ employeeCode: p.cardNo })),
      // Every hop the user added becomes one leg, numbered in form order. The
      // trip's start/end bound each leg — the form asks for dates once, not
      // per hop.
      legs: (v.legs ?? []).map((leg, idx) => ({
        legSequence: idx + 1,
        originLocationCode: leg.origin,
        destinationLocationCode: leg.destination,
        plannedDepartureAt: start.toISOString(),
        plannedArrivalAt: end.toISOString(),
        transportationTypeCode: transportationTypeCode(leg.transportationType, travelType),
        vehicleSourceCode:
          isLocal && leg.vehicleSource ? VEHICLE_SOURCE_CODE[leg.vehicleSource] : undefined,
        vehicleNumber: isLocal ? leg.vehicleNo : undefined,
        vehicleDescription: isLocal ? leg.transportationType : undefined,
        plannedDistanceKm: isLocal ? leg.totalDistanceKm : undefined,
      })),
    };
  };

  const initialValues: Partial<Values> = tour
    ? {
        ...(tour.travelType === 'LOCAL'
          ? { localTourStart: dayjs(tour.tourStart), localTourEnd: dayjs(tour.tourEnd) }
          : {
              internationalTourStart: dayjs(tour.tourStart),
              internationalTourEnd: dayjs(tour.tourEnd),
            }),
        country: tour.country ?? undefined,
        currency: tour.currency ?? undefined,
        purpose: tour.purpose ?? undefined,
        // The list view collapses a request to a single origin -> destination
        // hop, so editing starts from that one leg.
        legs: [
          {
            origin: tour.origin,
            // Domestic requests carry the destination as the city.
            destination: (tour.travelType === 'LOCAL' ? tour.city : null) ?? tour.destination,
            vehicleSource: tour.vehicleSource ?? undefined,
            transportationType: tour.transportationType ?? undefined,
            vehicleNo: tour.vehicleNo ?? undefined,
            totalDistanceKm: tour.totalDistanceKm ?? undefined,
          },
        ],
      }
    : { legs: [{} as LegValues] };

  const requestClose = () => {
    if (submitMutation.isPending) return;
    if (!form.isFieldsTouched(true)) {
      onClose();
      return;
    }
    modal.confirm({
      title: 'Discard changes?',
      content: 'Your unsaved input will be lost. You can keep editing or discard and close.',
      okText: 'Discard',
      okButtonProps: { danger: true },
      cancelText: 'Keep editing',
      onOk: onClose,
    });
  };

  return (
    <Modal
      title={isEdit ? `Edit travel request ${tour!.requestId}` : 'Travel Request'}
      open={open}
      onCancel={requestClose}
      width="min(1080px, calc(100vw - 32px))"
      centered
      destroyOnHidden
      // overflowX hidden: Row's gutter uses negative margins, which otherwise
      // push a phantom horizontal scrollbar onto the scroll container.
      styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', overflowX: 'hidden' } }}
      footer={[
        <Button key="cancel" onClick={requestClose}>
          Cancel
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={submitMutation.isPending}
          onClick={() => form.submit()}
        >
          Submit for approval
        </Button>,
      ]}
    >
      <Form<Values>
        form={form}
        layout="vertical"
        initialValues={initialValues}
        onFinish={(v) => submitMutation.mutate(buildBody(v))}
      >
        <SectionTitle>Travel Info</SectionTitle>

      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Form.Item label="Travel Type">
            <div
              style={{
                display: 'flex',
                gap: 4,
                padding: 4,
                background: token.colorFillQuaternary,
                borderRadius: 12,
                width: 'fit-content',
              }}
            >
              {TRAVEL_TABS.map(({ key, label, icon: Icon }) => {
                const active = travelType === key;
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setTravelType(key);
                      form.setFieldsValue({ country: undefined, currency: undefined });
                    }}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '8px 14px',
                      fontSize: 13,
                      fontWeight: 500,
                      borderRadius: 8,
                      border: 'none',
                      cursor: 'pointer',
                      color: active ? token.colorText : token.colorTextSecondary,
                      background: active ? token.colorBgContainer : 'transparent',
                      boxShadow: active
                        ? `0 1px 0 ${token.colorBorderSecondary}, 0 4px 12px color-mix(in srgb, ${token.colorText} 6%, transparent)`
                        : 'none',
                      transition: 'background 160ms ease, color 160ms ease',
                    }}
                  >
                    <Icon size={15} strokeWidth={1.75} />
                    {label}
                  </button>
                );
              })}
            </div>
          </Form.Item>
        </Col>

        {travelType === 'LOCAL' ? (
          <>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                label="Travel Start"
                name="localTourStart"
                rules={[{ required: true, message: 'Pick a start date/time' }]}
              >
                <DatePicker
                  showTime
                  format="DD MMM YYYY, hh:mm A"
                  style={{ width: '100%' }}
                  onChange={(value) => form.setFieldValue('localTourEnd', value ?? undefined)}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                label="Travel End"
                name="localTourEnd"
                dependencies={['localTourStart']}
                rules={[
                  { required: true, message: 'Pick an end date/time' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const start = getFieldValue('localTourStart') as dayjs.Dayjs | undefined;
                      if (!value || !start || value.isAfter(start)) return Promise.resolve();
                      return Promise.reject(new Error('End must be after start'));
                    },
                  }),
                ]}
              >
                <DatePicker showTime format="DD MMM YYYY, hh:mm A" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </>
        ) : (
          <>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                label="Travel Start"
                name="internationalTourStart"
                rules={[{ required: true, message: 'Pick a start date/time' }]}
              >
                <DatePicker
                  showTime
                  format="DD MMM YYYY, hh:mm A"
                  style={{ width: '100%' }}
                  onChange={(value) => form.setFieldValue('internationalTourEnd', value ?? undefined)}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                label="Travel End"
                name="internationalTourEnd"
                dependencies={['internationalTourStart']}
                rules={[
                  { required: true, message: 'Pick an end date/time' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const start = getFieldValue('internationalTourStart') as dayjs.Dayjs | undefined;
                      if (!value || !start || value.isAfter(start)) return Promise.resolve();
                      return Promise.reject(new Error('End must be after start'));
                    },
                  }),
                ]}
              >
                <DatePicker showTime format="DD MMM YYYY, hh:mm A" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </>
        )}
      </Row>

      <Form.Item
        label="Purpose To Go"
        name="purpose"
        rules={[{ required: true, message: 'Enter the purpose of travel' }]}
      >
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 3 }}
          placeholder="Enter purpose here..."
        />
      </Form.Item>

      {travelType === 'INTERNATIONAL' ? (
        <Row gutter={16}>
          <Col xs={24} sm={12} md={8}>
            <Form.Item
              label="Country"
              name="country"
              rules={[{ required: true, message: 'Pick a country' }]}
            >
              <Select
                showSearch
                placeholder="Select Country"
                optionFilterProp="label"
                options={COUNTRY_OPTIONS}
                onChange={(country: string) => {
                  const currency = currencyForCountry(country);
                  if (currency) form.setFieldValue('currency', currency);
                }}
              />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Form.Item
              label="Currency"
              name="currency"
              rules={[{ required: true, message: 'Select a currency' }]}
            >
              <Select placeholder="Select Currency" options={CURRENCY_OPTIONS} />
            </Form.Item>
          </Col>
        </Row>
      ) : null}

      <SectionTitle>Route{travelType === 'LOCAL' ? ' & vehicle' : ''}</SectionTitle>

      <Form.List name="legs">
        {(fields, { add, remove }) => (
          <>
            <Collapse
              accordion
              size="small"
              activeKey={activeLeg}
              onChange={(keys) => setActiveLeg(keys[0] ?? '')}
              style={{ marginBottom: 12 }}
              items={fields.map((field, index) => ({
                key: String(index),
                label: legSummary(legValues?.[index], index),
                // Keep collapsed hops mounted so their rules still run on submit.
                forceRender: true,
                extra:
                  fields.length > 1 ? (
                    <Button
                      size="small"
                      danger
                      type="text"
                      icon={<Trash2 size={14} />}
                      onClick={(e) => {
                        e.stopPropagation();
                        remove(field.name);
                        setActiveLeg(String(Math.max(0, index - 1)));
                      }}
                    />
                  ) : undefined,
                children: (
                  <>
                    <Row gutter={16}>
                      <Col xs={24} md={12}>
                        <Form.Item
                          label="Origin"
                          name={[field.name, 'origin']}
                          rules={[
                            {
                              required: true,
                              message:
                                index === 0
                                  ? 'Pick an origin'
                                  : `Set Route ${index}'s destination first`,
                            },
                          ]}
                          extra={
                            index > 0 && legValues?.[index - 1]?.destination
                              ? `Carried over from Route ${index}'s destination`
                              : undefined
                          }
                        >
                          {index === 0 ? (
                            <Select
                              placeholder="Select Origin"
                              options={ORIGIN_OPTIONS.map((o) => ({ value: o, label: o }))}
                            />
                          ) : (
                            // Mirrors the previous hop's destination. readOnly (not
                            // disabled) so the inherited city reads as filled-in text
                            // rather than greyed-out placeholder.
                            <Input
                              readOnly
                              placeholder={`Set Route ${index}'s destination first`}
                              style={{
                                background: token.colorFillQuaternary,
                                cursor: 'default',
                              }}
                            />
                          )}
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item
                          label="Destination"
                          name={[field.name, 'destination']}
                          rules={[{ required: true, message: 'Pick a destination' }]}
                        >
                          {travelType === 'LOCAL' ? (
                            <Select
                              showSearch
                              placeholder="Select Destination"
                              optionFilterProp="label"
                              options={PAKISTAN_CITIES.map((c) => ({ value: c, label: c }))}
                            />
                          ) : (
                            <Input placeholder="Destination To" />
                          )}
                        </Form.Item>
                      </Col>
                    </Row>

                    {travelType === 'LOCAL' ? (
                      <Row gutter={16}>
                        <Col xs={24} sm={12} md={6}>
                          <Form.Item
                            label="Vehicle Source"
                            name={[field.name, 'vehicleSource']}
                            rules={[{ required: true, message: 'Pick a vehicle source' }]}
                          >
                            <Select
                              placeholder="Select Vehicle Source"
                              options={VEHICLE_SOURCE_OPTIONS.map((v) => ({
                                value: v,
                                label: VEHICLE_SOURCE_LABEL[v],
                              }))}
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                          <Form.Item
                            label="Transportation Type"
                            name={[field.name, 'transportationType']}
                          >
                            <Input placeholder="e.g. Car, Van, Bike" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                          <Form.Item label="Vehicle Number" name={[field.name, 'vehicleNo']}>
                            <Input placeholder="Vehicle number" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                          <Form.Item label="Distance (KM)" name={[field.name, 'totalDistanceKm']}>
                            <InputNumber min={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                      </Row>
                    ) : null}
                  </>
                ),
              }))}
            />

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="dashed"
                block
                icon={<Plus size={14} />}
                onClick={() => {
                  // Start the new hop where the last one ended.
                  add({ origin: legValues?.[fields.length - 1]?.destination });
                  // Collapse whatever was open and reveal the new hop.
                  setActiveLeg(String(fields.length));
                }}
              >
                Add another route and vehicle
              </Button>
            </Form.Item>
          </>
        )}
      </Form.List>

      <SectionTitle>Participants</SectionTitle>

      <Form.Item style={{ marginBottom: addingPersons ? 8 : 0 }}>
        <Checkbox checked={addingPersons} onChange={(e) => setAddingPersons(e.target.checked)}>
          Add Additional Travel Employees
        </Checkbox>
      </Form.Item>

      {addingPersons ? (
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            Want to add a travel employee with you? Enter their Card No.
          </Text>
          <Select
            showSearch
            allowClear
            value={personCardNo}
            onChange={addPerson}
            onSearch={setPersonSearch}
            filterOption={false}
            placeholder="Employee CardNo:"
            loading={colleaguesQuery.isFetching}
            notFoundContent={colleaguesQuery.isFetching ? 'Searching…' : 'No matches'}
            style={{ width: '100%' }}
            options={(colleaguesQuery.data?.items ?? [])
              .filter((e) => e.employeeCode)
              .map((e) => ({
                value: e.employeeCode!,
                label: [e.employeeCode, e.fullName].filter(Boolean).join(' · '),
              }))}
          />

          {additionalPersons.length > 0 ? (
            <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 4 }}>
              {additionalPersons.map((p, idx) => (
                <span key={p.cardNo} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <Tag closable onClose={() => removePerson(p.cardNo)} style={{ marginInlineEnd: 0 }}>
                    {[p.name, p.cardNo].filter(Boolean).join(' - ')}
                  </Tag>
                  {idx < additionalPersons.length - 1 ? <Text type="secondary">,</Text> : null}
                </span>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
      </Form>
    </Modal>
  );
}

/** Collapsed-hop header: "Route 2" plus the route itself once it's filled in. */
function legSummary(leg: LegValues | undefined, index: number): ReactNode {
  const route = leg?.origin && leg?.destination ? `${leg.origin} → ${leg.destination}` : null;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'baseline', gap: 8 }}>
      <Text strong style={{ fontSize: 13 }}>
        Route {index + 1}
      </Text>
      {route ? (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {route}
        </Text>
      ) : null}
    </span>
  );
}

function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <Divider titlePlacement="left" style={{ marginTop: 0, marginBottom: 12 }}>
      <Text strong style={{ fontSize: 13 }}>
        {children}
      </Text>
    </Divider>
  );
}
