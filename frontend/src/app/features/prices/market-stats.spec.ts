import { MarketSlot } from '../../core/models';
import { averagePrice, cheapestWindow, currentSlot, mostExpensive } from './market-stats';

function day(prices: number[]): MarketSlot[] {
  const start = Date.parse('2026-11-18T23:00:00Z');
  return prices.map((price, i) => ({
    start: new Date(start + i * 15 * 60 * 1000).toISOString(),
    priceDkkPerKwh: price,
    co2GramsPerKwh: 100,
    co2Forecast: false,
    demoData: false,
  }));
}

describe('market stats', () => {
  it('finds the cheapest consecutive window', () => {
    const slots = day([1.0, 0.9, 0.3, 0.2, 0.4, 1.2]);

    const w = cheapestWindow(slots, 2);

    expect(w?.start).toBe(slots[2].start);
    expect(w?.end).toBe(slots[4].start);
    expect(w?.averagePrice).toBeCloseTo(0.25);
  });

  it('returns null when there are fewer slots than the window', () => {
    expect(cheapestWindow(day([0.5]), 8)).toBeNull();
  });

  it('finds the slot that is running now', () => {
    const slots = day([1, 2, 3]);
    const now = new Date(Date.parse(slots[1].start) + 5 * 60 * 1000);

    expect(currentSlot(slots, now)).toBe(slots[1]);
    expect(currentSlot(slots, new Date('2030-01-01'))).toBeUndefined();
  });

  it('finds the peak and the average', () => {
    const slots = day([0.5, 1.5, 1.0]);

    expect(mostExpensive(slots)).toBe(slots[1]);
    expect(averagePrice(slots)).toBeCloseTo(1.0);
    expect(averagePrice([])).toBeNull();
  });
});
