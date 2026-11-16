"""Observe real Gradle up-to-date behavior and real shared export/sync bytes."""
from design_system_liveboard_fixtures import ACCEPTED_MANIFEST, DesignFixture, digest


class LiveboardInputsTest(DesignFixture):
    def test_liveboard_only_change_invalidates_actual_gradle_tasks(self):
        self.setup_fixture_generator()
        self.cache_cycle("liveboard/styles/vault.css", "310px", "1310px", self.tools())

    def test_used_brand_svg_change_invalidates_actual_gradle_tasks(self):
        self.setup_fixture_generator()
        self.cache_cycle("brand/metasecret-gray.svg", "#2C2E2F", "#2C2E2FFF", self.tools())

    def test_real_031_generator_and_sync_track_liveboard(self):
        self.require_031_admission()
        self.setup_real_generator()
        properties = self.tools()
        self.assert_success(self.run_gradle(properties=properties))
        self.assertEqual(digest(self.package / "dist/manifest.json"), ACCEPTED_MANIFEST)
        self.assertEqual(len(self.consistent_state()["files"]), 57)
        before, after = self.cache_cycle("liveboard/styles/vault.css", "min-height: 310px",
                                         "min-height: 1310px", properties)
        self.assertNotEqual(before["files"]["composeApp/src/commonMain/composeResources/files/liveboard_styles.json"],
                            after["files"]["composeApp/src/commonMain/composeResources/files/liveboard_styles.json"])
        self.record("reached", {"actualGeneratorAndCliSync": True, "complete57": True})

    def test_real_031_generator_and_sync_track_brand(self):
        self.require_031_admission()
        self.setup_real_generator()
        properties = self.tools()
        self.assert_success(self.run_gradle(properties=properties))
        self.assertEqual(digest(self.package / "dist/manifest.json"), ACCEPTED_MANIFEST)
        before, after = self.cache_cycle("brand/metasecret-gray.svg", "#2C2E2F",
                                         "#2C2E2FFF", properties)
        name = "composeApp/src/commonMain/composeResources/files/liveboard_brand.svg"
        self.assertNotEqual(before["files"][name], after["files"][name])
        self.record("reached", {"actualBrandExport": True, "complete57": len(after["files"]) == 57})

    def test_existing_tracked_inputs_remain_live(self):
        self.require_031_admission()
        self.setup_real_generator()
        properties = self.tools()
        # Mutations retain parsable source syntax and change actual deterministic outputs.
        cases = [("tokens/liveboard.json", '"#2c2e2f"', '"#2d2e2f80"'),
                 ("assets/fonts/OFL-Manrope.txt", "Copyright", "Copyright "),
                 ("scripts/liveboard-export.mjs", "// ${header}", "// Input fixture ${header}")]
        for name, before, after in cases:
            with self.subTest(input=name):
                self.cache_cycle(name, before, after, properties)
        self.record("reached", {"existingInputFamilies": 3})
