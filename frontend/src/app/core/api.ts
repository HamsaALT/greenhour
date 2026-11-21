import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { formatDuration } from './format';
import { t } from './language';
import { Observable } from 'rxjs';
import {
  Appliance,
  ApplianceInput,
  Household,
  ImportResult,
  ImportStatus,
  MarketDay,
  PlanPreview,
  PlanRequest,
  PriceArea,
  Problem,
  Run,
  RunStatus,
  RunSummary,
} from './models';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  marketDay(area: PriceArea, date?: string): Observable<MarketDay> {
    const params: Record<string, string> = { area };
    if (date) params['date'] = date;
    return this.http.get<MarketDay>('/api/market', { params });
  }

  household(): Observable<Household> {
    return this.http.get<Household>('/api/household');
  }

  updateHousehold(name: string, priceArea: PriceArea): Observable<Household> {
    return this.http.put<Household>('/api/household', { name, priceArea });
  }

  appliances(): Observable<Appliance[]> {
    return this.http.get<Appliance[]>('/api/appliances');
  }

  createAppliance(input: ApplianceInput): Observable<Appliance> {
    return this.http.post<Appliance>('/api/appliances', input);
  }

  updateAppliance(id: number, input: ApplianceInput): Observable<Appliance> {
    return this.http.put<Appliance>(`/api/appliances/${id}`, input);
  }

  deleteAppliance(id: number): Observable<void> {
    return this.http.delete<void>(`/api/appliances/${id}`);
  }

  previewRun(request: PlanRequest): Observable<PlanPreview> {
    return this.http.post<PlanPreview>('/api/runs/preview', request);
  }

  scheduleRun(request: PlanRequest): Observable<Run> {
    return this.http.post<Run>('/api/runs', request);
  }

  runs(): Observable<Run[]> {
    return this.http.get<Run[]>('/api/runs');
  }

  setRunStatus(id: number, status: RunStatus): Observable<Run> {
    return this.http.patch<Run>(`/api/runs/${id}`, { status });
  }

  runSummary(): Observable<RunSummary> {
    return this.http.get<RunSummary>('/api/runs/summary');
  }

  lastImport(): Observable<ImportStatus> {
    return this.http.get<ImportStatus>('/api/admin/market-import');
  }

  runImport(daysBack: number): Observable<ImportResult> {
    return this.http.post<ImportResult>('/api/admin/market-import', null, {
      params: { daysBack },
    });
  }
}

/**
 * Turns an HTTP error into a sentence in the user's language. The backend sends a
 * stable code with each problem and the app has its own text for each one; the
 * English detail in the response is meant for API users and logs.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as Problem | null;
    if (problem?.code) {
      const params = { ...(problem.params ?? {}) } as Record<string, unknown>;
      if (typeof params['runMinutes'] === 'number') {
        params['duration'] = formatDuration(params['runMinutes']);
      }
      const key = `errors.${problem.code}`;
      const translated = t(key, params);
      if (translated !== key) return translated;
    }
    if (error.status === 0) return t('errors.network');
    if (error.status === 400) return t('errors.validation');
    if (error.status === 403) return t('errors.forbidden');
    if (error.status === 404) return t('errors.NOT_FOUND');
  }
  return t('errors.generic');
}
