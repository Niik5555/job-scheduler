import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { DashboardComponent } from './app/app.component';

bootstrapApplication(DashboardComponent, appConfig)
  .catch((err) => console.error(err));
