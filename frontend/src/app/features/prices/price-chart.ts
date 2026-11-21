import {
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  input,
  viewChild,
} from '@angular/core';
import { MarketSlot } from '../../core/models';
import { TranslatePipe } from '@ngx-translate/core';
import { formatNumber, formatTime } from '../../core/format';
import { t } from '../../core/language';
import type { Plugin } from 'chart.js';
import { Chart, chartColors, tickLimit } from '../../shared/charts';
import { PriceWindow } from './market-stats';

@Component({
  selector: 'app-price-chart',
  imports: [TranslatePipe],
  template: `
    <div class="chart-box">
      <canvas #canvas role="img" [attr.aria-label]="summary()"></canvas>
    </div>
    <div class="legend" aria-hidden="true">
      <span
        ><i class="swatch price"></i
        >{{ 'chart.price' | translate: { unit: ('units.perKwh' | translate) } }}</span
      >
      <span
        ><i class="swatch cheap"></i>{{ 'chart.cheapestWindow' | translate }}
        <span class="legend-tag">{{ 'chart.cheapestTag' | translate }}</span></span
      >
      <span><i class="swatch co2"></i>{{ 'chart.co2' | translate }}</span>
    </div>
  `,
  styles: `
    .chart-box {
      position: relative;
      height: 320px;
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2) var(--space-5);
      margin-top: var(--space-3);
      font-size: var(--text-sm);
      color: var(--muted);
    }
    .legend-tag {
      margin-left: var(--space-1);
      padding: 0 6px;
      border-radius: 999px;
      background: var(--accent);
      color: #fff;
      font-size: var(--text-xs);
      font-weight: 600;
    }
    @media (max-width: 600px) {
      .chart-box {
        height: 260px;
      }
    }
    .swatch {
      display: inline-block;
      width: 10px;
      height: 10px;
      margin-right: 6px;
      border-radius: 2px;
      vertical-align: -1px;
    }
    .swatch.price {
      background: #5f93aa;
    }
    .swatch.cheap {
      background: var(--accent);
    }
    .swatch.co2 {
      height: 2px;
      vertical-align: 3px;
      background: var(--co2);
    }
  `,
})
export class PriceChart implements OnDestroy {
  readonly slots = input.required<MarketSlot[]>();
  readonly cheapest = input<PriceWindow | null>(null);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;
  private highlight: { first: number; last: number } | null = null;

