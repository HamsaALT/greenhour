import { Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export interface SourceEntry {
  /** Translation key for what the number is, e.g. "Elpris". */
  title: string;
  /** Translation key for who publishes it. */
  publisher: string;
  /** Translation key for how the number is used. */
  text: string;
  /** Link to the data set, when there is a public one. */
  url?: string;
  /** True for our own estimates and calculations, so they never look official. */
  own?: boolean;
}

/**
 * "Where do these numbers come from?" Every figure gets the organisation behind
 * it, and anything GreenHour works out or guesses itself is marked as such.
 */
@Component({
  selector: 'app-source-list',
  imports: [TranslatePipe],
  template: `
    <section class="panel sources" [attr.aria-labelledby]="headingId()">
      <h2 [id]="headingId()">{{ heading() | translate }}</h2>
      <ul>
        @for (s of entries(); track s.title) {
          <li>
            <div class="head">
              <h3>{{ s.title | translate }}</h3>
              <span class="publisher" [class.own]="s.own">{{ s.publisher | translate }}</span>
            </div>
            <p>
              {{ s.text | translate }}
              @if (s.url) {
                <a [href]="s.url" rel="noopener" target="_blank"
                  >{{ 'sources.seeData' | translate
                  }}<span class="sr-only"> {{ 'sources.newTab' | translate }}</span></a
                >
              }
            </p>
          </li>
        }
      </ul>
      @if (footnote()) {
        <p class="footnote">{{ footnote()! | translate }}</p>
      }
    </section>
  `,
  styles: `
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      padding: 14px 0;
      border-top: 1px solid var(--line);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 6px 12px;
    }
    h3 {
      margin: 0;
      font-size: 15px;
    }
    .publisher {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--accent);
      background: var(--accent-soft);
      border-radius: 999px;
      padding: 2px 10px;
    }
    .publisher.own {
      color: var(--warn-ink);
      background: var(--warn-bg);
    }
    p {
      margin: 6px 0 0;
      color: var(--muted);
      max-width: 70ch;
    }
    .footnote {
      border-top: 1px solid var(--line);
      padding-top: 14px;
      font-size: 12.5px;
    }
  `,
})
export class SourceList {
  readonly heading = input.required<string>();
  readonly headingId = input('sources-title');
  readonly entries = input.required<SourceEntry[]>();
  readonly footnote = input<string>();
}
