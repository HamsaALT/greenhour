import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, debounceTime, filter, map, of, startWith, switchMap } from 'rxjs';
import { Api, errorMessage } from '../../core/api';
import { Goal, PlanPreview, PlanRequest } from '../../core/models';
import {
  defaultDeadline,
  formatCo2,
  formatDayTime,
  formatDkk,
  formatDuration,
  formatNumber,
  formatTime,
  toLocalInputValue,
} from '../../core/format';
import { t } from '../../core/language';
import { OptionsChart } from './options-chart';
import { Icon } from '../../shared/icon';
import { SourceEntry, SourceList } from '../../shared/source-list';

/** How far ahead prices can be known: today plus tomorrow once published. */
const MAX_HOURS_AHEAD = 48;

interface PreviewState {
  loading: boolean;
  preview?: PlanPreview;
  error?: unknown;
}

@Component({
  selector: 'app-plan-page',
  imports: [Icon, OptionsChart, ReactiveFormsModule, RouterLink, SourceList, TranslatePipe],
  templateUrl: './plan-page.html',
  styleUrl: './plan-page.css',
})
export class PlanPage {
  protected readonly sources: SourceEntry[] = [
    {
      title: 'sources.usage.title',
      publisher: 'sources.yourNumbers',
      text: 'sources.usage.text',
    },
    {
      title: 'sources.planData.title',
      publisher: 'sources.energinetNordPool',
      text: 'sources.planData.text',
    },
    {
      title: 'sources.bestStart.title',
      publisher: 'sources.calculatedByUs',
      text: 'sources.bestStart.text',
      own: true,
    },
  ];

  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Pre-selects an appliance when coming from the "My home" page (?appliance=3). */
  readonly appliance = input<string>();

  protected readonly appliances = toSignal(this.api.appliances(), { initialValue: null });

  protected readonly form = this.fb.group({
    applianceId: this.fb.control<number | null>(null, Validators.required),
    earliestStart: this.fb.control(toLocalInputValue(new Date()), Validators.required),
    deadline: this.fb.control(toLocalInputValue(defaultDeadline(new Date())), Validators.required),
    goal: this.fb.control<Goal>('PRICE'),
  });

