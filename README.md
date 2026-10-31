# Distributed job scheduler

Schedule cron or one-off jobs, run them on a pool of Python workers, and recover from failures with
retries, backoff and a dead-letter queue.

```
Angular dashboard -> Spring Boot (REST API + scheduler) -> PostgreSQL (state)
                                   |  ^
                                   v  |
                               RabbitMQ (jobs, retry tiers, results, DLQ)
                                   |  ^
                                   v  |
                            Python workers (scale out)
```

## What you need
Docker, JDK 17+, Maven, Node 20+ (for the dashboard). Everything is free and runs locally.

## Run it

1. Start Postgres, RabbitMQ and 3 workers:
   ```
   docker compose up -d --build --scale worker=3
   ```
   RabbitMQ UI: http://localhost:15672 (guest / guest).
   The workers keep retrying until the backend has created the queues, so start order does not matter.

2. Start the backend (creates tables and queues on startup):
   ```
   cd backend
   mvn spring-boot:run
   ```

3. Create the dashboard (one time), then copy the provided files over the generated ones:
   ```
   npx @angular/cli new dashboard --defaults --ssr=false --skip-git
   cp dashboard-src/main.ts dashboard/src/main.ts
   cp dashboard-src/app/*.ts dashboard/src/app/
   cd dashboard
   npx ng serve
   ```
   Open http://localhost:4200. Delete the generated root component files in `src/app`
   (`app.ts`, `app.html`, `app.css`, `app.spec.ts`, or the `app.component.*` equivalents) if the build
   complains about them.

## Try it

Create these jobs in the dashboard (or with curl):

```
curl -X POST localhost:8080/api/jobs -H 'Content-Type: application/json' -d '{
  "name": "ping-example", "type": "http_request",
  "payload": "{\"url\":\"https://example.com\"}",
  "cronExpression": "0/30 * * * * *", "maxRetries": 3 }'

curl -X POST localhost:8080/api/jobs -H 'Content-Type: application/json' -d '{
  "name": "always-flaky", "type": "flaky",
  "payload": "{\"failureRate\":1.0}", "maxRetries": 2 }'
```

Cron has 6 fields: second minute hour day-of-month month day-of-week.

Demos worth recording:
- **Retries and DLQ:** trigger `always-flaky`. It fails, waits 5s, fails, waits 15s, fails, and ends as DEAD.
  Open the Dead-letter queue tab and press Replay.
- **Worker crash:** create a `sleep` job with payload `{"seconds":20}`, trigger it, then
  `docker stop` the worker container that picked it up. RabbitMQ redelivers the unacked message
  to another worker.
- **Scale out:** `docker compose up -d --scale worker=5` and trigger several jobs at once.

## How it works

- **No double dispatch:** the scheduler claims due jobs with `SELECT ... FOR UPDATE SKIP LOCKED`,
  so several backend instances can run at the same time.
- **At-least-once delivery:** workers ack a message only after publishing the result. A crash before the
  ack means redelivery, so handlers should be idempotent.
- **Retries with backoff:** failed runs go to `jobs.retry.1/2/3`, queues with no consumers and a TTL.
  When the TTL expires they dead-letter back into `jobs.queue`. One queue per tier avoids head-of-line blocking.
- **Dead-letter queue:** after `maxRetries` the run is marked DEAD and copied to `jobs.dlq`.
- **Publish after commit:** messages are sent only after the DB transaction commits.

## Known limits (good next steps)
- No worker heartbeats or crash reaper, and no job timeout enforcement beyond the handler's own timeout.
  Add Redis heartbeats with a TTL and a reaper that requeues stale RUNNING runs.
- Commit-then-publish can still lose a message if the broker is down at that instant.
  Fix with the transactional outbox pattern.
- No auth, job dependencies, or pagination beyond the latest 50 runs.
- Tables are created by Hibernate (`ddl-auto: update`). Use Flyway migrations for anything real.
