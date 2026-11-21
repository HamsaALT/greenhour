import { MarketSlot } from '../../core/models';

const SLOT_MS = 15 * 60 * 1000;

export interface PriceWindow {
  start: string;
  end: string;
  averagePrice: number;
}

/** The slot that contains `now`, if the day being shown includes it. */
export function currentSlot(slots: MarketSlot[], now: Date): MarketSlot | undefined {
  const t = now.getTime();
  return slots.find((s) => {
    const start = Date.parse(s.start);
    return start <= t && t < start + SLOT_MS;
  });
}

/**
 * Cheapest run of consecutive slots, e.g. the cheapest two hours of the day.
 * Uses a sliding sum so it stays linear even for long windows.
 */
export function cheapestWindow(slots: MarketSlot[], slotCount: number): PriceWindow | null {
  if (slots.length < slotCount || slotCount <= 0) return null;

  let sum = 0;
  for (let i = 0; i < slotCount; i++) sum += slots[i].priceDkkPerKwh;
  let bestSum = sum;
  let bestIndex = 0;

  for (let i = slotCount; i < slots.length; i++) {
    sum += slots[i].priceDkkPerKwh - slots[i - slotCount].priceDkkPerKwh;
    if (sum < bestSum) {
      bestSum = sum;
      bestIndex = i - slotCount + 1;
    }
  }

  const first = slots[bestIndex];
  return {
    start: first.start,
    end: new Date(Date.parse(first.start) + slotCount * SLOT_MS).toISOString(),
    averagePrice: bestSum / slotCount,
  };
}

export function mostExpensive(slots: MarketSlot[]): MarketSlot | undefined {
  return slots.reduce<MarketSlot | undefined>(
    (max, s) => (!max || s.priceDkkPerKwh > max.priceDkkPerKwh ? s : max),
    undefined,
  );
}

export function averagePrice(slots: MarketSlot[]): number | null {
  if (slots.length === 0) return null;
  return slots.reduce((sum, s) => sum + s.priceDkkPerKwh, 0) / slots.length;
}
