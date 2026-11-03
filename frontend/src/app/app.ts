import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeToggle } from './components/theme-toggle/theme-toggle';
import { PlayerService } from './core/player.service';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ThemeToggle],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly player = inject(PlayerService);
}
