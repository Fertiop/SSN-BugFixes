"""Offline characterization of the real SSN 1.13.2 JAR (not a proof of fixed behavior)."""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import subprocess
import unittest
import zipfile

EXPECTED_SHA256 = "1db6f5595e5edc255db6800de9e0e1aa8e01e10d79d1784d549d70eb2cf53012"
CLASSES = {
    "request": "com.lothrazar.storagenetwork.block.request.TileRequest",
    "base": "com.lothrazar.storagenetwork.block.TileConnectable",
    "builder": "com.lothrazar.storagenetwork.item.ItemBuilder",
    "network": "com.lothrazar.storagenetwork.block.main.NetworkModule",
    "craft": "com.lothrazar.storagenetwork.gui.ContainerNetwork",
}


def jar_path() -> Path:
    raw = os.environ.get("SSN_JAR")
    if not raw:
        raise RuntimeError("Set SSN_JAR to the released SimpleStorageNetwork-1.20.1-1.13.2.jar; never skip")
    path = Path(raw).expanduser().resolve()
    if not path.is_file():
        raise FileNotFoundError(path)
    return path


def method_body(output: str, declaration: str) -> str:
    """Select only one javap method, not all later methods."""
    start = output.find("  " + declaration)
    if start < 0:
        raise AssertionError(f"Missing javap method: {declaration}")
    following = re.search(r"(?m)^  (?:public|protected|private|static) ", output[start + 3:])
    return output[start:start + 3 + following.start()] if following else output[start:]


class SSN1132BytecodeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        jar = jar_path()
        actual = hashlib.sha256(jar.read_bytes()).hexdigest()
        if actual != EXPECTED_SHA256:
            raise AssertionError(f"Wrong SSN JAR: expected {EXPECTED_SHA256}, got {actual}")
        with zipfile.ZipFile(jar) as archive:
            if 'modId="storagenetwork"' not in archive.read("META-INF/mods.toml").decode("utf-8"):
                raise AssertionError("Not the SSN mod")
        cls.disassembly = {}
        for key, name in CLASSES.items():
            p = subprocess.run(["javap", "-p", "-c", "-classpath", str(jar), name],
                               text=True, capture_output=True, check=True)
            cls.disassembly[key] = p.stdout

    def test_001_request_omits_parent_connectable_save(self):
        request = method_body(self.disassembly["request"], "public void m_183515_(")
        base = method_body(self.disassembly["base"], "public void m_183515_(")
        self.assertIn("String autoFocus", request)
        self.assertIn("String connectable", base)
        self.assertNotRegex(request, r"invokespecial.*m_183515_")

    def test_002_builder_uses_nullable_level_registry_lookup(self):
        method = method_body(self.disassembly["builder"], "public static net.minecraft.world.level.block.state.BlockState getBlockState(")
        self.assertIn("String blockstate", method)
        self.assertIn("Level.m_246945_", method)

    def test_003_original_duplicate_target_guard_must_be_preserved(self):
        network = method_body(self.disassembly["network"], "private java.util.Set<com.lothrazar.storagenetwork.api.IConnectableLink> getConnectableStorage(")
        ordered = method_body(self.disassembly["network"], "private java.util.List<com.lothrazar.storagenetwork.api.IConnectableLink> getSortedConnectableStorage(")
        self.assertIn("canonicalTargetKey", network)
        self.assertIn("java/util/Set.add", network)
        self.assertIn("getConnectableStorage", ordered)

    def test_004_builder_checks_then_places_then_extracts(self):
        builder = method_body(self.disassembly["builder"], "public net.minecraft.world.InteractionResult m_6225_(")
        requests = [int(n) for n in re.findall(r"(?m)^\s*(\d+):\s+invokevirtual\s+.*TileMain.request:", builder)]
        place = re.search(r"(?m)^\s*(\d+):\s+invokevirtual\s+.*placeStateSafe:", builder)
        self.assertEqual(len(requests), 2)
        self.assertIsNotNone(place)
        self.assertLess(requests[0], int(place.group(1)))
        self.assertLess(int(place.group(1)), requests[1])

    def test_005_shift_crafting_replaces_container_item_slots(self):
        craft = method_body(self.disassembly["craft"], "protected void craftShift(")
        self.assertIn("getCraftingRemainingItem", craft)
        self.assertIn("NetworkCraftingInventory.m_6836_", craft)
        self.assertIn("CraftingRecipe.m_7457_", craft)

    def test_006_one_provider_exception_aborts_getStacks(self):
        network = method_body(self.disassembly["network"], "public java.util.List<net.minecraft.world.item.ItemStack> getStacks(boolean")
        self.assertIn("Exception table:", network)
        self.assertIn("String 3rd party storage mod has an error", network)
        self.assertIn("Class java/lang/Exception", network)
        self.assertIn("getConnectableStorage", network)


if __name__ == "__main__":
    unittest.main()
