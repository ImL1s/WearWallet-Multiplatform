#!/usr/bin/env python3
"""TDD tests for scripts/check_release_manifest_attack_surface.py."""

from __future__ import annotations

import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).parents[1] / "check_release_manifest_attack_surface.py"
SPEC = importlib.util.spec_from_file_location("check_release_manifest_attack_surface", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


GEMINI_AND_MIC_MANIFEST = """\
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <application android:allowBackup="false">
        <service
            android:name=".ai.system.WearWalletGeminiLiveService"
            android:exported="false"
            android:foregroundServiceType="microphone" />
    </application>
</manifest>
"""

REMOVED_GEMINI_AND_MIC_MANIFEST = """\
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"
        tools:node="remove" />
    <application android:allowBackup="false">
        <service
            android:name=".ai.system.WearWalletGeminiLiveService"
            tools:node="remove" />
    </application>
</manifest>
"""

CLEAN_RELEASE_MANIFEST = """\
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:allowBackup="false" />
</manifest>
"""


class ReleaseManifestAttackSurfaceTest(unittest.TestCase):
    def scan(self, xml: str) -> list[str]:
        with tempfile.NamedTemporaryFile(
            "w",
            suffix=".xml",
            encoding="utf-8",
            delete=False,
        ) as handle:
            handle.write(xml)
            path = pathlib.Path(handle.name)
        try:
            return checker.scan_manifest(path)
        finally:
            path.unlink(missing_ok=True)

    def test_flags_gemini_live_service_and_microphone_fgs(self) -> None:
        violations = self.scan(GEMINI_AND_MIC_MANIFEST)
        joined = "\n".join(violations)
        self.assertTrue(
            any("WearWalletGeminiLiveService" in item for item in violations),
            joined,
        )
        self.assertTrue(
            any("FOREGROUND_SERVICE_MICROPHONE" in item for item in violations),
            joined,
        )

    def test_tools_node_remove_is_not_a_release_declaration(self) -> None:
        self.assertEqual(self.scan(REMOVED_GEMINI_AND_MIC_MANIFEST), [])

    def test_clean_release_manifest_passes(self) -> None:
        self.assertEqual(self.scan(CLEAN_RELEASE_MANIFEST), [])


if __name__ == "__main__":
    unittest.main()
