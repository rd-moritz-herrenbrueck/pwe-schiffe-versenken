import { Component, computed, inject } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'app-theme-toggle',
  imports: [ButtonDirective],
  template: `
    <button
      pButton
      type="button"
      severity="secondary"
      [text]="true"
      [rounded]="true"
      data-testid="theme-toggle"
      [attr.data-mode]="theme.mode()"
      [attr.aria-label]="label()"
      [title]="label()"
      (click)="theme.toggle()"
    >
      <i [class]="icon()" aria-hidden="true"></i>
    </button>
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
  protected readonly label = computed(() =>
    this.theme.mode() === 'dark' ? 'Hellmodus aktivieren' : 'Dunkelmodus aktivieren',
  );
  protected readonly icon = computed(() =>
    this.theme.mode() === 'dark' ? 'pi pi-sun' : 'pi pi-moon',
  );
}
