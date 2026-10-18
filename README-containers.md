# Run O-Bank with Podman

## This Windows machine: mirrored networking

Your earlier working Podman setup used WSL mirrored networking and host-network containers to reach Oracle on `localhost:1522` and an existing Kafka broker on `localhost:9092`. The new `compose.mirrored.yaml` uses the same approach for the current backend copy and adds the frontend on `localhost:8001` because port `8000` is already in use. It does not start a second Kafka broker.

From this backend folder:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 up -d --build
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 ps
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 logs --tail=80
```

Stop with `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-mirrored.ps1 down`. The first invocation creates a project-local Python environment and installs `podman-compose==1.6.0`. On later starts, `up -d --no-build` reuses the images. Keep the Podman machine running and WSL mirrored networking enabled.

The mirrored frontend image uses a Red Hat Nginx base image and proxies `/api/` to the gateway on `localhost:8443`. The Java images also use the Red Hat UBI base images from the previous setup. The frontend source is in `frontend/`. Build its generated `web/` output before container builds.

## Standard bridged stack

This stack runs the existing frontend, seven Java services, and one Kafka broker. It connects to the existing Oracle `FREEPDB1` database on the Windows host. It does not create or replace Oracle schemas or data.

## Prepare

1. Install Podman for Windows and start a Podman machine (`podman machine init` once, then `podman machine start`). Check `podman info`.
2. Install a Compose provider, such as `podman-compose`, and check `podman compose version`. Podman's `compose` command delegates to an external provider.
3. Make sure Oracle is running on host port `1522` and is reachable from the Podman machine. The container connection uses `host.containers.internal`. Change `ORACLE_JDBC_URL` in `.env` if your Oracle listener has a different reachable address.
4. Stop any locally running services on ports `8000`, `8443`, or `8761`, or change the host port values in `.env`.

From the repository root, create `.env` using the helper, fill in its credentials, and build the frontend as described in README.md before starting:

```powershell
.\prepare-podman-env.ps1
podman compose up --build -d
podman compose ps
```

Open `http://localhost:8000`. The frontend image uses nginx to send `/api/` requests to the gateway. No frontend application source was changed; the image adjusts its copied JavaScript file during build.

The first build downloads base images and Maven dependencies. Wait for service registration in Eureka before testing. For logs:

```powershell
podman compose logs -f gateway identity banking credit forex investment
```

## Publish application images to Quay.io

The Java Containerfile removes database passwords, mail password, JWT secret, and internal API keys from the packaged JAR. The private `.env` file supplies them to containers at runtime. Do not publish images built from an older Containerfile that still packages those values.

After the local images build, use your Quay.io username or organization namespace:

```powershell
.\publish-quay.ps1 -Namespace YOUR_QUAY_NAMESPACE -Tag dev
```

The script logs in to Quay.io, tags the eight O-Bank images, and pushes them. It does not push the third-party Kafka image. Set the Quay repositories to private and grant access only to your team.

To run those published images, set `IMAGE_REGISTRY=quay.io/YOUR_QUAY_NAMESPACE` and `IMAGE_TAG=dev` in the private `.env` file, then run:

```powershell
podman compose pull
podman compose up --no-build -d
```

Set `IMAGE_REGISTRY=localhost` again before doing a local `up --build`.

## Stop and rebuild

```powershell
podman compose down
podman compose up --build -d
```

Kafka data stays in the `kafka-data` volume. `podman compose down -v` deletes that volume. Oracle data stays on the host. The frontend image copies the current `web` output, so rebuild Oracle JET's `web` output before rebuilding the image after frontend source changes.
