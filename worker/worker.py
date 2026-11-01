import json
import os
import random
import socket
import threading
import time

import pika
import redis
import requests

RABBIT_HOST = os.getenv("RABBIT_HOST", "localhost")
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
WORKER_ID = f"{socket.gethostname()}-{os.getpid()}"


def http_request(payload, timeout):
    resp = requests.request(
        payload.get("method", "GET"),
        payload["url"],
        json=payload.get("body"),
        timeout=timeout,
    )
    resp.raise_for_status()


def sleep(payload, timeout):
    time.sleep(min(payload.get("seconds", 2), timeout))


def flaky(payload, timeout):
    if random.random() < payload.get("failureRate", 0.5):
        raise RuntimeError("simulated failure")


HANDLERS = {"http_request": http_request, "sleep": sleep, "flaky": flaky}


def publish_result(ch, run_id, status, error=None):
    body = json.dumps(
        {"runId": run_id, "status": status, "workerId": WORKER_ID, "error": error}
    )
    ch.basic_publish(
        "",
        "results.queue",
        body,
        pika.BasicProperties(content_type="application/json", delivery_mode=2),
    )


def on_message(ch, method, props, body):
    msg = json.loads(body)
    run_id = msg["runId"]
    print(f"[{WORKER_ID}] run {run_id} ({msg['type']}) attempt {msg['attempt']}")
    publish_result(ch, run_id, "RUNNING")
    try:
        handler = HANDLERS[msg["type"]]
        payload = json.loads(msg.get("payload") or "{}")
        handler(payload, msg.get("timeoutSeconds") or 30)
        publish_result(ch, run_id, "SUCCEEDED")
    except Exception as exc:
        publish_result(ch, run_id, "FAILED", f"{type(exc).__name__}: {exc}")
    # Ack only after the result is published. If this process dies before this
    # line, RabbitMQ redelivers the message (at-least-once delivery).
    ch.basic_ack(method.delivery_tag)


def heartbeat_loop():
    """Refreshes a Redis key with a 15s TTL every 5s. If this process dies, the key
    expires and the backend knows the worker is gone."""
    client = redis.Redis(host=REDIS_HOST, decode_responses=True)
    while True:
        try:
            client.set(f"worker:{WORKER_ID}", "1", ex=15)
        except redis.RedisError as exc:
            print(f"[{WORKER_ID}] heartbeat failed: {exc}")
        time.sleep(5)


def main():
    threading.Thread(target=heartbeat_loop, daemon=True).start()
    while True:
        try:
            conn = pika.BlockingConnection(
                pika.ConnectionParameters(RABBIT_HOST, heartbeat=120)
            )
            ch = conn.channel()
            ch.basic_qos(prefetch_count=1)
            ch.basic_consume("jobs.queue", on_message)
            print(f"[{WORKER_ID}] waiting for jobs")
            ch.start_consuming()
        except (pika.exceptions.AMQPError, OSError) as exc:
            print(f"broker or queue not ready ({exc}); retrying in 5s")
            time.sleep(5)


if __name__ == "__main__":
    main()
