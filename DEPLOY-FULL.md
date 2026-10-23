# Run O-Bank with containerized Oracle and Kafka

This deployment uses **10 published images**: the eight application services plus a clean Oracle Database Free image and a single-node Kafka broker.

| Dependency | Published image | Contents |
| --- | --- | --- |
| Oracle | `quay.io/shashimadari/o-bank-oracle:demo-20261023` | Oracle Database Free plus structure-only initialization for five O-Bank schemas (55 tables, packages, constraints, indexes, and triggers) |
| Kafka | `quay.io/shashimadari/o-bank-kafka:demo-20261023` | Kafka 4.3.1 in KRaft mode, without existing topics or messages |

Both also provide `latest`. Use the dated tag for this deployment. Images target Linux/AMD64. Oracle binaries and their included license are retained unchanged; see `/licenses/license.txt` in the image and the [Oracle Free Use Terms and Conditions](https://www.oracle.com/downloads/licenses/oracle-free-license.html). Kafka license and notice files are included under `/opt/kafka`.

Public pulls were verified for both dependencies and the eight application images. See [container-images.json](container-images.json) for the release's registry digests.

## What is included

- Oracle creates `O_BANK_IDENTITY`, `O_BANK_BANKING`, `O_BANK_CREDIT`, `O_BANK_FOREX`, and `O_BANK_INVESTMENT` using passwords supplied at runtime.
- Schema initialization runs automatically on first startup and records a completion marker in its data volume.
- Later starts preserve the initialized database. The hook refuses to overwrite pre-existing O-Bank schemas without its marker.
- Kafka initializes a new broker data directory when its volume is empty.
- Oracle and Kafka use separate persistent named volumes. The application waits for their health checks before starting database-dependent services.

**No customer records, account balances, administrator logins, password hashes, catalogue seed records, or old Kafka messages are included.** Provision your own demo users, accounts, reference data, and product catalogues after initialization. Registration requires an existing eligible customer; an empty schema alone does not create a customer or administrator.

## Requirements

- Windows with Podman, WSL mirrored networking, and a running Podman machine.
- Python with the `py` launcher; `run-full.ps1` installs its Compose provider in `.venv` if missing.
- Available memory and disk space for Oracle and the complete service stack. Oracle is substantially larger than the application images; leave several GB of memory available.
- SMTP settings for email-based OTP, statements, and reminders.
- No native Oracle or Kafka installation is required for this mode.

## 1. Clone and configure

```powershell
git clone https://github.com/ShashiMadari/NetBanking-SpringBoot.git
cd NetBanking-SpringBoot
powershell -NoProfile -ExecutionPolicy Bypass -File .\prepare-podman-env.ps1
```

For an existing checkout, update it and use its repository root. Open `.env` and set:

```dotenv
IMAGE_REGISTRY=quay.io/shashimadari
IMAGE_TAG=demo-20261023
ORACLE_HOST_PORT=1522
ORACLE_PWD=
IDENTITY_DB_PASSWORD=
BANKING_DB_PASSWORD=
CREDIT_DB_PASSWORD=
FOREX_DB_PASSWORD=
INVESTMENT_DB_PASSWORD=
MAIL_USERNAME=
MAIL_PASSWORD=
JWT_SECRET=
IDENTITY_API_KEY=
BANKING_API_KEY=
INVESTMENT_SERVICE_KEY=
```

Fill every blank value yourself. `ORACLE_PWD` is the Oracle administrator password. Schema passwords must contain 8-64 characters from letters, digits, and `_#@$!%+=.-` for this initialization script. Quote values appropriately in `.env`, especially if they contain `#` or `$`. Do not share or commit `.env`.

Use a suitable random Base64 JWT signing secret and consistent internal service keys. The same database passwords are passed to Oracle initialization and the matching backend services.

If port 1522 is already used by your existing Oracle installation, set **`ORACLE_HOST_PORT=11522`** instead. The full Compose configuration uses this value for both the database port mapping and the application JDBC URLs. Avoid port conflicts with existing Kafka (`9092`, `9093`) or application services; stop the old stack/broker before starting this full stack.

## 2. Pull and start

Start the existing Podman machine if stopped:

```powershell
podman machine start
```

Then run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 pull
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 up -d
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 ps
```

No application source build or frontend build is needed. Do not add `--build`.

Wait for Oracle initialization and dependency health checks. Open **<http://localhost:8001>** after service registration. Eureka is at <http://localhost:8761>.

## 3. Check initialization

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 logs --tail=100 oracle
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 logs --tail=100 kafka
```

On first initialization, Oracle reports:

```text
O-Bank schemas initialized successfully (structure only, no customer data).
```

On subsequent startup it reports that existing data is being preserved. An initialization or health-check failure should be resolved before opening the application.

The containerized database is reachable by SQL Developer at `localhost`, your `ORACLE_HOST_PORT`, service name **`FREEPDB1`**, using the schema account and password you supplied.

## 4. Stop without deleting data

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-full.ps1 down
```

The `o-bank-full` project retains its `oracle-data` and `kafka-data` volumes. Avoid `down -v` when you want to keep data. Image updates do not automatically change existing database structures or passwords; initialization is not a migration or password-rotation mechanism.

## Pull dependencies individually

```powershell
podman pull quay.io/shashimadari/o-bank-oracle:demo-20261023
podman pull quay.io/shashimadari/o-bank-kafka:demo-20261023
```

## Rebuild these images from source

For Oracle, obtain the vendor base image and build the schema wrapper:

```powershell
podman pull container-registry.oracle.com/database/free:latest
podman build --format docker -f infrastructure/oracle/Containerfile -t localhost/o-bank-oracle:demo-20261023 infrastructure/oracle
```

For Kafka, obtain and extract the official Apache Kafka 4.3.1 binary distribution. Supply that directory as the named build context (example path `C:\kafka`):

```powershell
podman build --format docker --build-context kafkadist=C:\kafka -f infrastructure/kafka/Containerfile -t localhost/o-bank-kafka:demo-20261023 infrastructure/kafka
```

Only distribution files are copied into the image; `kafka-logs` and other existing message directories are not copied. Oracle schema definitions are under `infrastructure/oracle/schema/`; the image does not copy any existing `oradata` volume.

The full deployment uses host networking for the application and Kafka and a loopback port mapping for Oracle. It is intended for the Windows/WSL mirrored setup, rather than an externally exposed or clustered database/broker deployment.
