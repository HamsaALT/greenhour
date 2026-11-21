import { Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import { PlanOption } from '../../core/models';
import { TranslatePipe } from '@ngx-translate/core';
import { formatDayTime, formatDkk, formatNumber, formatTime } from '../../core/format';
import { t } from '../../core/language';
import { Chart, chartColors, tickLimit } from '../../shared/charts';

/** Cost of the run for every possible start time, with the chosen one marked. */
@Component({
  selector: 'app-options-chart',
  imports: [TranslatePipe],
  template: `
    <div class="chart-box">
      <canvas #canvas role="img" [attr.aria-label]="'chart.optionsLabel' | translate"></canvas>
    </div>
  `,
  styles: `
    .chart-box {
      position: relative;
      height: 200px;
    }
  `,
})
export class OptionsChart implements OnDestroy {
  readonly options = input.required<PlanOption[]>();
  readonly chosenStart = input.required<string>();
  readonly metric = input<'cost' | 'co2'>('cost');

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => this.render(this.options(), this.chosenStart(), this.metric()));
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(options: PlanOption[], chosen: string, metric: 'cost' | 'co2'): void {
    const chosenIndex = options.findIndex((o) => o.start === chosen);
    const pointRadius = options.map((_, i) => (i === chosenIndex ? 5 : 0));
    const isCo2 = metric === 'co2';
    const values = options.map((o) => (isCo2 ? o.co2Grams : o.costDkk));
    const color = isCo2 ? chartColors.co2 : chartColors.price;
    const unit = (v: unknown) => (isCo2 ? `${formatNumber(Number(v), 0)} g` : formatDkk(Number(v)));

    this.chart?.destroy();
    this.chart = new Chart(this.canvas().nativeElement, {
      type: 'line',
      data: {
        labels: options.map((o) => formatTime(o.start)),
        datasets: [
          {
            data: values,
            borderColor: color,
            backgroundColor: isCo2 ? 'rgba(138, 109, 59, 0.08)' : 'rgba(47, 111, 143, 0.08)',
            fill: true,
            borderWidth: 1.5,
            tension: 0.2,
            pointRadius,
            pointBackgroundColor: chartColors.accent,
            pointBorderColor: chartColors.accent,
          },
        ],
      },
      options: {
        animation: false,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            grid: { display: false },
            ticks: { maxTicksLimit: tickLimit(window.innerWidth), maxRotation: 0 },
          },
          y: {
            grid: { color: chartColors.grid },
            ticks: { callback: unit },
          },
        },
        plugins: {
          tooltip: {
            callbacks: {
              title: (items) =>
                t('chart.start', { time: formatDayTime(options[items[0].dataIndex].start) }),
              label: (ctx) => ` ${unit(ctx.raw)}`,
            },
          },
        },
      },
    });
  }
}
