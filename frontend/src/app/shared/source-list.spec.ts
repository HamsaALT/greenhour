import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n, startLanguage } from '../testing/i18n';
import { SourceEntry, SourceList } from './source-list';

@Component({
  imports: [SourceList],
  template: `<app-source-list
    heading="sources.heading"
    [entries]="entries"
    footnote="sources.footnote"
  />`,
})
class Host {
  entries: SourceEntry[] = [
    {
      title: 'sources.price.title',
      publisher: 'sources.energinetNordPool',
      text: 'sources.price.text',
      url: 'https://www.energidataservice.dk/tso-electricity/DayAheadPrices',
    },
    {
      title: 'sources.windows.title',
      publisher: 'sources.calculatedByUs',
      text: 'sources.windows.text',
      own: true,
    },
  ];
}

describe('SourceList', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
  });

  async function render() {
    startLanguage();
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('names who publishes each number and links to the data', async () => {
    const page = await render();
    const items = page.querySelectorAll('li');

    expect(page.querySelector('h2')?.textContent).toBe('Hvor kommer tallene fra?');
    expect(items[0].querySelector('.publisher')?.textContent).toBe('Nord Pool via Energinet');
    const link = items[0].querySelector('a')!;
    expect(link.href).toContain('energidataservice.dk');
    expect(link.textContent).toContain('(åbner i en ny fane)');
    expect(link.rel).toBe('noopener');
  });

  it('marks our own calculations so they never look official', async () => {
    const page = await render();
    const own = page.querySelectorAll('li')[1].querySelector('.publisher')!;

    expect(own.textContent).toBe('Beregnet af GreenHour');
    expect(own.classList).toContain('own');
    expect(page.querySelectorAll('li')[1].querySelector('a')).toBeNull();
  });

  it('ends with a note that the app is not an authority', async () => {
    const page = await render();

    expect(page.querySelector('.footnote')?.textContent).toContain('ikke en myndighed');
  });
});
