package com.euphoriapatches.euphoria_patcher;

public class PatchInfo {
    public static final String VERSION = "_r5.9";
    public static final String PATCH_VERSION = "_1.10.0";
    public static final String BASE_TAR_SHA256 = "5e627575eb31710c502a4680860f4fd62cf74a6982c2194a9844395bfd3a2b5c";
    public static final int BASE_TAR_SIZE = 1643520;

    // Photon target. The base TAR values describe the deterministic archive produced from an
    // unmodified photon_v1.3b.zip; tools/build_patch.sh in the photon_patches repository prints
    // fresh values whenever PHOTON_VERSION is bumped.
    public static final String PHOTON_VERSION = "_v1.3b";
    public static final String PHOTON_BASE_TAR_SHA256 = "554a515e997e1200ef852e45edeb08530527ea0eb1e090dd096772153c703a43";
    public static final long PHOTON_BASE_TAR_SIZE = 5399040L;
}
