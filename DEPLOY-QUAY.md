# Run the published O-Bank containers

The eight application images are published under **`quay.io/shashimadari`**. All repositories are public; no Quay login is needed to pull them.

**Oracle and Kafka are also available as containers.** To start all 10 components with fresh persistent database and broker volumes, use **[DEPLOY-FULL.md](DEPLOY-FULL.md)** and `run-full.ps1`. The instructions below are the alternative for an existing external Oracle/Kafka setup.

- Version tag: **`demo-20261023`**
- Convenience tag: **`latest`** (can change with future publications)
- Platform: **Linux / AMD64**
- Deployment configuration for this release: **`compose.mirrored.yaml`**

## Published images

```text
quay.io/shashimadari/o-bank-eureka:demo-20261023
quay.io/shashimadari/o-bank-identity:demo-20261023
quay.io/shashimadari/o-bank-banking:demo-20261023
quay.io/shashimadari/o-bank-credit:demo-20261023
quay.io/shashimadari/o-bank-forex:demo-20261023
quay.io/shashimadari/o-bank-investment:demo-20261023
quay.io/shashimadari/o-bank-gateway:demo-20261023
quay.io/shashimadari/o-bank-frontend:demo-20261023
```

Each also has a `latest` tag. Browse the repositories at <https://quay.io/user/shashimadari/>.

## Pull one image

```powershell
podman pull quay.io/shashimadari/o-bank-frontend:demo-20261023
```

Pulling an image downloads that component. The frontend needs Gateway and the backend services for application features.

## Run the application without building

### 1. Prepare dependencies

Use a running Podman machine with **Windows WSL mirrored networking** configured. Oracle must have the required service schemas, tables, procedures, and test users. Start the existing Kafka broker and Oracle listener:

- Kafka: `localhost:9092`
- Oracle: `localhost:1522/FREEPDB1`

These dependencies and customer data are **not** bundled into the eight application images. SMTP settings are required for OTP email and statement/reminder delivery. Python with the Windows `py` launcher is required by the Compose wrapper.

### 2. Get deployment files

```powershell
git clone https://github.com/ShashiMadari/NetBanking-SpringBoot.git
cd NetBanking-SpringBoot
powershell -NoProfile -ExecutionPolicy Bypass -File .\prepare-podman-env.ps1
```

If you already cloned the repository, update your checkout and work from its root.

### 3. Configure `.env`

Set these two lines in the generated `.env`:

```dotenv
IMAGE_REGISTRY=quay.io/shashimadari
IMAGE_TAG=demo-20261023
```

Fill all blank database passwords, mail settings, JWT signing secret, and internal keys with values for your own environment. Never share or commit `.env`. For different Oracle/Kafka addresses, adjust the corresponding environment values in `compose.mirrored.yaml`.

### 4. Pull and start

Start your existing Podman machine if it is stopped:

```powershell
podman machine start
```

Then run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 pull
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 up -d
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 ps
```

Open **<http://localhost:8001>** and allow time for backend registration. This route uses prebuilt images, so it needs no local Maven build, Node installation, or frontend build output.

**Do not add `--build`** when running this published release. The wrapper's build mode is for the local `localhost/...:dev` images. Use the mirrored Compose file: the published frontend listens on port 8001 and proxies to `127.0.0.1:8443`. It is not the port-80 frontend expected by the alternative bridged `compose.yaml`.

### 5. Logs and shutdown

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 logs --tail=100
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 down
```

The mirrored stack does not remove your external Oracle data or stop your separately managed Kafka broker.

## Release scope

These are the existing demo application images, including simulated Forex rates, accelerated statement/reminder schedules, and FD maturity with manual settlement. Public pull access does not provide access to the maintainer's database or credentials. Supply your own runtime configuration.
