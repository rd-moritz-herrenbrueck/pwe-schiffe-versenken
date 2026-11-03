import { TestBed } from '@angular/core/testing';
import { DARK_MODE_CLASS, ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  function systemPrefersDark(prefersDark: boolean): void {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('prefers-color-scheme: dark') && prefersDark,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  }

  function create(): ThemeService {
    TestBed.resetTestingModule();
    return TestBed.inject(ThemeService);
  }

  beforeEach(() => {
    localStorage.clear();
    root.classList.remove(DARK_MODE_CLASS);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
    root.classList.remove(DARK_MODE_CLASS);
  });

  it('usesLightModeWhenNothingIsKnown', () => {
    const theme = create();

    expect(theme.mode()).toBe('light');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(false);
  });

  it('followsDarkSystemPreferenceOnFirstVisit', () => {
    systemPrefersDark(true);

    const theme = create();

    expect(theme.mode()).toBe('dark');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(true);
  });

  it('followsLightSystemPreferenceOnFirstVisit', () => {
    systemPrefersDark(false);

    expect(create().mode()).toBe('light');
  });

  it('prefersSavedChoiceOverSystemPreference', () => {
    systemPrefersDark(true);
    create().set('light');

    const afterReload = create();

    expect(afterReload.mode()).toBe('light');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(false);
  });

  it('switchesToDarkModeAndMarksRootElement', () => {
    const theme = create();

    theme.set('dark');

    expect(theme.mode()).toBe('dark');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(true);
  });

  it('switchesBackToLightModeAndRemovesMarker', () => {
    const theme = create();
    theme.set('dark');

    theme.set('light');

    expect(theme.mode()).toBe('light');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(false);
  });

  it('togglesBetweenLightAndDark', () => {
    const theme = create();

    theme.toggle();
    expect(theme.mode()).toBe('dark');
    theme.toggle();
    expect(theme.mode()).toBe('light');
  });

  it('keepsDarkModeAfterReload', () => {
    create().toggle();

    const afterReload = create();

    expect(afterReload.mode()).toBe('dark');
    expect(root.classList.contains(DARK_MODE_CLASS)).toBe(true);
  });
});
