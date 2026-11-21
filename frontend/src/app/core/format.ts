import { intlLocale, t } from './language';

// Times are always Danish time, whatever the browser's time zone is. Only the
// way they're written follows the chosen language.
const ZONE = 'Europe/Copenhagen';

// Intl formatters are relatively expensive to build, so keep one per locale and style.
const cache = new Map<string, Intl.DateTimeFormat | Intl.NumberFormat>();

function dateFormat(name: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const locale = intlLocale();
  const key = `${locale}|${name}`;
  let format = cache.get(key) as Intl.DateTimeFormat | undefined;
  if (!format) {
    format = new Intl.DateTimeFormat(locale, { timeZone: ZONE, ...options });
    cache.set(key, format);
  }
  return format;
}

function numberFormat(name: string, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  const locale = intlLocale();
  const key = `${locale}|${name}`;
  let format = cache.get(key) as Intl.NumberFormat | undefined;
  if (!format) {
    format = new Intl.NumberFormat(locale, options);
    cache.set(key, format);
  }
  return format;
}

export function formatTime(iso: string): string {
  return dateFormat('time', { hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

export function formatDayTime(iso: string): string {
  return dateFormat('dayTime', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

export function formatLongDate(isoDate: string): string {
  // Noon avoids the date flipping when converting a bare date across time zones.
  return dateFormat('longDate', { weekday: 'long', day: 'numeric', month: 'long' }).format(
    new Date(`${isoDate}T12:00:00Z`),
  );
}

/** yyyy-mm-dd for a moment, in Danish time. Not for display. */
export function danishDate(moment: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(moment);
}

/** 1.234,50 in Danish, 1,234.50 in English. */
export function formatNumber(value: number, decimals: number): string {
  return numberFormat(`n${decimals}`, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/** "3,80 kr." in Danish, "DKK 3.80" in English. */
export function formatDkk(value: number, decimals = 2): string {
  return numberFormat(`dkk${decimals}`, {
    style: 'currency',
    currency: 'DKK',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

export function formatPricePerKwh(value: number): string {
  return `${formatNumber(value, 2)} ${t('units.perKwh')}`;
}

export function formatCo2(grams: number | null): string {
  if (grams === null) return '–';
  return grams >= 1000 ? `${formatNumber(grams / 1000, 1)} kg` : `${formatNumber(grams, 0)} g`;
}

export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return t('units.minutes', { m });
  return m === 0 ? t('units.hours', { h }) : t('units.hoursMinutes', { h, m });
}

/** Value for an <input type="datetime-local">, in the browser's local time. */
export function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/**
 * Default deadline: the next 07:00 that is at least three hours away. Most
 * flexible loads are overnight loads that must be done by breakfast.
 */
export function defaultDeadline(now: Date): Date {
  const deadline = new Date(now);
  deadline.setHours(7, 0, 0, 0);
  if (deadline.getTime() - now.getTime() < 3 * 60 * 60 * 1000) {
    deadline.setDate(deadline.getDate() + 1);
  }
  return deadline;
}
