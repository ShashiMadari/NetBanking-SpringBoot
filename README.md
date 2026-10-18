# O-Bank NetBanking

Spring Boot banking microservices with an Oracle JET frontend, Oracle Database, Kafka integration, and Podman container configuration.

## Project layout

| Directory | Component |
| --- | --- |
| `eureka-server` | Service discovery |
| `o-back-gateway` | API gateway |
| `o-back-identity` | Authentication, users, notifications, and audit |
| `o-back-banking` | Accounts, payments, and banking transactions |
| `o-bank-credit` | Cards, rewards, merchant purchases, loans, and statements |
| `o-bank-forex` | Forex exchange, wallets, cards, and rates |
| `o-bank-investment` | Investment orders, portfolios, recurring plans, and FD maturity |
| `frontend` | Oracle JET application and Nginx container configuration |

## Requirements

- Java 17 and Maven (or the included Maven wrappers) for local backend development.
- Node.js 18 and npm for the frontend build; dependencies are pinned in `frontend/package-lock.json`.
- Oracle Database with the application's service schemas and stored procedures provisioned.
- Kafka. The mirrored configuration expects an existing broker on `localhost:9092`.
- Podman and a running Podman machine. The Windows configuration uses WSL mirrored networking.
- Python for the `run-mirrored.ps1` Compose wrapper.

Database provisioning files in this repository are not a complete automated setup for every service. No database contents or customer exports are included.

## Configure credentials

From the repository root:

```powershell
.\prepare-podman-env.ps1
```

Edit the generated `.env` and fill every password and key. `MAIL_USERNAME` is your SMTP sender address. Use a strong Base64 JWT secret suitable for the configured signing algorithm and matching internal service keys. The example file contains no credentials. Never commit `.env`.

Container Compose files inject these values. For running a service directly through Maven or an IDE, supply the corresponding environment variables there as well. Changes in `.env` are not automatically loaded by Maven.

## Build the frontend

Generated `web/` output and `node_modules/` are excluded from Git. Create the web output before building the frontend image:

```powershell
cd frontend
npm ci
npx ojet build
cd ..
```

## Start the Windows mirrored stack

Start Oracle and Kafka first. Start the Podman machine, then ensure the base images are present because the build wrapper uses `--pull=never`:

```powershell
podman machine start
podman pull registry.access.redhat.com/ubi9/openjdk-17:latest
podman pull registry.access.redhat.com/ubi9/openjdk-17-runtime:latest
podman pull registry.access.redhat.com/ubi9/nginx-124:latest
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 up -d --build
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 ps
```

Open <http://localhost:8001>. Allow time for service registration. The mirrored frontend serves the application through Nginx and forwards `/api` calls to the gateway on port 8443.

```powershell
# Inspect logs
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 logs --tail=100

# Stop containers
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 down
```

`compose.yaml` provides the alternative bridged configuration, including Kafka, with frontend port 8000. Use one configuration at a time. See [container instructions](README-containers.md) for Quay image publishing.

## Repository contents

This is a shareable source copy of the project. Runtime credentials have been replaced with environment placeholders. Build outputs, local environments, logs, browser test reports, and private runtime files are excluded.
