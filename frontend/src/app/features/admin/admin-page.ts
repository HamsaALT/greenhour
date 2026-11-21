import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { Api, errorMessage } from '../../core/api';
import { ImportStatus } from '../../core/models';
import { formatDayTime, formatNumber } from '../../core/format';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-admin-page',
  imports: [FormsModule, Icon, TranslatePipe],
  template: `
    <div class="page-head">
      <div>
        <h1>{{ 'admin.title' | translate }}</h1>
        <p>{{ 'admin.intro' | translate }}</p>
      </div>
    </div>

    @if (error()) {
      <div class="notice error" role="alert">
        <app-icon name="alert" /><span>{{ errorMessage(error()) }}</span>
      </div>
    }

    <section class="panel" aria-labelledby="last-title">
      <div class="section-head">
        <h2 id="last-title">{{ 'admin.lastImport' | translate }}</h2>
        @if (status(); as s) {
          @if (s.error) {
            <span class="badge bad"
              ><app-icon name="x" />{{ 'admin.statusFailed' | translate }}</span
            >
          } @else {
            <span class="badge ok"
              ><app-icon name="check" />{{ 'admin.statusOk' | translate }}</span
            >
          }
        }
      </div>
      @if (status(); as s) {
        <table class="details">
          <tbody>
            <tr>
              <th scope="row">{{ 'admin.ranAt' | translate }}</th>
              <td class="num">{{ formatDayTime(s.ranAt) }}</td>
            </tr>
            @if (s.result; as r) {
              <tr>
                <th scope="row">{{ 'admin.range' | translate }}</th>
                <td class="num">{{ formatDayTime(r.from) }} – {{ formatDayTime(r.to) }}</td>
              </tr>
              <tr>
                <th scope="row">{{ 'admin.slotsStored' | translate }}</th>
                <td class="num">
                  {{
                    'admin.slotsValue'
                      | translate
                        : {
                            stored: formatNumber(r.slotsStored, 0),
                            withCo2: formatNumber(r.slotsWithCo2, 0),
                          }
                  }}
                </td>
              </tr>
            }
            @if (s.error) {
              <tr>
                <th scope="row">{{ 'admin.error' | translate }}</th>
                <td class="failed">{{ 'admin.importFailed' | translate }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">
          <app-icon name="clock" />
          <p>{{ 'admin.noImport' | translate }}</p>
        </div>
      }
    </section>

    <section class="panel" aria-labelledby="run-title">
      <h2 id="run-title">{{ 'admin.runImport' | translate }}</h2>
      <form class="run" (ngSubmit)="askToConfirm()">
        <div class="field">
          <label for="days">{{ 'admin.daysBack' | translate }}</label>
          <input
            id="days"
            type="number"
            name="days"
            min="0"
            max="31"
            aria-describedby="days-hint"
            [(ngModel)]="daysBack"
            (ngModelChange)="confirming.set(false)"
          />
          <span class="hint" id="days-hint">{{ 'admin.daysBackHint' | translate }}</span>
        </div>
        @if (!confirming()) {
          <button type="submit" class="btn" [disabled]="running()">
            <app-icon name="download" />
            {{ (running() ? 'admin.importing' : 'admin.importNow') | translate }}
          </button>
        }
      </form>

      @if (confirming()) {
        <div class="confirm-box" role="alertdialog" aria-labelledby="confirm-text">
          <p id="confirm-text">
            <app-icon name="alert" />
            {{ 'admin.confirmText' | translate }}
          </p>
          <div class="confirm-actions">
            <button type="button" class="btn" (click)="runImport()">
              {{ 'admin.confirmYes' | translate }}
            </button>
            <button type="button" class="btn btn-secondary" (click)="confirming.set(false)">
              {{ 'admin.confirmNo' | translate }}
            </button>
          </div>
        </div>
      }
    </section>
  `,
  styles: `
    .details th {
      width: 200px;
      text-transform: none;
      letter-spacing: 0;
      font-size: var(--text-sm);
      border-bottom: 1px solid var(--line);
      padding: var(--space-3);
    }
    .details tr:last-child th {
      border-bottom: none;
    }
    .failed {
      color: var(--danger);
      font-weight: 500;
    }
    .run {
      display: flex;
      align-items: flex-end;
      flex-wrap: wrap;
      gap: var(--space-3);
    }
    .run .field {
      width: 220px;
    }
    .run .btn {
      min-height: var(--touch);
      margin-bottom: 26px;
    }
    .confirm-box {
      margin-top: var(--space-4);
      padding: var(--space-4);
      border: 1px solid #e9cf9f;
      border-radius: var(--radius-md);
      background: var(--warn-bg);
    }
    .confirm-box p {
      display: flex;
      gap: var(--space-2);
      margin: 0 0 var(--space-3);
      color: var(--warn-ink);
      font-weight: 500;
    }
    .confirm-actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
    .confirm-actions .btn {
      min-height: var(--touch);
    }
  `,
})
export class AdminPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly status = signal<ImportStatus | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly running = signal(false);
  /** The import rewrites stored prices, so it needs a second click. */
  protected readonly confirming = signal(false);
  protected daysBack = 1;

  protected readonly formatDayTime = formatDayTime;
  protected readonly formatNumber = formatNumber;
  protected readonly errorMessage = errorMessage;

  ngOnInit(): void {
    this.refreshStatus();
  }

  protected askToConfirm(): void {
    if (this.running()) return;
    this.confirming.set(true);
    // Move focus to the confirm button so keyboard users land on the question.
    setTimeout(() => document.querySelector<HTMLButtonElement>('.confirm-actions .btn')?.focus());
  }

  protected runImport(): void {
    this.confirming.set(false);
    this.running.set(true);
    this.error.set(null);
    this.api.runImport(this.daysBack).subscribe({
      next: () => {
        this.running.set(false);
        this.refreshStatus();
      },
      error: (e) => {
        this.running.set(false);
        this.error.set(e);
        this.refreshStatus();
      },
    });
  }

  private refreshStatus(): void {
    this.api.lastImport().subscribe({
      next: (s) => this.status.set(s),
      // 404 just means nothing has run yet.
      error: (e) => {
        if (!(e instanceof HttpErrorResponse && e.status === 404)) this.error.set(e);
      },
    });
  }
}
