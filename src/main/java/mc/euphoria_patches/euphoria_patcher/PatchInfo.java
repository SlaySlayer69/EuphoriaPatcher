package mc.euphoria_patches.euphoria_patcher;

public class PatchInfo {
    private static final String VERSION = "_r5.6.1";
    private static final String PATCH_VERSION = "_1.7.7";

    private static final String BASE_TAR_SHA256 = "85bedae6a1fc8cac5f24cbcca18950f850dc7867c30e7db2bb0e15cb63729fe5";
    private static final int BASE_TAR_SIZE = 1440768;

    public static String getBaseVersion() {
        return VERSION;
    }
    public static String getPatchVersion() {
        return PATCH_VERSION;
    }
    public static String getBaseTarSha256() {
        return BASE_TAR_SHA256;
    }
    public static int getBaseTarSize() {
        return BASE_TAR_SIZE;
    }
}
