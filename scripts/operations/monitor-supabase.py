"""Read-only availability probe. Diagnostics deliberately contain no external text."""

import json
import os
import re
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path


class ConfigurationError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def configuration(env):
    names = ("SUPABASE_PROJECT_REF", "SUPABASE_DB_HOST", "SUPABASE_DB_NAME",
             "SUPABASE_DB_USER", "SUPABASE_DB_PASSWORD", "SUPABASE_DB_CA")
    if any(not env.get(name) for name in names):
        raise ConfigurationError
    ref = env["SUPABASE_PROJECT_REF"]
    host = env["SUPABASE_DB_HOST"]
    user = env["SUPABASE_DB_USER"]
    if not re.fullmatch(r"[a-z]{20}", ref):
        raise ConfigurationError
    direct = host == f"db.{ref}.supabase.co"
    pooler = re.fullmatch(r"aws-[0-9]+-[a-z0-9-]+\.pooler\.supabase\.com", host)
    if not (direct or pooler):
        raise ConfigurationError
    if user != ("lavanda_monitor" if direct else f"lavanda_monitor.{ref}"):
        raise ConfigurationError
    if not re.fullmatch(r"[a-zA-Z0-9_]+", env["SUPABASE_DB_NAME"]):
        raise ConfigurationError
    if any(c in env["SUPABASE_DB_PASSWORD"] for c in "\x00\r\n"):
        raise ConfigurationError
    token = env.get("SUPABASE_STATUS_TOKEN", "")
    if token and (not token.isascii() or any(c.isspace() or ord(c) < 33 for c in token)):
        raise ConfigurationError
    try:
        ssl.create_default_context(cadata=env["SUPABASE_DB_CA"])
    except (ssl.SSLError, ValueError):
        raise ConfigurationError from None
    return ref, host, user


def database_check(env, host, user, certificate):
    # A fresh environment prevents inherited libpq options/services from changing the target.
    pg_env = {
        "PATH": os.defpath,
        "LANG": "C",
        "PGHOST": host,
        "PGPORT": "5432",
        "PGDATABASE": env["SUPABASE_DB_NAME"],
        "PGUSER": user,
        "PGPASSWORD": env["SUPABASE_DB_PASSWORD"],
        "PGSSLMODE": "verify-full",
        "PGSSLROOTCERT": str(certificate),
        "PGCONNECT_TIMEOUT": "10",
        "PGOPTIONS": "-c statement_timeout=5000 -c default_transaction_read_only=on",
        "PGAPPNAME": "lavanda-availability-monitor",
        "PGPASSFILE": "/dev/null",
    }
    for attempt in range(2):
        try:
            result = subprocess.run(
                ["psql", "-X", "-w", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", "SELECT 1"],
                env=pg_env, capture_output=True, timeout=15, check=False,
            )
        except subprocess.TimeoutExpired:
            result = None
        except OSError:
            raise ConfigurationError from None
        if result is not None:
            if result.returncode == 0:
                if result.stdout.strip() != b"1":
                    raise ConfigurationError
                return True
            # Never emit libpq diagnostics: even errors can contain credentials or endpoint details.
            errors = result.stderr.lower()
            if any(marker in errors for marker in (
                b"password authentication failed", b"no password supplied", b"tenant or user not found",
                b"certificate verify failed", b"does not match host name", b"root certificate",
                b"permission denied", b"does not exist", b"invalid connection option",
            )):
                raise ConfigurationError
        if attempt == 0:
            time.sleep(5)
    return False


def provider_status(env, ref):
    token = env.get("SUPABASE_STATUS_TOKEN")
    if not token:
        return "UNKNOWN_PROVIDER_STATUS"
    request = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{ref}",
        headers={"Authorization": f"Bearer {token}"}, method="GET",
    )
    # Disable environment proxies and redirects so the bearer token stays at this fixed origin.
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), NoRedirect(),
        urllib.request.HTTPSHandler(context=ssl.create_default_context()),
    )
    try:
        with opener.open(request, timeout=10) as response:
            payload = response.read(65537)
            if len(payload) > 65536:
                return "UNKNOWN_PROVIDER_STATUS"
            project = json.loads(payload)
    except urllib.error.HTTPError as error:
        error.close()
        if error.code in (401, 403):
            raise ConfigurationError from None
        return "UNKNOWN_PROVIDER_STATUS"
    except (OSError, ValueError):
        return "UNKNOWN_PROVIDER_STATUS"
    if (not isinstance(project, dict) or project.get("ref") != ref
            or ("id" in project and project["id"] != ref)
            or not isinstance(project.get("status"), str)):
        return "UNKNOWN_PROVIDER_STATUS"
    return {
        "ACTIVE_HEALTHY": "TRANSIENT_CONNECTIVITY_FAILURE",
        "INACTIVE": "CONFIRMED_PAUSED",
        "COMING_UP": "RESTORATION_IN_PROGRESS",
        "RESTORING": "RESTORATION_IN_PROGRESS",
    }.get(project.get("status"), "UNKNOWN_PROVIDER_STATUS")


def main():
    try:
        ref, host, user = configuration(os.environ)
        with tempfile.TemporaryDirectory(prefix="lavanda-monitor-") as directory:
            certificate = Path(directory) / "root.crt"
            certificate.write_text(os.environ["SUPABASE_DB_CA"], encoding="ascii")
            certificate.chmod(0o600)
            healthy = database_check(os.environ, host, user, certificate)
        status = "HEALTHY" if healthy else provider_status(os.environ, ref)
    except ConfigurationError:
        status = "INVALID_CONFIGURATION"
    except Exception:
        # No traceback: an exception may embed a password, token, URL, or provider response.
        status = "UNKNOWN_PROVIDER_STATUS"
    print(f"Supabase availability: {status}")
    if status != "HEALTHY":
        print("Availability not established. Follow docs/operations/supabase-availability.md.")
    return 0 if status == "HEALTHY" else 1


if __name__ == "__main__":
    raise SystemExit(main())
