// BFF aggregator for the /admin/sagas console — fans out across saga
// services so the FE doesn't have to. Rows are tagged with their owning
// service so retry can route to the right per-service endpoint.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';

export type SagaService = 'employee' | 'payroll';
export type SagaStatusValue = 'RUNNING' | 'COMPLETED' | 'FAILED';

export type BffSagaRow = {
  service: SagaService;
  id: number;
  sagaName: string;
  correlationKey: string;
  status: SagaStatusValue;
  currentStep: string | null;
  stepIndex: number;
  attemptCount: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
};

export type BffSagasResponse = {
  rows: BffSagaRow[];
  employeeCount: number;
  payrollCount: number;
  employeeDegraded: boolean;
  payrollDegraded: boolean;
};

export type BffSagasParams = {
  service?: SagaService;
  status?: SagaStatusValue;
  size?: number;
};

export const bffSagasApi = {
  list: (params: BffSagasParams = {}, signal?: AbortSignal) =>
    http.get<BffSagasResponse>(`/bff/sagas${qs(params)}`, { signal }),
};
