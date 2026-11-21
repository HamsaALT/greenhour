import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { Api, errorMessage } from '../../core/api';
import { Run, RunStatus, RunSummary } from '../../core/models';
import { Icon } from '../../shared/icon';
import { formatCo2, formatDayTime, formatDkk, formatNumber, formatTime } from '../../core/format';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-history-page',
  imports: [Icon, RouterLink, TranslatePipe],
  templateUrl: './history-page.html',
  styleUrl: './history-page.css',
})
export class HistoryPage implements OnInit {
  private readonly api = inject(Api);

  /** Set by the plan page after scheduling, to show a confirmation. */
  readonly planned = input<string>();

  protected readonly runs = signal<Run[] | null>(null);
  protected readonly summary = signal<RunSummary | null>(null);
  protected readonly error = signal<unknown>(null);
  /** The planned run waiting for the user to confirm the cancellation. */
  protected readonly confirmCancelId = signal<number | null>(null);
  protected readonly errorMessage = errorMessage;

  protected readonly upcoming = computed(() =>
    (this.runs() ?? [])
      .filter((r) => r.status === 'PLANNED')
      .sort((a, b) => a.plannedStart.localeCompare(b.plannedStart)),
  );
  protected readonly past = computed(() =>
    (this.runs() ?? []).filter((r) => r.status !== 'PLANNED'),
  );
  /** Long histories are shown in pages so the list stays short on phones. */
  protected readonly pastLimit = signal(PAGE_SIZE);
  protected readonly visiblePast = computed(() => this.past().slice(0, this.pastLimit()));

  protected showMore(): void {
    this.pastLimit.update((n) => n + PAGE_SIZE);
  }

  protected readonly formatDayTime = formatDayTime;
  protected readonly formatTime = formatTime;
  protected readonly formatDkk = formatDkk;
  protected readonly formatCo2 = formatCo2;
  protected readonly formatNumber = formatNumber;

  ngOnInit(): void {
    this.load();
  }

  protected mark(run: Run, status: RunStatus): void {
    this.confirmCancelId.set(null);
    this.api.setRunStatus(run.id, status).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(e),
    });
  }

  private load(): void {
    forkJoin({ runs: this.api.runs(), summary: this.api.runSummary() }).subscribe({
      next: ({ runs, summary }) => {
        this.runs.set(runs);
        this.summary.set(summary);
      },
      error: (e) => this.error.set(e),
    });
  }
}
