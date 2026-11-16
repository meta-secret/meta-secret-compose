"""Owned temporary projects applying the real Gradle DS script, never app plugins."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest

import test_design_system_toolchain as phase1


WORKSPACE = phase1.REPO.parent
PACKAGE = WORKSPACE / "design-system"
ACCEPTED_MANIFEST = "5872eee1ddce9959c7ee3857ccd84f0ea229cf0bb724cae102ff46accf82c837"
LEGACY_MANIFEST = "ad8b58fee8ce55788f719ec557e76b353b5fbeef2579090b297f53c119b71526"
LEGACY_LOCK = "80e174aecb66387a908776dfb702d042dbceacd0da17835b67cd514f944479b3"
KOTLIN = "composeApp/src/commonMain/kotlin/ui/design/generated/"
RESOURCES = "composeApp/src/commonMain/composeResources/"
TOKEN_NAMES = (
    "OrbitTokens LiveboardTokens LiveboardGeometry LiveboardMotion LiveboardTimelines "
    "LiveboardMotionSchema LiveboardScriptedGeometry LiveboardMaterials LiveboardStyles "
    "LiveboardGlyphs LiveboardDeviceArt LiveboardUtilityArt LiveboardIcons "
    "LiveboardStyleRulesA LiveboardStyleRulesB"
).split()
ORBIT_NAMES = "apple approved back cancel create enter fusion join ok qr request".split()
LIVEBOARD_NAMES = (
    "approve back bell brand cancel close copy create current decline delete error hide "
    "lock paste pending pending_device plus qr remove reset search seed show"
).split()
GOLDEN_57 = frozenset(
    [KOTLIN + name + ".kt" for name in TOKEN_NAMES]
    + [RESOURCES + "drawable/orbit_" + name + ".xml" for name in ORBIT_NAMES]
    + [RESOURCES + "drawable/liveboard_" + name + ".xml" for name in LIVEBOARD_NAMES]
    + [RESOURCES + "files/liveboard_" + name + ".json"
       for name in ("geometry", "motion", "materials", "styles")]
    + [RESOURCES + "files/liveboard_brand.svg", phase1.FONT, phase1.LICENSE]
)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + "\n")


def export_destination(name):
    tail = name.removeprefix("compose/")
    if "/" not in tail:
        return KOTLIN + tail
    category, filename = tail.split("/", 1)
    return RESOURCES + ("files" if category == "licenses" else category) + "/" + filename


class DesignFixture(unittest.TestCase):
    # Reuse only fixture methods; none of the eight phase1 test bodies are inherited.
    setUp = phase1.ToolchainTest.setUp
    node_wrapper = phase1.ToolchainTest.node_wrapper

    def run_gradle(self, task="prepareDesignSystem", properties=None):
        env = os.environ.copy()
        env.update(PATH=phase1.MINIMAL_PATH,
                   GRADLE_USER_HOME=str(phase1.REPO / ".gradle-home"),
                   npm_config_offline="true", npm_config_update_notifier="false")
        if phase1.JBR.is_dir():
            env["JAVA_HOME"] = str(phase1.JBR)
        command = ["/bin/sh", str(phase1.REPO / "gradlew"), task,
                   "--offline", "--console=plain"]
        command += [f"-P{key}={value}" for key, value in (properties or {}).items()]
        result = subprocess.run(command, cwd=self.repo, env=env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
        self.invocation += 1
        phase1.RECEIPTS.mkdir(parents=True, exist_ok=True)
        stem = phase1.RECEIPTS / f"{self._testMethodName}-{self.invocation}"
        stem.with_suffix(".log").write_text(result.stdout)
        write_json(stem.with_suffix(".json"), {
            "command": command, "cwd": str(self.repo), "exit": result.returncode,
            "scriptSha256": digest(phase1.SCRIPT), "path": env["PATH"],
        })
        return result

    def assert_success(self, result):
        self.assertEqual(result.returncode, 0, result.stdout[-12000:])

    def assert_rejected(self, result, cause):
        self.assertNotEqual(result.returncode, 0, result.stdout[-12000:])
        self.assertIn(cause, result.stdout)

    def record(self, label, value):
        phase1.RECEIPTS.mkdir(parents=True, exist_ok=True)
        write_json(phase1.RECEIPTS / f"{self._testMethodName}-{label}.json", value)

    def install_pin(self, version="0.3.1"):
        shutil.rmtree(self.repo / "composeApp", ignore_errors=True)
        lock_path = self.repo / "design-system.lock.json"
        lock_path.unlink(missing_ok=True)
        manifest_path = PACKAGE / "dist/manifest.json"
        self.assertEqual(digest(manifest_path), ACCEPTED_MANIFEST)
        manifest = json.loads(manifest_path.read_text())
        files = {}
        for name, expected in manifest["files"].items():
            if not name.startswith("compose/"):
                continue
            if version == "0.2.0" and ("Liveboard" in name or "liveboard_" in name):
                continue
            source = PACKAGE / "dist" / name
            self.assertEqual(digest(source), expected)
            destination = export_destination(name)
            target = self.repo / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            content = source.read_bytes()
            if version == "0.2.0" and destination == phase1.TOKEN:
                content = content.replace(b"design-system 0.3.1.", b"design-system 0.2.0.", 1)
            target.write_bytes(content)
            files[destination] = digest(target)
        self.assertEqual(len(files), 14 if version == "0.2.0" else 57)
        if version == "0.3.1":
            self.assertEqual(frozenset(files), GOLDEN_57)
        self.lock = {"package": "@meta-secret/design-system", "version": version,
                     "sourceManifestSha256": LEGACY_MANIFEST if version == "0.2.0"
                     else ACCEPTED_MANIFEST, "files": files}
        write_json(lock_path, self.lock)
        if version == "0.2.0":
            self.assertEqual(digest(lock_path), LEGACY_LOCK, "fixture preserves exact original legacy pin")

    def pinned(self):
        return {"designSystemMode": "pinned", "designSystemNode": "/missing/node",
                "designSystemNpm": "/missing/npm"}

    def require_031_admission(self):
        self.install_pin()
        result = self.run_gradle(properties=self.pinned())
        if result.returncode:
            self.assertIn("Invalid managed design resource", result.stdout)
            self.record("UNREACHED", {"reason": "complete031 positive admission rejected",
                                      "guardsExecuted": False})
            self.skipTest("UNREACHED: complete031 positive admission rejected by production Gradle")

    def tools(self):
        return {"designSystemMode": "workspace", "designSystemNode": self.node_wrapper(),
                "designSystemNpm": self.npm}

    def setup_fixture_generator(self):
        """A real deterministic external generator; shared sync API stays unmodified."""
        for name, content in [("liveboard/styles/vault.css", ".tile {min-height:310px;}"),
                              ("brand/metasecret-gray.svg", '<svg fill="#2C2E2F"/>')]:
            path = self.package / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        shutil.copyfile(PACKAGE / "scripts/sync.mjs", self.package / "scripts/sync-contract.mjs")
        build = r"""import fs from 'node:fs';
