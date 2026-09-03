package com.euphoriapatches.euphoria_patcher.targets;

/**
 * Describes a base shaderpack that Euphoria Patches can be applied to.
 * <p>
 * Everything that used to be hardcoded to Complementary Shaders lives here, so that
 * additional base shaders (currently Photon) can be patched by the exact same pipeline.
 */
public final class ShaderTarget {

    private final String id;
    private final String displayName;
    private final String brandName;
    private final String baseVersion;
    private final String patchVersion;
    private final boolean hasStyles;
    private final String commonLocation;
    private final String markerFileLocation;
    private final String markerFileNamePart;
    private final String patchResourceSuffix;
    private final String baseTarSha256;
    private final long baseTarSize;
    private final String modrinthProjectId;
    private final String downloadUrl;
    private final String[] packagedRoots;

    /**
     * @param id                  stable identifier, used in logs and config
     * @param displayName         human readable name shown to the user
     * @param brandName           prefix every base shaderpack file name starts with
     * @param baseVersion         base shader version as it appears in the file name, e.g. {@code _r5.9}
     * @param patchVersion        Euphoria Patches version used for the patched folder name, e.g. {@code _1.10.0}
     * @param hasStyles           whether the base shader ships in a Reimagined/Unbound style pair
     * @param commonLocation      path of the file holding {@code SHADER_STYLE}, or {@code null} when the
     *                            target has no styles to normalise
     * @param markerFileLocation  file inside a patched pack whose first line carries the patch version
     * @param markerFileNamePart  substring identifying a patched pack's files, used for install verification
     * @param patchResourceSuffix suffix appended to the patch resource name, empty for the default target
     * @param baseTarSha256       SHA-256 of the normalised base TAR the bundled patch was built against
     * @param baseTarSize         size in bytes of that same TAR
     * @param modrinthProjectId   Modrinth project id or slug of the base shader
     * @param downloadUrl         where a user can obtain the base shader
     * @param packagedRoots       top level entries a released pack of this shader consists of, used
     *                            when building a patch from a source checkout; {@code null} means
     *                            "everything except the usual development files"
     */
    public ShaderTarget(String id, String displayName, String brandName, String baseVersion, String patchVersion,
                        boolean hasStyles, String commonLocation, String markerFileLocation,
                        String markerFileNamePart, String patchResourceSuffix, String baseTarSha256,
                        long baseTarSize, String modrinthProjectId, String downloadUrl,
                        String[] packagedRoots) {
        this.id = id;
        this.displayName = displayName;
        this.brandName = brandName;
        this.baseVersion = baseVersion;
        this.patchVersion = patchVersion;
        this.hasStyles = hasStyles;
        this.commonLocation = commonLocation;
        this.markerFileLocation = markerFileLocation;
        this.markerFileNamePart = markerFileNamePart;
        this.patchResourceSuffix = patchResourceSuffix;
        this.baseTarSha256 = baseTarSha256;
        this.baseTarSize = baseTarSize;
        this.modrinthProjectId = modrinthProjectId;
        this.downloadUrl = downloadUrl;
        this.packagedRoots = packagedRoots == null ? null : packagedRoots.clone();
    }

    /**
     * Top level entries a released pack of this shader consists of, or {@code null} when the whole
     * source tree (minus development files) is shipped.
     */
    public String[] getPackagedRoots() {
        return packagedRoots == null ? null : packagedRoots.clone();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public String getPatchVersion() {
        return patchVersion;
    }

    /**
     * Whether this base shader ships as a Reimagined/Unbound style pair. Targets without styles skip
     * all style detection, style normalisation and the "patch the other style too" logic.
     */
    public boolean hasStyles() {
        return hasStyles;
    }

    public String getCommonLocation() {
        return commonLocation;
    }

    public boolean hasCommonFile() {
        return commonLocation != null;
    }

    public String getMarkerFileLocation() {
        return markerFileLocation;
    }

    public String getMarkerFileNamePart() {
        return markerFileNamePart;
    }

    public String getBaseTarSha256() {
        return baseTarSha256;
    }

    public long getBaseTarSize() {
        return baseTarSize;
    }

    public String getModrinthProjectId() {
        return modrinthProjectId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Name of the patch file bundled in the mod's resources for this target.
     *
     * @param patchName the global patch brand, i.e. {@code EuphoriaPatches}
     */
    public String getPatchResourceName(String patchName) {
        return patchName + patchVersion + patchResourceSuffix + ".patch";
    }

    /**
     * Whether the patch for this target is actually present in the mod jar. Targets whose patch has
     * not been built yet are skipped silently instead of reporting a build error to the user.
     */
    public boolean isPatchBundled(String patchName) {
        String resourceName = getPatchResourceName(patchName);
        return ShaderTarget.class.getClassLoader().getResource(resourceName) != null;
    }

    /**
     * Expected file name of an unpatched base shaderpack.
     *
     * @param style {@code "Reimagined"} or {@code "Unbound"}; ignored for targets without styles
     */
    public String getBaseShaderName(String style) {
        return brandName + (hasStyles && style != null ? style : "") + baseVersion;
    }

    @Override
    public String toString() {
        return "ShaderTarget{" + id + " " + brandName + baseVersion + "}";
    }
}
