package com.panzer.mods.velox.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VeloxConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_ITEMS_PER_LEVEL;
    public static final ModConfigSpec.IntValue WINDOW_SECTIONS;
    public static final ModConfigSpec.IntValue SECTION_FILLS_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Simulate item entities with the Celeris physics engine. Read when the server starts:",
                        "a change takes effect after a restart.")
                .define("enabled", true);
        MAX_ITEMS_PER_LEVEL = b.comment("Items simulated per dimension; the rest keep vanilla physics.")
                .defineInRange("maxItemsPerLevel", 32768, 1024, 1 << 20);
        WINDOW_SECTIONS = b.comment("Width of the terrain snapshot around the items, in 16-block sections. Items outside",
                        "it fall back to vanilla physics; the window follows the bulk of the items.")
                .defineInRange("windowSections", 48, 8, 256);
        SECTION_FILLS_PER_TICK = b.comment("Chunk sections copied into the terrain snapshot per tick.")
                .defineInRange("sectionFillsPerTick", 64, 1, 4096);
        SPEC = b.build();
    }

    private VeloxConfig() {
    }
}
