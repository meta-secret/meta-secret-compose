"""Exercise the production Kotlin Gradle script, without app plugins or network."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "gradle/design-system.gradle.kts"
MINIMAL_PATH = "/usr/bin:/bin:/usr/sbin:/sbin"
NODE = next((p for p in (Path("/opt/homebrew/bin/node"), Path("/usr/local/bin/node"))
             if p.is_file() and os.access(p, os.X_OK)), None)
JBR = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
RECEIPTS = Path(os.environ.get("DESIGN_SYSTEM_TEST_RECEIPTS", REPO / "build/test-reports/toolchain"))
TOKEN = "composeApp/src/commonMain/kotlin/ui/design/generated/OrbitTokens.kt"
FONT = "composeApp/src/commonMain/composeResources/font/orbit_manrope_regular.otf"
LICENSE = "composeApp/src/commonMain/composeResources/files/orbit_manrope_license.txt"


class ToolchainTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="orbit-gradle-toolchain-")
        self.addCleanup(self.temp.cleanup)
        self.workspace = Path(self.temp.name).resolve()
        self.repo = self.workspace / "meta-secret-compose"
        self.package = self.workspace / "design-system"
        self.repo.mkdir()
        self.package.mkdir()
        (self.repo / "settings.gradle.kts").write_text('rootProject.name = "toolchain-fixture"\n')
        (self.repo / "build.gradle.kts").write_text(f"apply(from = {json.dumps(str(SCRIPT))})\n")
        files = {}
        for name in (TOKEN, FONT, LICENSE):
            target = self.repo / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"synthetic pinned export\n")
            files[name] = hashlib.sha256(target.read_bytes()).hexdigest()
        self.lock = {"package": "@meta-secret/design-system", "version": "0.2.0",
                     "sourceManifestSha256": "0" * 64, "files": files}
        (self.repo / "design-system.lock.json").write_text(json.dumps(self.lock))
        (self.package / "package.json").write_text(json.dumps({
            "name": "@meta-secret/design-system", "version": "0.2.0"}))
        (self.package / "package-lock.json").write_text(json.dumps({
            "name": "@meta-secret/design-system", "version": "0.2.0", "lockfileVersion": 3,
            "packages": {"": {"name": "@meta-secret/design-system", "version": "0.2.0"}}}))
        scripts = self.package / "scripts"
        scripts.mkdir()
        record = """import fs from 'node:fs';
fs.appendFileSync('calls.jsonl', JSON.stringify({kind:KIND, path:process.env.PATH})+'\\n');
fs.mkdirSync('dist/compose', {recursive:true});
fs.writeFileSync('dist/manifest.json', '{}');
"""
        for name in ("build", "sync"):
            (scripts / f"{name}.mjs").write_text(record.replace("KIND", json.dumps(name)))
        self.npm = self.workspace / "npm tools" / "npm"
        self.npm.parent.mkdir()
        self.npm.write_text("""#!/usr/bin/env node
