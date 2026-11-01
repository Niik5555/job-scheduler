# Distributed job scheduler

Schedule cron or one-off jobs, run them on a pool of Python workers, and recover from failures with
retries, backoff, a dead-letter queue, and dead-worker detection.

**Stack:** Spring Boot, Angular, Python, RabbitMQ, PostgreSQL, Redis, Docker Compose. Everything is free and runs locally.

```
                  Angular dashboard
                         |
                         v
     Spring Boot: REST API, scheduler, reaper ------> PostgreSQL (jobs, runs)
              |  ^                      |
              v  |                      v
     RabbitMQ (jobs, retry tiers,    Redis (worker heartbeats,
     results, dead-letter queue)      reaper lock)
              |  ^                      ^
              v  |                      |
     Python workers (scale out) --------+
```

## Features
- Cron jobs (6-field Spring cron, evaluated in UTC) and manual "run now" triggers
- Several scheduler instances can run side by side without dispatching a job twice
- At-least-once delivery with acks sent after the result is reported
- Retries with backoff (5s, 15s, 45s), then a dead-letter queue with one-click replay
- Worker heartbeats in Redis, a live "Workers online" list, and a reaper that resets runs stuck on dead workers
- Dashboard for jobs, run history, worker health, and dead-letter replay

## Run it

You need Docker, JDK 17+, Maven, and Node 20+.

1. Start Postgres, RabbitMQ, Redis, and 3 workers:
   ```
   docker compose up -d --build --scale worker=3
   ```
2. Start the backend (creates the tables and queues on startup):
   ```
   cd backend
   mvn spring-boot:run
   ```
3. Start the dashboard:
   ```
   cd job-scheduler-front
   npm install
   npx ng serve
   ```
   Open http://localhost:4200.

| Service | Address |
|---|---|
| Dashboard | http://localhost:4200 |
| API | http://localhost:8080/api |
| RabbitMQ UI | http://localhost:15672 (guest / guest) |
| PostgreSQL | localhost:5433 (scheduler / scheduler) |
| Redis | localhost:6379 |

Workers keep retrying until the backend has created the queues, so start order does not matter.
Use `--build` after changing worker code, or `docker compose build --no-cache worker` if the old code keeps running.

## API

| Method and path | What it does |
|---|---|
| `POST /api/jobs` | Create a job (`name`, `type`, `payload`, optional `cronExpression`, `maxRetries`, `timeoutSeconds`) |
| `GET /api/jobs` | List jobs |
| `POST /api/jobs/{id}/trigger` | Run a job now |
| `POST /api/jobs/{id}/toggle` | Pause or resume a job |
| `GET /api/jobs/{id}/runs` | Latest runs of one job |
| `GET /api/runs?status=DEAD` | Latest runs, optionally filtered by status |
| `POST /api/runs/{id}/replay` | Send a DEAD run through the queue again |
| `GET /api/workers` | Workers with a live heartbeat |

Job types handled by the workers: `http_request`, `sleep`, `flaky` (fails at random, for testing).

## Try it

Create these in the dashboard:

| Name | Type | Payload | Cron | Retries |
|---|---|---|---|---|
| `ping-example` | `http_request` | `{"url":"https://example.com"}` | `0/30 * * * * *` | 3 |
| `always-flaky` | `flaky` | `{"failureRate":1.0}` | empty | 2 |
| `slow-long` | `sleep` | `{"seconds":25}` | empty | 0 |

Demos worth recording:
- **Retries and dead-letter queue:** run `always-flaky`. It fails, waits 5s, fails, waits 15s, fails, and ends DEAD.
  Open the Dead-letter queue tab and press Replay.
- **Worker crash:** run `slow-long`, then `docker kill <container id>` for the worker shown on the run
  (use the part of the Worker column before the dash). RabbitMQ redelivers the unacked message and another
  worker finishes it.
- **Dead workers and the reaper:** run `slow-long`, then `docker compose stop worker`. Within about 20 seconds
  the run goes back to QUEUED with a "Worker stopped" note and the Workers list empties.
  Run `docker compose up -d --scale worker=3` and it completes.
- **Scale out:** `docker compose up -d --scale worker=5` and trigger several jobs at once.

## How it works

- **No double dispatch:** the scheduler claims due jobs with `SELECT ... FOR UPDATE SKIP LOCKED`, so several
  backend instances can run at the same time and each locks different rows.
- **At-least-once delivery:** workers ack a message only after publishing the result. A crash before the ack
  means redelivery, which does not count as a retry, so handlers should be idempotent.
- **Retries with backoff:** failed runs go to `jobs.retry.1/2/3`, queues with no consumers and a TTL.
  When the TTL expires they dead-letter back into `jobs.queue`. One queue per tier avoids head-of-line blocking.
- **Dead-letter queue:** after `maxRetries` the run is marked DEAD and copied to `jobs.dlq`. Replay resets it to attempt 1.
- **Publish after commit:** messages are sent only after the database transaction commits, so a worker never
  reports on a run that does not exist yet.
- **Worker heartbeats:** each worker writes `worker:<id>` to Redis with a 15s TTL every 5s. If a worker dies,
  its key expires.
- **Reaper:** every 5s one backend instance (chosen with a short Redis lock) finds RUNNING runs whose worker has no
  heartbeat and sets them back to QUEUED. RabbitMQ still holds the unacked message and redelivers it, so nothing
  is republished. The update is compare-and-set, so it cannot overwrite a run that just finished.
- **Redis is not critical:** it holds only short-lived, rebuildable state. If Redis is down, jobs still schedule,
  run, retry, and finish. Only the worker list and the reaper pause.

## Known limits and next steps
- Commit-then-publish can still lose a message if RabbitMQ is down at that exact moment. The transactional outbox
  pattern would close that gap.
- The reaper corrects status for dead workers. It does not detect a hung handler on a live worker, and job
  timeouts are enforced only inside the handlers.
- The backend reads results with a single consumer. Raising its concurrency needs a guard so a RUNNING message
  cannot overwrite a later SUCCEEDED one.
- No authentication, job dependencies, or pagination beyond the latest 50 runs.
- Tables are created by Hibernate (`ddl-auto: update`). Flyway migrations would be the next step for real use.
- No load-test results yet.
