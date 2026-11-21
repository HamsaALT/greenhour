import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { Api, errorMessage } from '../../core/api';
import { Auth } from '../../core/auth';
import { MarketDay, PriceArea } from '../../core/models';
import {
  danishDate,
  formatCo2,
  formatLongDate,
  formatNumber,
  formatPricePerKwh,
  formatTime,
} from '../../core/format';
import { averagePrice, cheapestWindow, currentSlot, mostExpensive } from './market-stats';
import { SourceEntry, SourceList } from '../../shared/source-list';
import { PriceChart } from './price-chart';
import { Icon } from '../../shared/icon';

type DayChoice = 'today' | 'tomorrow';

interface LoadState {
  loading: boolean;
  day?: MarketDay;
  error?: unknown;
}

@Component({
  selector: 'app-prices-page',
  imports: [Icon, PriceChart, RouterLink, SourceList, TranslatePipe],
  templateUrl: './prices-page.html',
  styleUrl: './prices-page.css',
})
export class PricesPage {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);

  protected readonly sources: SourceEntry[] = [
    {
      title: 'sources.price.title',
      publisher: 'sources.energinetNordPool',
      text: 'sources.price.text',
      url: 'https://www.energidataservice.dk/tso-electricity/DayAheadPrices',
    },
    {
      title: 'sources.co2.title',
      publisher: 'sources.energinet',
      text: 'sources.co2.text',
      url: 'https://www.energidataservice.dk/tso-electricity/CO2Emis',
    },
    {
      title: 'sources.windows.title',
      publisher: 'sources.calculatedByUs',
      text: 'sources.windows.text',
      own: true,
    },
    {
      title: 'sources.tariff.title',
      publisher: 'sources.gridCompany',
      text: 'sources.tariff.text',
    },
  ];

  protected readonly area = signal<PriceArea>('DK2');
  protected readonly dayChoice = signal<DayChoice>('today');
  /** Bumped by the retry button to fetch the same day again. */
  private readonly attempt = signal(0);

  private readonly date = computed(() => {
    const d = new Date();
    if (this.dayChoice() === 'tomorrow') d.setDate(d.getDate() + 1);
    return danishDate(d);
  });

  // switchMap drops the previous request if the user flips area or day quickly.
  private readonly state = toSignal(
    combineLatest([
      toObservable(this.area),
      toObservable(this.date),
      toObservable(this.attempt),
    ]).pipe(
      switchMap(([area, date]) =>
        this.api.marketDay(area, date).pipe(
          map((day): LoadState => ({ loading: false, day })),
          catchError((error) => of<LoadState>({ loading: false, error })),
          startWith<LoadState>({ loading: true }),
        ),
      ),
    ),
    { initialValue: { loading: true } as LoadState },
  );

  protected readonly loading = computed(() => this.state().loading);
  // Kept as the raw error so the message follows a language switch.
  protected readonly error = computed(() =>
    this.state().error ? errorMessage(this.state().error) : null,
  );
  protected readonly day = computed(() => this.state().day);
  protected readonly slots = computed(() => this.day()?.slots ?? []);

  protected readonly now = computed(() => currentSlot(this.slots(), new Date()));
  protected readonly cheapest = computed(() => cheapestWindow(this.slots(), 8));
  protected readonly cheapestHour = computed(() => cheapestWindow(this.slots(), 4));
  protected readonly peak = computed(() => mostExpensive(this.slots()));
  protected readonly average = computed(() => averagePrice(this.slots()));
  protected readonly isDemo = computed(() => this.slots().some((s) => s.demoData));
  protected readonly hasForecastCo2 = computed(() => this.slots().some((s) => s.co2Forecast));

  protected readonly formatTime = formatTime;
  protected readonly formatPrice = formatPricePerKwh;
  protected readonly formatNumber = formatNumber;
  protected readonly formatCo2 = formatCo2;
  protected readonly longDate = computed(() => formatLongDate(this.date()));

  protected setArea(area: PriceArea): void {
    this.area.set(area);
  }

  protected setDay(choice: DayChoice): void {
    this.dayChoice.set(choice);
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