const fs = require('node:fs');
fs.appendFileSync('calls.jsonl', JSON.stringify({kind:'npm',path:process.env.PATH,args:process.argv.slice(2)})+'\\n');
if(process.argv[2]==='--version') console.log('10.0.0');
else if(process.argv[2]==='ci') fs.mkdirSync('node_modules',{recursive:true});
else process.exit(19);
""")
        self.npm.chmod(0o755)
        self.invocation = 0

    def gradle(self, task="checkDesignSystemToolchain", properties=None, path=MINIMAL_PATH, success=True):
        env = os.environ.copy()
        env["PATH"] = path
        env["GRADLE_USER_HOME"] = str(REPO / ".gradle-home")
        env["npm_config_offline"] = "true"
        env["npm_config_update_notifier"] = "false"
        if JBR.is_dir():
            env["JAVA_HOME"] = str(JBR)
        command = ["/bin/sh", str(REPO / "gradlew"), task, "--offline", "--console=plain"]
        command.extend(f"-P{k}={v}" for k, v in (properties or {}).items())
        result = subprocess.run(command, cwd=self.repo, env=env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
        self.invocation += 1
        RECEIPTS.mkdir(parents=True, exist_ok=True)
        (RECEIPTS / f"{self._testMethodName}-{self.invocation}.log").write_text(result.stdout)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout[-12000:])
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout[-12000:])
        return result.stdout

    def node_wrapper(self, folder="selected node tools", old=False):
        self.assertIsNotNone(NODE, "This macOS regression requires installed Homebrew Node")
        binary = self.workspace / folder / "node"
        binary.parent.mkdir(exist_ok=True)
        args = []
        if old:
            bootstrap = binary.parent / "old.cjs"
            bootstrap.write_text("Object.defineProperty(process.versions,'node',{value:'21.0.0'});")
            args = ["--require", str(bootstrap)]
        binary.write_text(f"#!{sys.executable}\nimport os,sys\n"
                          f"open({str(binary.parent / 'used')!r},'a').write('used\\n')\n"
                          f"os.execv({str(NODE)!r},[{str(NODE)!r}]+{args!r}+sys.argv[1:])\n")
        binary.chmod(0o755)
        return binary

    @unittest.skipUnless(sys.platform == "darwin", "Homebrew fallback is macOS-only")
    def test_minimal_path_uses_installed_homebrew(self):
        self.assertIsNotNone(NODE, "Installed Node is a required reproduction prerequisite")
        self.gradle("installDesignSystemDependencies")

    def test_explicit_tools_spaces_and_all_exec_child_paths(self):
        node = self.node_wrapper()
        self.gradle("prepareDesignSystem", {"designSystemNode": node, "designSystemNpm": self.npm})
        calls = [json.loads(line) for line in (self.package / "calls.jsonl").read_text().splitlines()]
        self.assertEqual({c["kind"] for c in calls}, {"npm", "build", "sync"})
        expected = [str(node.parent), str(self.npm.parent), *MINIMAL_PATH.split(":")]
        self.assertTrue(all(c["path"].split(":") == expected for c in calls), calls)
        self.assertTrue((node.parent / "used").is_file())
        output = self.gradle("prepareDesignSystem", {"designSystemNode": node, "designSystemNpm": self.npm})
        self.assertIn(":buildDesignSystem UP-TO-DATE", output)
        self.assertIn(":syncDesignSystem UP-TO-DATE", output)

    def test_independent_overrides_and_path_precedence(self):
        node = self.node_wrapper()
        self.gradle(properties={"designSystemNode": node})
        self.assertTrue((node.parent / "used").is_file())
        self.gradle(properties={"designSystemNpm": self.npm})
        (node.parent / "used").unlink()
        self.gradle(properties={"designSystemNpm": self.npm}, path=f"{node.parent}:{MINIMAL_PATH}")
        self.assertTrue((node.parent / "used").is_file())

    def test_invalid_explicit_tools_do_not_fallback(self):
        missing = self.workspace / "absent tool"
        output = self.gradle(properties={"designSystemNode": missing}, success=False)
        self.assertIn("designSystemNode", output)
        missing.write_text("not executable")
        output = self.gradle(properties={"designSystemNpm": missing}, success=False)
        self.assertIn("designSystemNpm", output)
        node = self.node_wrapper()
        alias = node.with_name("custom-node22")
        node.rename(alias)
        output = self.gradle(properties={"designSystemNode": alias, "designSystemNpm": self.npm}, success=False)
        self.assertIn("designSystemNode", output)
        self.assertIn("npm children", output)

    def test_old_path_node_fails_instead_of_fallback(self):
        node = self.node_wrapper(old=True)
        output = self.gradle(path=f"{node.parent}:{MINIMAL_PATH}", success=False)
        self.assertIn("Node >=22", output)
        self.assertTrue((node.parent / "used").is_file())

    def test_relative_and_empty_path_entries(self):
        node = self.node_wrapper(folder="relative tools")
        tools = self.package / "relative tools"
        shutil.copytree(node.parent, tools)
        self.gradle("prepareDesignSystem", {"designSystemNpm": self.npm}, path=f"relative tools:{MINIMAL_PATH}")
        calls = [json.loads(line) for line in (self.package / "calls.jsonl").read_text().splitlines()]
        self.assertEqual(calls[-1]["path"].split(":")[0], str(tools))
        shutil.copy2(node, self.package / "node")
        self.gradle("prepareDesignSystem", {"designSystemNpm": self.npm}, path=f":{MINIMAL_PATH}")
        calls = [json.loads(line) for line in (self.package / "calls.jsonl").read_text().splitlines()]
        self.assertEqual(calls[-1]["path"].split(":")[0], str(self.package))

    def test_pinned_uses_no_tools_and_preserves_integrity(self):
        props = {"designSystemMode": "pinned", "designSystemNode": "/missing/node",
                 "designSystemNpm": "/missing/npm"}
        self.gradle("prepareDesignSystem", props)
        self.assertFalse((self.package / "calls.jsonl").exists())
        (self.repo / TOKEN).write_text("tampered")
        output = self.gradle("prepareDesignSystem", props, success=False)
        self.assertIn("Pinned design resource missing or changed", output)

    def test_workspace_version_mismatch_still_fails(self):
        (self.package / "package.json").write_text(json.dumps({
            "name": "@meta-secret/design-system", "version": "9.9.9"}))
        output = self.gradle(success=False)
        self.assertIn("identity/version differs", output)


if __name__ == "__main__":
    unittest.main(verbosity=2)
