// Mirrors the backend's JSON. Money and CO2 arrive as numbers; timestamps as ISO strings in UTC.

export type PriceArea = 'DK1' | 'DK2';
export type Goal = 'PRICE' | 'CO2' | 'BALANCED';
export type RunStatus = 'PLANNED' | 'DONE' | 'CANCELLED';
export type ApplianceKind =
  'DISHWASHER' | 'WASHING_MACHINE' | 'DRYER' | 'EV_CHARGER' | 'HEAT_PUMP' | 'OTHER';

export interface MarketSlot {
  start: string;
  priceDkkPerKwh: number;
  co2GramsPerKwh: number | null;
  co2Forecast: boolean;
  demoData: boolean;
}

export interface MarketDay {
  area: PriceArea;
  date: string;
  dataAvailableUntil: string | null;
  slots: MarketSlot[];
}

export interface Household {
  id: number;
  name: string;
  priceArea: PriceArea;
}

export interface Appliance {
  id: number;
  name: string;
  kind: ApplianceKind;
  powerKw: number;
  runMinutes: number;
  energyPerRunKwh: number;
}

export type ApplianceInput = Omit<Appliance, 'id' | 'energyPerRunKwh'>;

export interface PlanRequest {
  applianceId: number;
  earliestStart: string;
  deadline: string;
  goal: Goal;
}

export interface PlanOption {
  start: string;
  costDkk: number;
  co2Grams: number | null;
}

export interface PlanPreview {
  start: string;
  end: string;
  costDkk: number;
  co2Grams: number | null;
  baselineStart: string;
  baselineCostDkk: number;
  baselineCo2Grams: number | null;
  savedDkk: number;
  savedCo2Grams: number | null;
  options: PlanOption[];
}

export interface Run {
  id: number;
  applianceId: number;
  applianceName: string;
  applianceKind: ApplianceKind;
  goal: Goal;
  status: RunStatus;
  earliestStart: string;
  deadline: string;
  plannedStart: string;
  plannedEnd: string;
  energyKwh: number;
  costDkk: number;
  co2Grams: number | null;
  savedDkk: number;
  savedCo2Grams: number | null;
}

export interface RunSummary {
  completedRuns: number;
  savedDkk: number;
  savedCo2Grams: number;
}

export interface ImportResult {
  from: string;
  to: string;
  slotsStored: number;
  slotsWithCo2: number;
}

export interface ImportStatus {
  ranAt: string;
  result: ImportResult | null;
  error: string | null;
}

/** RFC 9457 body the backend returns for errors. */
export interface Problem {
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
  params?: Record<string, unknown>;
}

export const APPLIANCE_KINDS: ApplianceKind[] = [
  'DISHWASHER',
  'WASHING_MACHINE',
  'DRYER',
  'EV_CHARGER',
  'HEAT_PUMP',
  'OTHER',
];
