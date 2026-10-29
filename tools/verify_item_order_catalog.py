"""Bounded source inventory audit; does not bootstrap Minecraft or replace runtime Core tests."""

import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/miaokatze/gtit"


def source(relative):
    text = (JAVA / relative).read_text(encoding="utf-8")
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


def audit():
    items = source("register/ItemRegistrar.java")
    shells = source("gift/GiftShellCatalog.java")
    machines = source("register/MultiblockMachineRegistrar.java")
    blocks = source("register/BlockRegistrar.java")
    catalog = source("register/GTITItemOrderCatalog.java")
    registered = re.findall(r"\b(\w+)\.setAndRegister\(", items + shells)
    registered += re.findall(r"GTITItemList\.(\w+)\s*\)", machines)
    registered += re.findall(r"GTITItemList\.(\w+)\.set\(", blocks)
    enum = source("common/api/enums/GTITItemList.java")
    enum_body = enum.split("implements IItemContainer {", 1)[1].split(";", 1)[0]
    declared = re.findall(r"\b\w+\b", enum_body)
    ordered = re.findall(r"GTITItemList\.(\w+)", catalog)
    assert len(registered) == len(set(registered)), "Duplicate registration container"
    assert set(registered) == set(declared), "Enum/registration inventory differs"
    assert len(ordered) == len(set(ordered)), "Duplicate domain entry"
    assert set(ordered) | {"StarterGift"} == set(registered), "Unclassified or extra domain entry"
    keys_body = source("gift/GiftIconCatalog.java").split("String[] KEYS =", 1)[1].split(";", 1)[0]
    keys = re.findall(r'"([a-z]+)"', keys_body)
    assert len(keys) == len(set(keys)) == 32, "Gift shell inventory changed; review complete order"
    assert "GiftIconCatalog.keys()" in shells and "GiftIconCatalog.keys()" in catalog
    assert '"starter".equals(key)' in shells and "StarterGift.setAndRegister" in shells
    # A new subtype producer requires an explicit expansion review instead of silently passing a meta-0 audit.
    variants = []
    for path in sorted((JAVA / "common/items").rglob("*.java")):
        text = source(path.relative_to(JAVA))
        if re.search(r"\bgetSubItems\s*\(|setHasSubtypes\s*\(\s*true\s*\)", text):
            variants.append(str(path.relative_to(ROOT)))
    assert not variants, f"New metadata-variant producer requires review: {variants}"
    assert "GameRegistry.registerItem(shadowMage, LEGACY_REGISTER_NAME_MAGE)" in items
    assert "hideShadowFromNEI(shadowMage)" in items
    return {
        "status": "PASS",
        "validation": "source registration coverage only; no Minecraft/client execution",
        "domain_entries": ordered,
        "gift_shells_in_existing_order": keys,
        "client_display_count": len(ordered) + len(keys),
        "dedicated_server_display_count": len(ordered) + len(keys) - 1,
        "metadata_variants": "none in ordinary items; machine stack uses registered MTE metadata including offset",
        "hidden_compatibility_item": "neko_pocket_upgrade_distill_fast",
        "non_item_features": ["terminal network/UI", "ascension carrier entity"],
    }


if __name__ == "__main__":
    print(json.dumps(audit(), ensure_ascii=False, indent=2))