  protected readonly goals: Goal[] = ['PRICE', 'CO2', 'BALANCED'];

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });
  protected readonly submitted = signal(false);

  /** Problems with the time window, as a translation key and its parameters. */
  protected readonly windowError = computed(
    (): { key: string; params?: Record<string, string> } | null => {
      this.formValue();
      const v = this.form.getRawValue();
      const earliest = new Date(v.earliestStart).getTime();
      const deadline = new Date(v.deadline).getTime();
      if (isNaN(earliest) || isNaN(deadline)) return null;
      if (deadline <= earliest) return { key: 'plan.errors.order' };
      const appliance = this.selectedAppliance();
      if (appliance && deadline - earliest < appliance.runMinutes * 60_000) {
        return {
          key: 'plan.errors.tooShort',
          params: { duration: formatDuration(appliance.runMinutes) },
        };
      }
      if (deadline - Date.now() > MAX_HOURS_AHEAD * 3_600_000) return { key: 'plan.errors.tooFar' };
      return null;
    },
  );

  protected readonly earliestMissing = computed(() => {
    this.formValue();
    return this.form.controls.earliestStart.invalid;
  });
  protected readonly deadlineMissing = computed(() => {
    this.formValue();
    return this.form.controls.deadline.invalid;
  });

  // Re-plan as the user edits, but wait for them to stop typing first.
  private readonly previewState = toSignal(
    this.form.valueChanges.pipe(
      debounceTime(300),
      map(() => this.request()),
      filter((r): r is PlanRequest => r !== null),
      switchMap((request) =>
        this.api.previewRun(request).pipe(
          map((preview): PreviewState => ({ loading: false, preview })),
          catchError((error) => of<PreviewState>({ loading: false, error })),
          startWith<PreviewState>({ loading: true }),
        ),
      ),
    ),
    { initialValue: { loading: false } as PreviewState },
  );

  protected readonly preview = computed(() => this.previewState().preview);
  protected readonly previewError = computed(() => {
    const error = this.previewState().error;
    return error ? errorMessage(error) : null;
  });
  protected readonly previewLoading = computed(() => this.previewState().loading);

  private readonly selectedId = signal<number | null>(null);
  private readonly goal = toSignal(this.form.controls.goal.valueChanges, {
    initialValue: this.form.controls.goal.value,
  });

  protected readonly selectedAppliance = computed(() => {
    const id = this.selectedId();
    return this.appliances()?.find((a) => a.id === id);
  });

  /** Where the chosen run sits inside the user's window, for the timeline bar. */
  protected readonly timeline = computed(() => {
    const p = this.preview();
    if (!p) return null;
    this.formValue();
    const from = Date.parse(p.baselineStart);
    const to = Math.max(new Date(this.form.controls.deadline.value).getTime(), Date.parse(p.end));
    const span = to - from;
    if (!(span > 0)) return null;
    const pct = (time: number) => Math.min(100, Math.max(0, ((time - from) / span) * 100));
    const left = pct(Date.parse(p.start));
    return {
      from: new Date(from).toISOString(),
      to: new Date(to).toISOString(),
      left,
      width: Math.max(2, pct(Date.parse(p.end)) - left),
    };
  });

  protected readonly chartMetric = computed(() => (this.goal() === 'CO2' ? 'co2' : 'cost'));

  protected readonly savingPercent = computed(() => {
    const p = this.preview();
    if (!p || p.baselineCostDkk <= 0) return null;
    return Math.round((p.savedDkk / p.baselineCostDkk) * 100);
  });

  protected readonly savingSentence = computed(() => {
    const p = this.preview();
    if (!p) return '';
    const savesMoney = p.savedDkk > 0.005;
    const savesCo2 = p.savedCo2Grams !== null && p.savedCo2Grams >= 1;
    const pct = this.savingPercent();
    const params = {
      money: formatDkk(p.savedDkk),
      percent: pct !== null ? t('plan.percent', { value: pct }) : '',
      co2: formatCo2(p.savedCo2Grams),
      extra: formatDkk(-p.savedDkk),
    };

    if (savesMoney && savesCo2) return t('plan.savedBoth', params);
    if (savesMoney) return t('plan.savedMoney', params);
    if (savesCo2) {
      // Optimising for CO2 can mean paying a little more. Say so rather than hide it.
      return -p.savedDkk > 0.005 ? t('plan.savedCo2Costs', params) : t('plan.savedCo2', params);
    }
    return t('plan.alreadyBest');
  });

  /**
   * CO2 forecasts usually stop at midnight, so the backend can only consider part
   * of a long window. Returns when the usable part ends, if it ends early.
   */
  protected readonly co2CutOff = computed(() => {
    const p = this.preview();
    const appliance = this.selectedAppliance();
    if (!p || !appliance || this.goal() === 'PRICE' || p.options.length === 0) return null;
    const lastStart = Date.parse(p.options[p.options.length - 1].start);
    const lastEnd = lastStart + appliance.runMinutes * 60_000;
    const deadline = new Date(this.form.controls.deadline.value).getTime();
    return deadline - lastEnd >= 15 * 60_000 ? new Date(lastEnd).toISOString() : null;
  });

  protected readonly saving = signal(false);
  protected readonly saveError = signal<unknown>(null);
  protected readonly errorMessage = errorMessage;

  protected readonly formatDayTime = formatDayTime;
  protected readonly formatTime = formatTime;
  protected readonly formatDkk = formatDkk;
  protected readonly formatCo2 = formatCo2;
  protected readonly formatDuration = formatDuration;
  protected readonly formatNumber = formatNumber;

  constructor() {
    this.form.controls.applianceId.valueChanges.subscribe((id) => this.selectedId.set(id));

    // Pick an appliance as soon as the list arrives: the one from the URL, or the first.
    effect(() => {
      const list = this.appliances();
      if (!list?.length || this.form.controls.applianceId.value !== null) return;
      const wanted = Number(this.appliance());
      const initial = list.find((a) => a.id === wanted) ?? list[0];
      this.form.controls.applianceId.setValue(initial.id);
    });
  }

  protected schedule(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const request = this.request();
    if (!request) return;
    this.saving.set(true);
    this.saveError.set(null);
    this.api.scheduleRun(request).subscribe({
      next: () => this.router.navigate(['/history'], { queryParams: { planned: 1 } }),
      error: (e) => {
        this.saveError.set(e);
        this.saving.set(false);
      },
    });
  }

  private request(): PlanRequest | null {
    const v = this.form.getRawValue();
    if (this.form.invalid || v.applianceId === null || this.windowError()) return null;
    const earliest = new Date(v.earliestStart);
    const deadline = new Date(v.deadline);
    if (isNaN(earliest.getTime()) || isNaN(deadline.getTime())) return null;
    return {
      applianceId: v.applianceId,
      earliestStart: earliest.toISOString(),
      deadline: deadline.toISOString(),
      goal: v.goal,
    };
  }
}