import crypto from 'node:crypto';
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
fs.appendFileSync('calls.jsonl',JSON.stringify({kind:'build'})+'\n');
const source=fs.readFileSync('liveboard/styles/vault.css','utf8')+fs.readFileSync('brand/metasecret-gray.svg','utf8');
const files={'compose/OrbitTokens.kt':'// fixture input '+hash(source)+'\n',
 'compose/font/orbit_manrope_regular.otf':'fixture font\n',
 'compose/licenses/orbit_manrope_license.txt':'fixture license\n'};
for(const [name,bytes] of Object.entries(files)){
 fs.mkdirSync('dist/'+name.slice(0,name.lastIndexOf('/')),{recursive:true});
 if(!fs.existsSync('dist/'+name)||fs.readFileSync('dist/'+name,'utf8')!==bytes)fs.writeFileSync('dist/'+name,bytes);
}
const manifest=JSON.stringify({name:'@meta-secret/design-system',version:'0.2.0',
 files:Object.fromEntries(Object.entries(files).map(([n,b])=>[n,hash(b)]))},null,2)+'\n';
if(!fs.existsSync('dist/manifest.json')||fs.readFileSync('dist/manifest.json','utf8')!==manifest)fs.writeFileSync('dist/manifest.json',manifest);
"""
        (self.package / "scripts/build.mjs").write_text(build)
        (self.package / "scripts/sync.mjs").write_text(
            "import fs from 'node:fs';\nimport {syncClients} from './sync-contract.mjs';\n"
            "fs.appendFileSync('calls.jsonl',JSON.stringify({kind:'sync'})+'\\n');\n"
            "await syncClients({packageRoot:process.cwd(),client:'compose'});\n")

    def setup_real_generator(self):
        for name in ("scripts", "tokens", "assets", "liveboard", "node_modules"):
            target = self.package / name
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(PACKAGE / name, target, symlinks=True)
        (self.package / "brand").mkdir(exist_ok=True)
        shutil.copyfile(PACKAGE / "brand/metasecret-gray.svg",
                        self.package / "brand/metasecret-gray.svg")
        for name in ("package.json", "package-lock.json"):
            shutil.copyfile(PACKAGE / name, self.package / name)

    def consistent_state(self):
        manifest_path = self.package / "dist/manifest.json"
        manifest = json.loads(manifest_path.read_text())
        lock = json.loads((self.repo / "design-system.lock.json").read_text())
        self.assertEqual(lock["sourceManifestSha256"], digest(manifest_path))
        for name, expected in manifest["files"].items():
            self.assertEqual(digest(self.package / "dist" / name), expected)
        for name, expected in lock["files"].items():
            self.assertEqual(digest(self.repo / name), expected)
        return {"manifest": digest(manifest_path), "lock": digest(self.repo / "design-system.lock.json"),
                "files": lock["files"]}

    def package_controls(self, changed):
        return {str(p.relative_to(self.package)): digest(p)
                for p in self.package.rglob("*") if p.is_file()
                and p.relative_to(self.package).parts[0] not in {"dist", "node_modules"}
                and p.name != "calls.jsonl" and p != changed}

    def mutate(self, relative_name, before, after):
        path = self.package / relative_name
        text = path.read_text()
        self.assertIn(before, text)
        self.assertNotEqual(len(before), len(after), "avoid timestamp-resolution dependence")
        old_hash = digest(path)
        path.write_text(text.replace(before, after, 1))
        self.assertNotEqual(digest(path), old_hash)

    def cache_cycle(self, relative_name, before, after, properties):
        first = self.run_gradle(properties=properties)
        self.assert_success(first)
        old_state = self.consistent_state()
        second = self.run_gradle(properties=properties)
        self.assert_success(second)
        self.assertIn(":buildDesignSystem UP-TO-DATE", second.stdout)
        self.assertIn(":syncDesignSystem UP-TO-DATE", second.stdout)
        changed = self.package / relative_name
        controls = self.package_controls(changed)
        self.mutate(relative_name, before, after)
        self.assertEqual(self.package_controls(changed), controls)
        self.assertEqual(self.consistent_state(), old_state, "input edit must not edit generated outputs")
        third = self.run_gradle(properties=properties)
        self.assert_success(third)
        new_state = self.consistent_state()
        fourth = self.run_gradle(properties=properties)
        self.assert_success(fourth)
        checks = {
            "buildReran": ":buildDesignSystem UP-TO-DATE" not in third.stdout,
            "syncReran": ":syncDesignSystem UP-TO-DATE" not in third.stdout,
            "manifestChanged": old_state["manifest"] != new_state["manifest"],
            "pinChanged": old_state["lock"] != new_state["lock"],
            "generatedBytesChanged": old_state["files"] != new_state["files"],
            "fourthBuildUpToDate": ":buildDesignSystem UP-TO-DATE" in fourth.stdout,
            "fourthSyncUpToDate": ":syncDesignSystem UP-TO-DATE" in fourth.stdout,
        }
        self.record("causal-cache", {"input": relative_name, "checks": checks,
                                    "before": old_state, "after": new_state})
        self.assertEqual([name for name, passed in checks.items() if not passed], [], checks)
        return old_state, new_state
