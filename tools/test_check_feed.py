"""Tests of tools/check_feed.py on small invented feed repositories (python3 -I -m unittest tools/test_check_feed.py)."""
import hashlib
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("check_feed", Path(__file__).with_name("check_feed.py"))
cf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cf)


def sha(data):
    return hashlib.sha256(data).hexdigest()


class Feed(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.old = os.getcwd()
        self.addCleanup(os.chdir, self.old)
        os.chdir(self.tmp.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "t@example.invalid")
        self.git("config", "user.name", "t")

    def git(self, *args):
        return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()

    def commit(self, code=3, pool=2, apk=b"apk-bytes", stated=None, words=b'["der Hund"]', word_hash=None, extra=None):
        Path("german-quiz.apk").write_bytes(apk)
        Path("latest.json").write_text(json.dumps({"versionCode": code, "apk": "german-quiz.apk", "sha256": stated or sha(apk)}))
        Path("content/vocab").mkdir(parents=True, exist_ok=True)
        Path("content/vocab/nouns.json").write_bytes(words)
        Path("content/content.json").write_text(json.dumps({"contentVersion": pool, "files": {"vocab/nouns.json": word_hash or sha(words)}}))
        if extra:
            Path(extra).write_text("x")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "state")
        return self.git("rev-parse", "HEAD")

    def test_a_consistent_feed_passes(self):
        self.assertEqual(cf.check(self.commit()), [])

    def test_an_apk_that_is_not_the_one_latest_json_describes_is_refused(self):
        problems = cf.check(self.commit(stated=sha(b"another apk")))
        self.assertTrue(any("sha256" in p and "APK" in p for p in problems), problems)

    def test_a_pool_file_that_differs_from_its_manifest_or_is_missing_is_refused(self):
        self.assertTrue(any("nouns.json" in p for p in cf.check(self.commit(word_hash=sha(b"other")))))
        first = self.commit()
        self.git("rm", "-q", "content/vocab/nouns.json")
        self.git("commit", "-q", "-m", "lost")
        self.assertTrue(any("not in the commit" in p for p in cf.check(self.git("rev-parse", "HEAD"), first)))

    def test_a_lower_app_or_pool_version_than_the_remote_is_refused_and_an_equal_or_higher_one_is_not(self):
        remote = self.commit(code=5, pool=4)
        self.assertEqual(cf.check(self.commit(code=5, pool=5), remote), [])
        problems = cf.check(self.commit(code=4, pool=3), remote)
        self.assertTrue(any("versionCode" in p for p in problems) and any("contentVersion" in p for p in problems), problems)

    def test_a_file_that_is_not_a_release_file_and_broken_json_are_refused(self):
        self.assertTrue(any("notes.txt does not belong" in p for p in cf.check(self.commit(extra="notes.txt"))))
        Path("latest.json").write_text("{broken")
        self.git("commit", "-qam", "broken")
        self.assertTrue(any("not valid JSON" in p for p in cf.check(self.git("rev-parse", "HEAD"))))

    def test_the_hook_reads_what_git_sends_refuses_with_exit_1_and_ignores_a_deleted_branch(self):
        good = self.commit()
        bad = self.commit(stated="0" * 64)
        line = "refs/heads/main {} refs/heads/main {}\n"
        self.assertEqual(cf.main(["--pre-push"], io.StringIO(line.format(good, cf.ZERO))), 0)
        self.assertEqual(cf.main(["--pre-push"], io.StringIO(line.format(bad, good))), 1)
        self.assertEqual(cf.main(["--pre-push"], io.StringIO(f"(delete) {cf.ZERO} refs/heads/old {good}\n")), 0)


if __name__ == "__main__":
    unittest.main()
