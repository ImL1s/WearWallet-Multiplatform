#!/usr/bin/env python3
"""Regression tests for the dependency-free Markdown link checker."""

from __future__ import annotations

import importlib.util
import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).parents[1] / "check_markdown_links.py"
SPEC = importlib.util.spec_from_file_location("check_markdown_links", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
check_markdown_links = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check_markdown_links)


class FenceRegressionTest(unittest.TestCase):
    def missing_targets(self, markdown: str) -> list[str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = pathlib.Path(temporary_directory)
            subprocess.run(
                ["git", "init", "--quiet"], cwd=root, check=True
            )
            source = root / "fixture.md"
            source.write_text(markdown, encoding="utf-8")
            subprocess.run(
                ["git", "add", "fixture.md"], cwd=root, check=True
            )
            return [
                raw_target
                for _, _, raw_target, _ in check_markdown_links.find_missing_targets(root)
            ]

    def test_shorter_backtick_run_does_not_close_longer_fence(self) -> None:
        missing = self.missing_targets(
            """\
````markdown
```markdown
[example inside fence](missing-inside.md)
```
````
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])

    def test_longer_matching_run_closes_backtick_fence(self) -> None:
        missing = self.missing_targets(
            """\
```markdown
[example inside fence](missing-inside.md)
````
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])

    def test_different_marker_does_not_close_tilde_fence(self) -> None:
        missing = self.missing_targets(
            """\
~~~markdown
```
[example inside fence](missing-inside.md)
```
~~~~
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])

    def test_four_space_pseudo_opener_does_not_start_fence(self) -> None:
        missing = self.missing_targets(
            """\
    ````markdown
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])

    def test_four_space_pseudo_closer_does_not_close_fence(self) -> None:
        missing = self.missing_targets(
            """\
````markdown
    ````
[example inside fence](missing-inside.md)
````
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])

    def test_trailing_text_pseudo_closer_does_not_close_fence(self) -> None:
        missing = self.missing_targets(
            """\
````markdown
````not-a-closing-fence
[example inside fence](missing-inside.md)
````
[outside fence](missing-outside.md)
"""
        )

        self.assertEqual(missing, ["missing-outside.md"])


if __name__ == "__main__":
    unittest.main()
