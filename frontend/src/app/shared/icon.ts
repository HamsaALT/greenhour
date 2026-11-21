import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Simple 24 px stroke icons. Paths only, so no icon font or library is needed. */
const PATHS = {
  check: 'M5 12.5l4.5 4.5L19 7.5',
  x: 'M6 6l12 12M18 6L6 18',
  alert: 'M12 3l9.5 17h-19L12 3zM12 10v4.5M12 17.5v.01',
  info: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 11v5.5M12 7.8v.01',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  bolt: 'M13 2.5L5 13.5h6l-1 8 8-11h-6l1-8z',
  leaf: 'M5 19c0-8 5-14 15-14 0 10-6 15-14 15M5 19c3-4 6-6 9-7.5',
  menu: 'M4 7h16M4 12h16M4 17h16',
  chevron: 'M6 9.5l6 6 6-6',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4.5 20.5c1-3.8 4-5.5 7.5-5.5s6.5 1.7 7.5 5.5',
  logout: 'M15 4h3.5A1.5 1.5 0 0120 5.5v13a1.5 1.5 0 01-1.5 1.5H15M10 16.5L5.5 12 10 7.5M5.5 12H15',
  calendar: 'M4.5 6.5h15v13h-15zM4.5 10.5h15M8.5 4v4M15.5 4v4',
  plug: 'M9 3v5M15 3v5M6.5 8h11v3a5.5 5.5 0 01-11 0V8zM12 16.5V21',
  home: 'M4 11l8-7 8 7M6 9.5V20h12V9.5',
  trend: 'M4 17l5-5 4 3 7-8M15 7h5v5',
  refresh: 'M19.5 12a7.5 7.5 0 11-2.2-5.3M19.5 4v4.5H15',
  download: 'M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14',
} as const;

export type IconName = keyof typeof PATHS;

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icon', 'aria-hidden': 'true' },
  template: `
    <svg
      viewBox="0 0 24 24"
      width="100%"
      height="100%"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      line-height: 0;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly path = computed(() => PATHS[this.name()]);
}
