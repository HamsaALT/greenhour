import { TestBed } from '@angular/core/testing';
import { provideTestI18n, startLanguage } from '../testing/i18n';
import {
  danishDate,
  defaultDeadline,
  formatCo2,
  formatDkk,
  formatDuration,
  formatLongDate,
  formatNumber,
  formatTime,
  toLocalInputValue,
} from './format';

// Intl output contains non-breaking spaces; compare with ordinary ones.
const plain = (s: string) => s.replace(/\s/g, ' ');

describe('format', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
  });

  describe('in Danish (the default)', () => {
    beforeEach(() => startLanguage());

    it('writes numbers and money the Danish way', () => {
      expect(formatNumber(1234.5, 2)).toBe('1.234,50');
      expect(plain(formatDkk(1234.5))).toBe('1.234,50 kr.');
    });

    it('writes times and dates the Danish way, in Danish time', () => {
      // 23:00 UTC in November is midnight in Copenhagen.
      expect(formatTime('2026-11-18T23:00:00Z')).toBe('00.00');
      // Browsers write "onsdag den 18. november"; Node's ICU leaves out "den". Both are correct.
      expect(formatLongDate('2026-11-18')).toMatch(/^onsdag (den )?18\. november$/);
    });

    it('uses Danish units', () => {
      expect(formatDuration(105)).toBe('1 t 45 min');
      expect(formatDuration(120)).toBe('2 t');
      expect(formatCo2(2450)).toBe('2,5 kg');
    });
  });

  describe('in English', () => {
    beforeEach(() => startLanguage('en'));

    it('writes numbers and money the English way', () => {
      expect(formatNumber(1234.5, 2)).toBe('1,234.50');
      expect(plain(formatDkk(1234.5))).toBe('DKK 1,234.50');
    });

    it('writes times and dates the English way', () => {
      expect(formatTime('2026-07-01T10:15:00Z')).toBe('12:15');
      expect(formatLongDate('2026-11-18')).toBe('Wednesday 18 November');
    });

    it('uses English units', () => {
      expect(formatDuration(45)).toBe('45 min');
      expect(formatDuration(105)).toBe('1 h 45 min');
      expect(formatCo2(412.4)).toBe('412 g');
      expect(formatCo2(null)).toBe('–');
    });
  });

  it('works out the Danish date near midnight', () => {
    expect(danishDate(new Date('2026-11-18T23:30:00Z'))).toBe('2026-11-19');
    expect(danishDate(new Date('2026-11-18T22:30:00Z'))).toBe('2026-11-18');
  });

  it('builds datetime-local values without seconds', () => {
    expect(toLocalInputValue(new Date(2026, 0, 5, 9, 7))).toBe('2026-01-05T09:07');
  });

  describe('defaultDeadline', () => {
    it('uses tomorrow morning in the evening', () => {
      expect(defaultDeadline(new Date(2026, 10, 18, 20, 0))).toEqual(new Date(2026, 10, 19, 7, 0));
    });

    it('uses this morning when it is still early enough', () => {
      expect(defaultDeadline(new Date(2026, 10, 18, 1, 30))).toEqual(new Date(2026, 10, 18, 7, 0));
    });

    it('skips to the next day when 07:00 is too close', () => {
      expect(defaultDeadline(new Date(2026, 10, 18, 5, 0))).toEqual(new Date(2026, 10, 19, 7, 0));
    });
  });
});
