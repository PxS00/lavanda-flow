# Windows operator workstation

## Purpose

This document records the physical workstation and trusted-LAN validation for the initial Lavanda Flow host. It is maintainer evidence for #185 and #187, not the final operator go-live runbook.

## Supported workstation profile

The validated host is a Samsung Galaxy Book with Windows 11 Home, a 12th Gen Intel Core i5-1235U, x64 architecture, and 8 GB RAM. It uses Docker Desktop 4.90.0, Docker Engine 29.7.2, Docker Compose v5.5.1, and the WSL 2 backend (WSL 2.7.13.0).

WSL 2 is Docker Desktop infrastructure only. No Ubuntu or other general-purpose WSL distribution is required, and daily operation remains entirely in Windows.

Validation used revision `929634efb881b771c0c8376eac389e587d589502`.

## Runtime topology

The existing `lavanda-flow-operational` Compose project remains unchanged. It has exactly two long-running services:

- `lavanda-flow-app`, published on TCP port 8080;
- `postgres`, using the named `postgres-data` volume on the private Compose network.

PostgreSQL has no published host port. Docker inspection reported `{"5432/tcp":null}`, and `Test-NetConnection` to both `127.0.0.1:5432` and `192.168.15.12:5432` failed as expected. Never expose PostgreSQL through a Windows Firewall or router rule.

## Operational checkout and secrets

The operational checkout is maintainer-controlled. Its ignored `.env.operational` remains outside source control and holds PostgreSQL credentials. The initial operator bootstrap was used once, then disabled; plaintext bootstrap username/password configuration was cleared. The persisted operator account continued working after application recreation and restart.

Do not copy credentials, bootstrap values, or `.env.operational` contents into source control, documentation, screenshots, logs, or issue comments.

## Automatic startup

Docker Desktop starts automatically when the Windows user signs in. Docker Desktop may run in the background; closing its window is harmless, but quitting Docker Desktop is not normal operator use.

A full Windows reboot was tested. After normal sign-in, Docker Desktop and the existing runtime recovered without maintainer or developer intervention. Tablet access and operator login returned, and the PostgreSQL named volume plus the persisted operator account survived the reboot.

## Operator shortcut

The Windows desktop shortcut is named `Lavanda Flow` and opens:

```text
http://192.168.15.12:8080
```

It requires no terminal, repository navigation, Docker Desktop UI, Maven, pnpm, or IDE. Browser closure does not stop either operational service; reopening the shortcut returns to the application normally.

## Trusted LAN endpoint

The operational Wi-Fi network is a Windows Private network. The Galaxy Book uses DHCP with a router reservation for `192.168.15.12`; the operator and tablet use the stable URL above on the trusted Wi-Fi.

Physical router validation confirmed that the port-forwarding table is empty, DMZ is disabled, UPnP is disabled, and DDNS is disabled. No router rule exposes Lavanda Flow or PostgreSQL to the public internet. Do not document or retain router credentials or the device MAC address in repository material.

## Windows Firewall

The validated inbound rule is:

| Setting | Value |
| --- | --- |
| Display name | `Lavanda Flow` |
| Protocol | TCP |
| Local port | 8080 |
| Action | Allow inbound |
| Profile | Private only |
| Remote address | LocalSubnet |
| Edge traversal | Blocked |

There is no TCP 5432 rule. Do not add one or allow a secondary backend port.

## Sleep and closed-lid behavior

The host is intentionally unavailable while asleep, powered off, or disconnected from Wi-Fi. That remains the accepted local-first limitation.

The business workflow requires tablet access while the powered Galaxy Book is closed. The tested Windows lid policy is therefore:

- on battery: close lid → Sleep;
- plugged in: close lid → Do nothing.

Connected-to-power closed-lid operation was tested successfully from the tablet. While plugged in, system sleep is disabled for this operational posture. Preserve normal battery suspend behavior and do not make unnecessary global battery power-management changes.

## Persistence and authentication validation

Notebook and tablet access succeeded through the stable trusted-LAN URL. Operator authentication succeeded on both devices, with the existing session and CSRF/XSRF behavior working unchanged and without CORS or JWT changes.

Browser closure, application recreation/restart, and full Windows reboot preserved the PostgreSQL named volume and persisted operator account.

## Backup and off-notebook validation

`scripts/operations/backup-postgres.sh` ran successfully on the Galaxy Book and created a custom-format dump plus SHA-256 sidecar. Local checksum verification returned OK.

Google Drive is the selected zero-recurring-cost off-notebook mechanism. Both the dump and its checksum were uploaded, downloaded again to a temporary local folder, and verified successfully after the round trip. Account identifiers and credentials are intentionally not recorded. Temporary downloaded verification copies may be removed afterward.

The operational database has no real inventory or production records yet; representative table counts are zero. Do not insert synthetic records into it merely to satisfy restore validation. Full representative disposable restore verification remains owned by #184 and #187. Backup frequency and retention remain defined in [PostgreSQL backup and restore](postgresql-backup-restore.md).

## Operator daily boundary

Normal operator use is:

1. Power on the Galaxy Book.
2. Sign in to Windows.
3. Wait for Docker Desktop and the runtime to recover automatically.
4. Open the `Lavanda Flow` desktop shortcut.
5. Use the notebook or tablet on the trusted LAN.

The operator must not need PowerShell, Git Bash, Docker Desktop UI, repository navigation, Docker commands, Maven, pnpm, or IDE tools.

## Maintainer notes

- Docker Desktop must remain running in the background; do not use Quit Docker Desktop during normal operation.
- Do not use `docker compose down -v`.
- PostgreSQL port 5432 must remain unpublished; do not create a firewall rule or router forwarding for it.
- Keep secrets only in ignored `.env.operational`.
- Backup and recovery operations remain maintainer procedures until #185 finalizes the operator runbook.

## Validation evidence

Physical validation confirmed notebook and same-Wi-Fi tablet access, stable DHCP-reserved addressing, Private-profile local-subnet firewall scope, no PostgreSQL LAN exposure, no router public exposure, operator authentication, browser-independent service lifecycle, reboot recovery, powered closed-lid availability, named-volume and operator-account persistence, local backup creation, and Google Drive checksum-verified off-notebook transfer.

## Known non-blocking observation

During physical validation, `localhost:8080` was unreliable on this host while `127.0.0.1:8080` worked. This does not change the runtime or LAN design: the supported operator shortcut uses the stable LAN URL `http://192.168.15.12:8080`.
