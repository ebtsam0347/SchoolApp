# Run backend locally (Docker)

All commands run from repo root.

## First time / full stack

```bash
docker compose --profile app up -d --build
```

Brings up infra (redis, kafka, minio) + all Java services + poller.

## Infra only (dev workflow — services run on host)

```bash
docker compose up -d
```

## Rebuild + redeploy one service after code changes

```bash
docker compose --profile app up -d --build <service>
```

Example:

```bash
docker compose --profile app up -d --build auth
```

Multiple services in one shot:

```bash
docker compose --profile app up -d --build auth employee gateway
```

`up -d --build <svc>` rebuilds the image, recreates only that container, and leaves everything else running.

## Logs

```bash
docker compose logs -f <service>
```

## Stop

```bash
docker compose --profile app down          # stop containers
docker compose --profile app down -v       # also wipe volumes (redis/kafka/minio data)
```

## Service names

`auth`, `audit`, `employee`, `attendance`, `notification`, `payroll`, `chat`, `bff`, `gateway`, `attendance-poller`.

## Ports

App on host: auth 8081, employee 8082, attendance 8083, notification 8084, audit 8085, bff 8086, payroll 8087, chat 8088, gateway 8080. Actuator = app port + 1000.
