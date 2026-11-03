import { TestBed } from '@angular/core/testing';
import { DARK_MODE_CLASS, ThemeService } from '../../core/theme.service';
import { ThemeToggle } from './theme-toggle';

describe('ThemeToggle', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove(DARK_MODE_CLASS);
    TestBed.configureTestingModule({ imports: [ThemeToggle] });
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove(DARK_MODE_CLASS);
  });

  async function render(): Promise<{ button: HTMLElement; detect: () => Promise<void> }> {
    const fixture = TestBed.createComponent(ThemeToggle);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '[data-testid="theme-toggle"]',
    );
    expect(button).not.toBeNull();
    return { button: button!, detect: () => fixture.whenStable() };
  }

  it('showsCurrentMode', async () => {
    const { button } = await render();

    expect(button.getAttribute('data-mode')).toBe('light');
  });

  it('switchesModeOnClick', async () => {
    const { button, detect } = await render();

    button.click();
    await detect();

    expect(TestBed.inject(ThemeService).mode()).toBe('dark');
    expect(button.getAttribute('data-mode')).toBe('dark');
  });

  it('hasAccessibleLabelThatNamesTheAction', async () => {
    const { button, detect } = await render();
    expect(button.getAttribute('aria-label')).toBe('Dunkelmodus aktivieren');

    button.click();
    await detect();

    expect(button.getAttribute('aria-label')).toBe('Hellmodus aktivieren');
  });
});
