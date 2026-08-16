# Windows releases and in-app updates

Each tenant application checks its own public manifest after startup:

```text
https://raw.githubusercontent.com/codenitros/BoatBookingSystemReleases/main/metadata/honnavar/stable.json
https://raw.githubusercontent.com/codenitros/BoatBookingSystemReleases/main/metadata/kasarkod/stable.json
```

The check runs in the background. When a higher semantic version is available, the user can choose
**Update and restart**. The application downloads the MSI over HTTPS, verifies its SHA-256 checksum
against the manifest, starts Windows Installer, and exits. A failed check or download leaves the
installed application unchanged.

## One-time GitHub setup

In the private `BoatBookingSystem` repository, create the following Actions configuration:

| Type | Name | Value / permission |
| --- | --- | --- |
| Variable | `RELEASE_REPOSITORY` | `codenitros/BoatBookingSystemReleases` |
| Secret | `RELEASE_REPO_TOKEN` | Fine-grained token with **Contents: Read and write** access only to `BoatBookingSystemReleases` |

The token is required because the release workflow publishes an MSI GitHub Release and commits the
new tenant-specific metadata file to a different repository.

## Publishing a release

1. Change `<version>` in `pom.xml` to the next semantic version, such as `1.1.0`.
2. Merge that change to `main`.
3. Create and push a tenant-specific annotated tag: `honnavar-v1.1.0` or `kasarkod-v1.1.0`.
4. The **Publish Windows release** workflow runs on Windows, verifies formatting, downloads the
   matching JavaFX Windows JMODs, and creates a self-contained per-user MSI containing the Java 17
   and JavaFX runtimes. It then uploads the installer to the public releases repository and updates
   the stable manifest.

For a deliberate manual release, use **Actions → Publish Windows release → Run workflow**, select
the tenant, and enter the exact version from `pom.xml`.

## Upgrade guarantees

- Each tenant has its own stable Windows upgrade UUID, so only a newer installer of the same tenant
  replaces an existing installation.
- It installs per user and does not require administrator access.
- User data stays in its tenant-specific user-home `BoatTicketSystem/<tenant>` directory and is not
  part of the MSI.
- Installer assets are GitHub Release assets rather than Git-tracked binaries, so the public
  repository remains small.
