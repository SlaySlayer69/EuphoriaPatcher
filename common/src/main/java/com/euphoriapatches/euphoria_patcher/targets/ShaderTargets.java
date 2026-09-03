package com.euphoriapatches.euphoria_patcher.targets;

import com.euphoriapatches.euphoria_patcher.PatchInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of every base shaderpack Euphoria Patches knows how to patch.
 */
public final class ShaderTargets {

    /**
     * Complementary Shaders - the original and default target. Ships as a Reimagined/Unbound style
     * pair which is normalised to a single base archive before patching.
     */
    public static final ShaderTarget COMPLEMENTARY = new ShaderTarget(
            "complementary",
            "Complementary Shaders",
            "Complementary",
            PatchInfo.VERSION,
            PatchInfo.PATCH_VERSION,
            true,
            "shaders/lib/common.glsl",
            "shaders/lib/misc/myFile.glsl",
            "EuphoriaPatches",
            "",
            PatchInfo.BASE_TAR_SHA256,
            PatchInfo.BASE_TAR_SIZE,
            "HVnmMxH1",
            "https://www.complementary.dev/manual-download/",
            null
    );

    /**
     * Photon. A single-style pack, so there is no {@code SHADER_STYLE} to normalise and no second
     * style to generate after patching. The marker file doubles as Photon's Euphoria integration
     * layer: its first line carries the patch version.
     */
    public static final ShaderTarget PHOTON = new ShaderTarget(
            "photon",
            "Photon",
            "photon",
            PatchInfo.PHOTON_VERSION,
            PatchInfo.PATCH_VERSION,
            false,
            null,
            "shaders/include/misc/euphoria_patches.glsl",
            "euphoria_patches",
            "_photon",
            PatchInfo.PHOTON_BASE_TAR_SHA256,
            PatchInfo.PHOTON_BASE_TAR_SIZE,
            "photon-shader",
            "https://modrinth.com/shader/photon-shader/versions",
            new String[]{"shaders", "LICENSE"}
    );

    private static final List<ShaderTarget> ALL =
            Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(COMPLEMENTARY, PHOTON)));

    private ShaderTargets() {}

    /**
     * Every registered target, in the order they should be processed.
     */
    public static List<ShaderTarget> all() {
        return ALL;
    }

    /**
     * The target assumed by code paths that predate multi-target support.
     */
    public static ShaderTarget defaultTarget() {
        return COMPLEMENTARY;
    }

    public static ShaderTarget byId(String id) {
        for (ShaderTarget target : ALL) {
            if (target.getId().equalsIgnoreCase(id)) {
                return target;
            }
        }
        return null;
    }
}
