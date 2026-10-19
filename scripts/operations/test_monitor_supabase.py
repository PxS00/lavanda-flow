"""Synthetic tests only: no provider or database connections."""

import contextlib
import importlib.util
import io
import json
import subprocess
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import MagicMock, patch


SPEC = importlib.util.spec_from_file_location("monitor", Path(__file__).with_name("monitor-supabase.py"))
monitor = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(monitor)
REF = "abcdefghijklmnopqrst"
ENV = {
    "SUPABASE_PROJECT_REF": REF,
    "SUPABASE_DB_HOST": "aws-0-sa-east-1.pooler.supabase.com",
    "SUPABASE_DB_NAME": "postgres",
    "SUPABASE_DB_USER": f"lavanda_monitor.{REF}",
    "SUPABASE_DB_PASSWORD": "synthetic-password-$'!",
    "SUPABASE_DB_CA": "synthetic-ca",
    "SUPABASE_STATUS_TOKEN": "synthetic-token",
}
SUCCESS = subprocess.CompletedProcess([], 0, b"1\n", b"")
FAILURE = subprocess.CompletedProcess([], 2, b"", b"synthetic-password synthetic-token network failure")


class MonitorTest(unittest.TestCase):
    def setUp(self):
        self.run = self.enterContext(patch.object(monitor.subprocess, "run", return_value=SUCCESS))
        self.sleep = self.enterContext(patch.object(monitor.time, "sleep"))
        self.context = self.enterContext(patch.object(monitor.ssl, "create_default_context"))
        self.opener = MagicMock()
        self.build = self.enterContext(patch.object(monitor.urllib.request, "build_opener", return_value=self.opener))
        self.response = self.opener.open.return_value.__enter__.return_value

    def execute(self, env=None):
        output = io.StringIO()
        with (patch.dict(monitor.os.environ, ENV if env is None else env, clear=True),
              contextlib.redirect_stdout(output), contextlib.redirect_stderr(output)):
            code = monitor.main()
        text = output.getvalue()
        for secret in (ENV["SUPABASE_DB_PASSWORD"], ENV["SUPABASE_STATUS_TOKEN"], ENV["SUPABASE_DB_HOST"],
                       "synthetic-ca", "synthetic-password", "business-data", "sensitive"):
            self.assertNotIn(secret, text)
        return code, text

    def status(self, status, **fields):
        self.response.read.return_value = json.dumps({"ref": REF, "id": REF, "status": status, **fields}).encode()

    def test_success_and_exact_database_contract(self):
        def probe(*args, **kwargs):
            certificate = Path(kwargs["env"]["PGSSLROOTCERT"])
            self.assertEqual(certificate.read_text(), ENV["SUPABASE_DB_CA"])
            self.assertEqual(certificate.stat().st_mode & 0o777, 0o600)
            self.assertEqual(certificate.parent.stat().st_mode & 0o777, 0o700)
            return SUCCESS

        self.run.side_effect = probe
        self.assertEqual(self.execute()[0], 0)
        self.run.assert_called_once()
        args, kwargs = self.run.call_args
        self.assertEqual(args[0], ["psql", "-X", "-w", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", "SELECT 1"])
        self.assertEqual(kwargs["timeout"], 15)
        self.assertTrue(kwargs["capture_output"])
        env = kwargs["env"]
        self.assertEqual(env["PGSSLMODE"], "verify-full")
        self.assertEqual(env["PGPORT"], "5432")
        self.assertEqual(env["PGCONNECT_TIMEOUT"], "10")
        self.assertEqual(env["PGOPTIONS"], "-c statement_timeout=5000 -c default_transaction_read_only=on")
        self.assertEqual(env["PGPASSWORD"], ENV["SUPABASE_DB_PASSWORD"])
        self.assertFalse(Path(env["PGSSLROOTCERT"]).exists())
        self.assertNotIn(ENV["SUPABASE_DB_PASSWORD"], str(args))
        self.assertNotIn("SUPABASE_STATUS_TOKEN", env)
        self.assertNotIn("PGSERVICE", env)
        self.build.assert_not_called()

    def test_missing_required_configuration(self):
        for key in ENV.keys() - {"SUPABASE_STATUS_TOKEN"}:
            with self.subTest(key=key):
                self.assertIn("INVALID_CONFIGURATION", self.execute({**ENV, key: ""})[1])
        self.run.assert_not_called()

    def test_invalid_configuration(self):
        cases = {
            "SUPABASE_PROJECT_REF": "../other",
            "SUPABASE_DB_HOST": "attacker.example",
            "SUPABASE_DB_USER": "postgres",
            "SUPABASE_DB_NAME": "postgres sslmode=disable",
            "SUPABASE_DB_PASSWORD": "bad\npassword",
            "SUPABASE_STATUS_TOKEN": "bad\r\ntoken",
        }
        for key, value in cases.items():
            with self.subTest(key=key):
                self.assertIn("INVALID_CONFIGURATION", self.execute({**ENV, key: value})[1])
        self.context.side_effect = monitor.ssl.SSLError("synthetic-ca")
        self.assertIn("INVALID_CONFIGURATION", self.execute()[1])
        self.run.assert_not_called()

    def test_direct_endpoint_identity(self):
        direct = {**ENV, "SUPABASE_DB_HOST": f"db.{REF}.supabase.co", "SUPABASE_DB_USER": "lavanda_monitor"}
        self.assertEqual(self.execute(direct)[0], 0)
        self.assertIn("INVALID_CONFIGURATION", self.execute({**direct, "SUPABASE_DB_HOST": "db.zyxwvutsrqponmlkjihg.supabase.co"})[1])

    def test_retry_can_establish_readiness(self):
        self.run.side_effect = [FAILURE, SUCCESS]
        self.assertEqual(self.execute()[0], 0)
        self.assertEqual(self.run.call_count, 2)
        self.sleep.assert_called_once_with(5)
        self.build.assert_not_called()

    def test_failures_and_provider_states_never_claim_readiness(self):
        self.run.return_value = FAILURE
        for status, expected in (
            ("ACTIVE_HEALTHY", "TRANSIENT_CONNECTIVITY_FAILURE"),
            ("INACTIVE", "CONFIRMED_PAUSED"),
            ("COMING_UP", "RESTORATION_IN_PROGRESS"),
            ("RESTORING", "RESTORATION_IN_PROGRESS"),
            ("PAUSING", "UNKNOWN_PROVIDER_STATUS"),
            ("NEW_STATE", "UNKNOWN_PROVIDER_STATUS"),
        ):
            with self.subTest(status=status):
                self.run.reset_mock()
                self.sleep.reset_mock()
                self.status(status)
                code, text = self.execute()
                self.assertEqual(code, 1)
                self.assertIn(expected, text)
                self.assertEqual(self.run.call_count, 2)
                self.sleep.assert_called_once_with(5)
        request = self.opener.open.call_args.args[0]
        self.assertEqual(request.full_url, f"https://api.supabase.com/v1/projects/{REF}")
        self.assertEqual(request.get_method(), "GET")
        self.assertEqual(self.opener.open.call_args.kwargs["timeout"], 10)
        self.response.read.assert_called_with(65537)
        handlers = self.build.call_args.args
        self.assertEqual(handlers[0].proxies, {})
        self.assertIsInstance(handlers[1], monitor.NoRedirect)
        self.assertIsNone(handlers[1].redirect_request(None, None, 302, "", {}, "https://attacker.example"))

    def test_timeout_boundaries(self):
        self.run.side_effect = subprocess.TimeoutExpired("psql", 15, stderr=b"synthetic-password")
        self.opener.open.side_effect = TimeoutError("synthetic-token")
        self.assertIn("UNKNOWN_PROVIDER_STATUS", self.execute()[1])
        self.assertEqual(self.run.call_count, 2)
        self.sleep.assert_called_once_with(5)

    def test_unknown_or_mismatched_response(self):
        self.run.return_value = FAILURE
        for payload in (b"accepted", b"null", b"[]", b"{}", b"x" * 65537,
                        json.dumps({"ref": "wrong", "status": "INACTIVE"}).encode(),
                        json.dumps({"ref": REF, "id": "wrong", "status": "INACTIVE"}).encode(),
                        json.dumps({"ref": REF, "status": []}).encode()):
            with self.subTest(payload=payload[:40]):
                self.response.read.return_value = payload
                self.assertIn("UNKNOWN_PROVIDER_STATUS", self.execute()[1])

    def test_no_token_and_api_errors(self):
        self.run.return_value = FAILURE
        self.assertIn("UNKNOWN_PROVIDER_STATUS", self.execute({**ENV, "SUPABASE_STATUS_TOKEN": ""})[1])
        self.build.assert_not_called()
        for code in (401, 403, 429, 500, 302):
            with self.subTest(code=code):
                self.opener.open.side_effect = urllib.error.HTTPError("synthetic-token", code, "sensitive", {}, None)
                expected = "INVALID_CONFIGURATION" if code in (401, 403) else "UNKNOWN_PROVIDER_STATUS"
                self.assertIn(expected, self.execute()[1])

    def test_authentication_tls_and_tool_errors(self):
        for error in (b"password authentication failed", b"certificate verify failed", b"permission denied",
                      b"Tenant or user not found", b"root certificate"):
            self.run.reset_mock()
            self.run.return_value = subprocess.CompletedProcess([], 2, b"", error)
            self.assertIn("INVALID_CONFIGURATION", self.execute()[1])
            self.run.assert_called_once()
        self.run.side_effect = FileNotFoundError("synthetic-password")
        self.assertIn("INVALID_CONFIGURATION", self.execute()[1])
        self.run.side_effect = RuntimeError("synthetic-token")
        self.assertIn("UNKNOWN_PROVIDER_STATUS", self.execute()[1])

    def test_unexpected_success_output_is_not_readiness(self):
        self.run.return_value = subprocess.CompletedProcess([], 0, b"business-data", b"")
        self.assertIn("INVALID_CONFIGURATION", self.execute()[1])

    def test_workflow_credential_boundary(self):
        workflow = Path(".github/workflows/supabase-availability.yml").read_text()
        self.assertNotIn("pull_request", workflow)
        self.assertIn("if: github.ref == 'refs/heads/main'", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertIn("timeout-minutes: 5", workflow)
        self.assertIn("timeout --signal=TERM 90s", workflow)
        for name in ("SUPABASE_DB_PASSWORD", "SUPABASE_STATUS_TOKEN", "SUPABASE_DB_CA"):
            self.assertIn("${{ secrets." + name + " }}", workflow)
        self.assertEqual(workflow.count("cron:"), 3)


if __name__ == "__main__":
    unittest.main()
