import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HistoryPage } from './history-page';
import { Run } from '../../core/models';
import { provideTestI18n, startLanguage } from '../../testing/i18n';

function run(overrides: Partial<Run>): Run {
  return {
    id: 1,
    applianceId: 1,
    applianceName: 'Dishwasher',
    applianceKind: 'DISHWASHER',
    goal: 'PRICE',
    status: 'DONE',
    earliestStart: '2026-11-17T16:00:00Z',
    deadline: '2026-11-18T06:00:00Z',
    plannedStart: '2026-11-18T01:00:00Z',
    plannedEnd: '2026-11-18T03:00:00Z',
    energyKwh: 2.4,
    costDkk: 1.2,
    co2Grams: 150,
    savedDkk: 0.8,
    savedCo2Grams: 90,
    ...overrides,
  };
}

describe('HistoryPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        ...provideTestI18n(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  async function render(runs: Run[], lang: 'da' | 'en' = 'da') {
    startLanguage(lang);
    const fixture = TestBed.createComponent(HistoryPage);
    fixture.detectChanges();
    backend.expectOne('/api/runs').flush(runs);
    backend
      .expectOne('/api/runs/summary')
      .flush({ completedRuns: 1, savedDkk: 12.5, savedCo2Grams: 3400 });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  // Intl uses non-breaking spaces; compare with ordinary ones.
  const text = (page: HTMLElement) => page.textContent?.replace(/\s+/g, ' ') ?? '';

  it('shows totals from the backend in Danish by default', async () => {
    const page = await render([run({})]);

    expect(text(page)).toContain('Gennemførte kørsler');
    expect(text(page)).toContain('12,50 kr.');
    expect(text(page)).toContain('3,4 kg');
  });

  it('shows the same totals in English', async () => {
    const page = await render([run({})], 'en');

    expect(text(page)).toContain('Completed runs');
    expect(text(page)).toContain('DKK 12.50');
    expect(text(page)).toContain('3.4 kg');
  });

  it('splits planned runs from past ones', async () => {
    const page = await render([
      run({ id: 1, applianceName: 'Old wash', status: 'DONE' }),
      run({ id: 2, applianceName: 'Tonight', status: 'PLANNED' }),
    ]);

    const [upcoming, past] = Array.from(page.querySelectorAll('section.panel'));
    expect(upcoming.textContent).toContain('Tonight');
    expect(upcoming.textContent).not.toContain('Old wash');
    expect(past.textContent).toContain('Old wash');
  });

  it('marks a run as done', async () => {
    const page = await render([run({ id: 7, status: 'PLANNED' })]);

    page.querySelector<HTMLButtonElement>('button.btn-link')!.click();

    const req = backend.expectOne('/api/runs/7');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'DONE' });
  });

  it('asks before cancelling a planned run', async () => {
    const page = await render([run({ id: 9, applianceName: 'Tonight', status: 'PLANNED' })]);

    page.querySelector<HTMLButtonElement>('button.btn-link.danger')!.click();
    await Promise.resolve();
    TestBed.tick();
    backend.expectNone('/api/runs/9');
    expect(text(page)).toContain('Annullér Tonight?');

    page.querySelector<HTMLButtonElement>('button.btn-danger')!.click();
    const req = backend.expectOne('/api/runs/9');
    expect(req.request.body).toEqual({ status: 'CANCELLED' });
  });
});
