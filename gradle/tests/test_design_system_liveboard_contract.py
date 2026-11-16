"""Pinned export contract exercised through the actual production Gradle task."""
import copy
import json
import shutil

from design_system_liveboard_fixtures import (
    DesignFixture, GOLDEN_57, KOTLIN, RESOURCES, digest, phase1, write_json,
)


class LiveboardContractTest(DesignFixture):
    def test_standalone_legacy_pins_need_no_node(self):
        shutil.rmtree(self.package)
        self.assertEqual(len(self.lock["files"]), 3)
        self.assert_success(self.run_gradle(properties=self.pinned()))
        self.install_pin("0.2.0")
        self.assertEqual(len(self.lock["files"]), 14)
        self.assert_success(self.run_gradle(properties=self.pinned()))
        marker = self.repo / "build/design-system/verified.sha256"
        self.assertEqual(marker.read_text().strip(), digest(self.repo / "design-system.lock.json"))
        self.assertFalse(self.package.exists(), "standalone must not recreate/read a sibling package")
        (self.repo / phase1.TOKEN).write_text("tampered legacy token")
        self.assert_rejected(self.run_gradle(properties=self.pinned()),
                             "Pinned design resource missing or changed")
        self.record("reached", {"minimal3": True, "actual14": True, "tamperRejected": True})

    def test_complete_031_pin_verifies_without_node(self):
        self.install_pin()
        shutil.rmtree(self.package)
        result = self.run_gradle(properties=self.pinned())
        self.assert_success(result)
        self.assertIn("57 managed Compose exports", result.stdout)
        marker = self.repo / "build/design-system/verified.sha256"
        self.assertEqual(marker.read_text().strip(), digest(self.repo / "design-system.lock.json"))
        self.assertFalse(self.package.exists())
        self.record("reached", {"complete57": True, "nodeFree": True, "markerMatchesLock": True})

    def test_031_requires_every_accepted_export(self):
        self.require_031_admission()
        for name in sorted(GOLDEN_57):
            with self.subTest(missing_entry=name):
                self.install_pin()
                del self.lock["files"][name]
                (self.repo / name).unlink()
                write_json(self.repo / "design-system.lock.json", self.lock)
                result = self.run_gradle(properties=self.pinned())
                self.assert_rejected(result, "missing")
        for name in (KOTLIN + "LiveboardMotionSchema.kt", KOTLIN + "LiveboardScriptedGeometry.kt"):
            with self.subTest(missing_bytes=name):
                self.install_pin()
                (self.repo / name).unlink()
                self.assert_rejected(self.run_gradle(properties=self.pinned()),
                                     "Pinned design resource missing or changed")
        self.record("reached", {"missingEntries": 57, "missingBytes": 2})

    def test_031_rejects_unknown_exports(self):
        self.require_031_admission()
        extras = [KOTLIN + "LiveboardExtra.kt", KOTLIN + "OrbitExtra.kt",
                  RESOURCES + "drawable/liveboard_extra.xml",
                  RESOURCES + "files/liveboard_extra.json",
                  RESOURCES + "other/liveboard_brand.svg", KOTLIN + "LiveboardTokens.kt.extra"]
        for name in extras:
            with self.subTest(extra=name):
                self.install_pin()
                path = self.repo / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("self-consistent extra bytes")
                self.lock["files"][name] = digest(path)
                write_json(self.repo / "design-system.lock.json", self.lock)
                result = self.run_gradle(properties=self.pinned())
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertFalse((self.package / "calls.jsonl").exists())
        self.record("reached", {"unknownExportsRejected": len(extras)})

    def test_paths_are_canonical_and_never_symlinked(self):
        # Safety controls are independently reachable on the supported legacy pin.
        aliases = ["/absolute/OrbitTokens.kt", "../outside.kt", "./" + phase1.TOKEN,
                   phase1.TOKEN.replace("/generated/", "/generated//"),
                   phase1.TOKEN.replace("/generated/", "/generated/../generated/"),
                   phase1.TOKEN.replace("/", "\\"), phase1.TOKEN + "\x00"]
        for name in aliases:
            with self.subTest(path=name):
                self.install_pin("0.2.0")
                self.lock["files"][name] = self.lock["files"][phase1.TOKEN]
                write_json(self.repo / "design-system.lock.json", self.lock)
                self.assert_rejected(self.run_gradle(properties=self.pinned()),
                                     "Invalid managed design resource")
        for kind in ("lock", "file", "parent"):
            with self.subTest(symlink=kind):
                self.install_pin("0.2.0")
                path = self.repo / ("design-system.lock.json" if kind == "lock" else phase1.TOKEN)
                if kind == "parent":
                    path = path.parent
                held = self.workspace / ("owned-held-" + kind)
                path.rename(held)
                path.symlink_to(held, target_is_directory=kind == "parent")
                result = self.run_gradle(properties=self.pinned())
                self.assert_rejected(result, "regular design-system.lock" if kind == "lock"
                                     else "must not use symbolic links")
        self.record("reached", {"canonicalPathGuards": len(aliases), "symlinkGuards": 3})

    def test_lock_schema_and_hashes_fail_closed(self):
        mutations = [
            ("notObject", lambda lock: [], "Invalid design-system.lock.json object"),
            ("package", lambda lock: dict(lock, package="foreign"), "Unexpected design-system package"),
            ("missingVersion", lambda lock: {k: v for k, v in lock.items() if k != "version"}, "has no version"),
            ("numericVersion", lambda lock: dict(lock, version=31), "has no version"),
            ("malformedVersion", lambda lock: dict(lock, version="0.3"), "Invalid pinned design-system version"),
            ("manifestHash", lambda lock: dict(lock, sourceManifestSha256="bad"), "Invalid source manifest SHA-256"),
            ("filesMap", lambda lock: dict(lock, files=[]), "has no files map"),
            ("hashType", lambda lock: dict(lock, files={phase1.TOKEN: 31}), "Invalid design resource SHA-256"),
            ("hashValue", lambda lock: dict(lock, files={phase1.TOKEN: "A" * 64}), "Invalid managed design resource"),
        ]
        for label, mutate, cause in mutations:
            with self.subTest(schema=label):
                self.install_pin("0.2.0")
                write_json(self.repo / "design-system.lock.json", mutate(copy.deepcopy(self.lock)))
                self.assert_rejected(self.run_gradle(properties=self.pinned()), cause)
        self.install_pin("0.2.0")
        (self.repo / phase1.FONT).unlink()
        self.assert_rejected(self.run_gradle(properties=self.pinned()),
                             "Pinned design resource missing or changed")
        self.record("reached", {"schemaGuards": len(mutations), "missingResource": True})

    def test_workspace_identity_mismatch_precedes_tools(self):
        for field, bad in (("name", "foreign"), ("version", "9.9.9")):
            with self.subTest(identity=field):
                self.install_pin("0.2.0")
                node = self.node_wrapper(folder="identity " + field)
                package = {"name": "@meta-secret/design-system", "version": "0.2.0", field: bad}
                write_json(self.package / "package.json", package)
                result = self.run_gradle("checkDesignSystemToolchain", {
                    "designSystemMode": "workspace", "designSystemNode": node,
                    "designSystemNpm": self.npm})
                self.assert_rejected(result, "identity/version differs")
                self.assertFalse((node.parent / "used").exists())
                self.assertFalse((self.package / "calls.jsonl").exists())
        self.record("reached", {"identityBeforeNodeAndNpm": 2})

    def test_031_workspace_mismatch_precedes_tools(self):
        self.require_031_admission()
        node = self.node_wrapper()
        write_json(self.package / "package.json", {"name": "@meta-secret/design-system", "version": "0.2.0"})
        result = self.run_gradle("checkDesignSystemToolchain", {
            "designSystemMode": "workspace", "designSystemNode": node, "designSystemNpm": self.npm})
        self.assert_rejected(result, "identity/version differs")
        self.assertFalse((node.parent / "used").exists())
        self.assertFalse((self.package / "calls.jsonl").exists())
