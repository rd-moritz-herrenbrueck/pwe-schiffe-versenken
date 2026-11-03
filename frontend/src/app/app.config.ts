import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import Aura from '@primeuix/themes/aura';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { DARK_MODE_CLASS } from './core/theme.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: { darkModeSelector: `.${DARK_MODE_CLASS}` },
      },
      license: 'eyJpZCI6IjIwZDJkYzJiLTE1MTQtNDc5Mi1hMWMzLTlmOGNjZGY2MWYwZCIsInByb2R1Y3QiOiJwcmltZXVpIiwidGllciI6ImNvbW11bml0eSIsInR5cGUiOiJkZXYiLCJpYXQiOjE3OTM3MTczNTAsImV4cCI6MTgyNTI1MzM1MH0.YeyrdeUFZDkvRXrFlhb2PyR3nH8uxVWHUw0fvmLsrd-tYxT1fIs8fwAyB7lL5dZLOUm6J_0Zy8jtokjfqY0IAA'
    }),
  ],
};
