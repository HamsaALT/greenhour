import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './auth-interceptor';
import { Auth } from './auth';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | undefined;

  beforeEach(() => {
    token = 'abc123';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Auth, useValue: { token: async () => token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  // The interceptor awaits the token, so give that promise a tick before checking.
  const flushMicrotasks = () => new Promise((resolve) => setTimeout(resolve));

  it('adds the bearer token to API calls', async () => {
    const response = firstValueFrom(http.get('/api/household'));
    await flushMicrotasks();

    const req = backend.expectOne('/api/household');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc123');
    req.flush({});
    await response;
  });

  it('sends API calls without a header when signed out', async () => {
    token = undefined;
    const response = firstValueFrom(http.get('/api/market'));
    await flushMicrotasks();

    const req = backend.expectOne('/api/market');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
    await response;
  });

  it('never sends the token to other hosts', async () => {
    const response = firstValueFrom(http.get('https://example.com/data'));

    const req = backend.expectOne('https://example.com/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
    await response;
  });
});