  /** Draws a bracket and a text label over the cheapest window, so it is not shown by colour only. */
  private readonly cheapestLabel: Plugin = {
    id: 'cheapestLabel',
    afterDatasetsDraw: (chart) => {
      const range = this.highlight;
      if (!range) return;
      const bars = chart.getDatasetMeta(0).data;
      const first = bars[range.first];
      const last = bars[range.last];
      if (!first || !last) return;
      const half = (first as unknown as { width: number }).width / 2;
      const left = first.x - half;
      const right = last.x + half;
      let top = Infinity;
      for (let i = range.first; i <= range.last; i++) top = Math.min(top, bars[i].y);
      const y = Math.max(chart.chartArea.top + 22, top - 10);

      const ctx = chart.ctx;
      ctx.save();
      ctx.strokeStyle = chartColors.accent;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(left, y + 5);
      ctx.lineTo(left, y);
      ctx.lineTo(right, y);
      ctx.lineTo(right, y + 5);
      ctx.stroke();

      const text = t('chart.cheapestTag');
      ctx.font = `600 12px ${Chart.defaults.font.family}`;
      const width = ctx.measureText(text).width + 12;
      const centre = Math.min(
        Math.max((left + right) / 2, chart.chartArea.left + width / 2),
        chart.chartArea.right - width / 2,
      );
      ctx.fillStyle = chartColors.accent;
      ctx.beginPath();
      ctx.roundRect(centre - width / 2, y - 20, width, 18, 9);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, centre, y - 11);
      ctx.restore();
    },
  };

  protected readonly summary = computed(() => {
    const slots = this.slots();
    if (slots.length === 0) return t('chart.noPrices');
    const prices = slots.map((s) => s.priceDkkPerKwh);
    return t('chart.summary', {
      min: formatNumber(Math.min(...prices), 2),
      max: formatNumber(Math.max(...prices), 2),
      unit: t('units.perKwh'),
    });
  });

  constructor() {
    effect(() => this.render(this.slots(), this.cheapest()));
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(slots: MarketSlot[], cheapest: PriceWindow | null): void {
    const inCheapest = (s: MarketSlot) => {
      if (cheapest === null) return false;
      const time = Date.parse(s.start);
      return time >= Date.parse(cheapest.start) && time < Date.parse(cheapest.end);
    };

    const labels = slots.map((s) => formatTime(s.start));
    const prices = slots.map((s) => s.priceDkkPerKwh);
    const co2 = slots.map((s) => s.co2GramsPerKwh);
    const barColors = slots.map((s) =>
      inCheapest(s) ? chartColors.accent : chartColors.priceMuted,
    );
    const inside = slots.map((s, i) => (inCheapest(s) ? i : -1)).filter((i) => i >= 0);
    this.highlight = inside.length ? { first: inside[0], last: inside[inside.length - 1] } : null;

    if (this.chart) {
      this.chart.data.labels = labels;
      this.chart.data.datasets[0].data = prices;
      this.chart.data.datasets[0].backgroundColor = barColors;
      this.chart.data.datasets[1].data = co2;
      this.chart.data.datasets[1].label = t('chart.co2');
      this.chart.data.datasets[0].label = t('chart.price', { unit: t('units.perKwh') });
      const scales = this.chart.options.scales as Record<string, { title?: { text?: string } }>;
      scales['price'].title!.text = t('units.perKwh');
      scales['co2'].title!.text = t('chart.co2Axis');
      this.chart.update();
      return;
    }

    this.chart = new Chart(this.canvas().nativeElement, {
      plugins: [this.cheapestLabel],
      data: {
        labels,
        datasets: [
          {
            type: 'bar',
            label: t('chart.price', { unit: t('units.perKwh') }),
            data: prices,
            backgroundColor: barColors,
            barPercentage: 1,
            categoryPercentage: 0.9,
            yAxisID: 'price',
          },
          {
            type: 'line',
            label: t('chart.co2'),
            data: co2,
            borderColor: chartColors.co2,
            borderWidth: 1.5,
            pointRadius: 0,
            tension: 0.25,
            spanGaps: false,
            yAxisID: 'co2',
          },
        ],
      },
      options: {
        animation: false,
        maintainAspectRatio: false,
        layout: { padding: { top: 8 } },
        onResize: (chart, size) => {
          const x = chart.options.scales?.['x'];
          if (x?.ticks) x.ticks.maxTicksLimit = tickLimit(size.width);
        },
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            grid: { display: false },
            ticks: { maxTicksLimit: tickLimit(window.innerWidth), maxRotation: 0 },
          },
          price: {
            position: 'left',
            beginAtZero: true,
            grid: { color: chartColors.grid },
            title: { display: true, text: t('units.perKwh'), color: chartColors.price },
            ticks: { callback: (v) => formatNumber(Number(v), 2), maxTicksLimit: 6 },
          },
          co2: {
            position: 'right',
            grid: { display: false },
            beginAtZero: true,
            title: { display: true, text: t('chart.co2Axis'), color: chartColors.co2 },
            ticks: { maxTicksLimit: 6 },
          },
        },
        plugins: {
          tooltip: {
            callbacks: {
              label: (ctx) =>
                ctx.dataset.yAxisID === 'price'
                  ? ` ${formatNumber(Number(ctx.raw), 3)} ${t('units.perKwh')}`
                  : ` ${ctx.raw === null ? t('chart.noData') : `${formatNumber(Number(ctx.raw), 0)} g ${t('units.co2PerKwh')}`}`,
            },
          },
        },
      },
    });
  }
}
