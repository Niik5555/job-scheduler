import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';

const API = 'http://localhost:8080/api';

interface Job {
  id: number;
  name: string;
  type: string;
  cronExpression: string | null;
  nextRunAt: string | null;
  maxRetries: number;
  enabled: boolean;
}

interface Run {
  id: number;
  jobId: number;
  jobName: string;
  attempt: number;
  status: string;
  workerId: string | null;
  error: string | null;
  createdAt: string;
}

@Component({
  selector: 'app-root',
  imports: [DatePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})

export class DashboardComponent implements OnInit, OnDestroy {
  jobs = signal<Job[]>([]);
  runs = signal<Run[]>([]);
  filter = signal<'ALL' | 'DEAD'>('ALL');
  error = signal('');
  private timer?: ReturnType<typeof setInterval>;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.refresh();
    this.timer = setInterval(() => this.refresh(), 3000);
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  refresh() {
    this.http.get<Job[]>(`${API}/jobs`).subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.error.set('');
      },
      error: () =>
        this.error.set(
          'Cannot reach the API at localhost:8080. Start the Spring Boot backend.',
        ),
    });
    const query = this.filter() === 'DEAD' ? '?status=DEAD' : '';
    this.http
      .get<Run[]>(`${API}/runs${query}`)
      .subscribe({ next: (runs) => this.runs.set(runs) });
  }

  create(
    name: string,
    type: string,
    payload: string,
    cron: string,
    maxRetries: number,
  ) {
    if (!name.trim()) {
      this.error.set('Enter a job name.');
      return;
    }
    const body = {
      name: name.trim(),
      type,
      payload: payload.trim() || '{}',
      cronExpression: cron.trim() || null,
      maxRetries,
    };
    this.http.post(`${API}/jobs`, body).subscribe({
      next: () => this.refresh(),
      error: (e) =>
        this.error.set(
          e.error?.message ??
            'Could not create the job. Check the cron expression and payload.',
        ),
    });
  }

  trigger(id: number) {
    this.http
      .post(`${API}/jobs/${id}/trigger`, {})
      .subscribe(() => this.refresh());
  }

  toggle(id: number) {
    this.http
      .post(`${API}/jobs/${id}/toggle`, {})
      .subscribe(() => this.refresh());
  }

  replay(id: number) {
    this.http
      .post(`${API}/runs/${id}/replay`, {})
      .subscribe(() => this.refresh());
  }

  setFilter(filter: 'ALL' | 'DEAD') {
    this.filter.set(filter);
    this.refresh();
  }
}
