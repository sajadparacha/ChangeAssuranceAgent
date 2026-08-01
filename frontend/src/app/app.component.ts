import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<main class="shell"><router-outlet /></main>`,
  styles: [`
    .shell {
      min-height: 100vh;
      background:
        radial-gradient(circle at top left, rgba(20, 90, 120, 0.12), transparent 40%),
        linear-gradient(180deg, #eef3f6 0%, #f7fafc 45%, #e8eef2 100%);
    }
  `]
})
export class AppComponent {}
