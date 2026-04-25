package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

@SuppressWarnings("unused")
public class ModChecker {
	private static final String MODERN_IRIS_CLASS = "net.irisshaders.iris.Iris";
	private static final String LEGACY_IRIS_CLASS = "net.coderbot.iris.Iris";
	private static final String OPTIFINE_CLASS = "optifine.OptiFineTweaker";
    private static final String PHOTONICS_CLASS = "at.redi2go.photonics.client.Photonics";
	private static final String ASTROCRAFT_CLASS = "mod.lwhrvw.astrocraft.Astrocraft";

	private static final String IRIS = ShaderLoader.IRIS;
	private static final String OCULUS = ShaderLoader.OCULUS;
	private static final String OPTIFINE = ShaderLoader.OPTIFINE;
	private static final String ANGELICA = ShaderLoader.ANGELICA;

	private static Boolean cachedPhotonicsPresent = null;
	private static Boolean cachedAstrocraftPresent = null;
	private static Boolean cachedOptiFinePresent = null;
	private static Boolean cachedIrisPresent = null;
	private static Boolean cachedOculusPresent = null;
	private static Boolean cachedAngelicaPresent = null;
	private static Class<?> cachedIrisClass = null;
	private static boolean irisClassSearched = false;
	private static String cachedIrisLikeLoader = null;

	private static void debugLog(String message) {
		EuphoriaLogger.debugLog("[ModChecker] " + message);
	}

	/**
	 * Checks if a class exists in the current classpath.
	 *
	 * @param className The fully qualified class name to check
	 * @return true if the class exists, false otherwise
	 */
	public static boolean checkClassExists(String className) {
		try {
			String resourceName = className.replace('.', '/') + ".class";
			boolean exists = ModChecker.class.getClassLoader().getResource(resourceName) != null;
			debugLog("Class check for " + className + ": " + (exists ? "found" : "not found"));
			return exists;
		} catch (Exception e) {
			debugLog("Exception checking class " + className + ": " + e.getMessage());
			return false;
		}
	}

    public static boolean isPhotonicsPresent() {
        if (cachedPhotonicsPresent != null) {
            return cachedPhotonicsPresent;
        }

        cachedPhotonicsPresent = checkClassExists(PHOTONICS_CLASS);
        return cachedPhotonicsPresent;
    }

    public static boolean isAstrocraftPresent() {
        if (cachedAstrocraftPresent != null) {
            return cachedAstrocraftPresent;
        }

        cachedAstrocraftPresent = checkClassExists(ASTROCRAFT_CLASS);
        return cachedAstrocraftPresent;
    }

	public static boolean isOptiFinePresent() {
		if (cachedOptiFinePresent != null) {
			return cachedOptiFinePresent;
		}

		cachedOptiFinePresent = checkClassExists(OPTIFINE_CLASS);
		return cachedOptiFinePresent;
	}

	public static boolean isIrisPresent() {
		if (cachedIrisPresent != null) {
			return cachedIrisPresent;
		}

		cachedIrisPresent = IRIS.equals(getIrisLikeLoader());
		return cachedIrisPresent;
	}

	public static boolean isOculusPresent() {
		if (cachedOculusPresent != null) {
			return cachedOculusPresent;
		}

		cachedOculusPresent = OCULUS.equals(getIrisLikeLoader());
		return cachedOculusPresent;
	}

	public static boolean isAngelicaPresent() {
		if (cachedAngelicaPresent != null) {
			return cachedAngelicaPresent;
		}

		cachedAngelicaPresent = ANGELICA.equals(getIrisLikeLoader());
		return cachedAngelicaPresent;
	}

	/**
	 * Attempts to find the Iris/Oculus/Angelica class from known possible locations.
	 *
	 * @return The Iris class if found, null otherwise
	 */
	public static Class<?> findIrisClass() {
		if (irisClassSearched) {
			return cachedIrisClass;
		}

		irisClassSearched = true;

		if (checkClassExists(MODERN_IRIS_CLASS)) {
			try {
				cachedIrisClass = Class.forName(MODERN_IRIS_CLASS);
				return cachedIrisClass;
			} catch (ClassNotFoundException e) {
				debugLog("Class exists but couldn't load: " + MODERN_IRIS_CLASS);
			}
		}

		if (checkClassExists(LEGACY_IRIS_CLASS)) {
			try {
				cachedIrisClass = Class.forName(LEGACY_IRIS_CLASS);
				return cachedIrisClass;
			} catch (ClassNotFoundException e) {
				debugLog("Class exists but couldn't load: " + LEGACY_IRIS_CLASS);
			}
		}

		cachedIrisClass = null;
		return null;
	}

	private static String getModIdFromIrisClass(Class<?> classObj) {
		try {
			try {
				java.lang.reflect.Field modNameField = classObj.getField("MODNAME");
				Object modNameValue = modNameField.get(null);
				if (modNameValue instanceof String) {
					String modName = (String) modNameValue;
					debugLog("Found MODNAME field in " + classObj.getName() + ": " + modName);

					switch (modName) {
						case "Iris":
							debugLog("Detected Iris via MODNAME='Iris'");
							return IRIS;
						case "Oculus":
							debugLog("Detected Oculus via MODNAME='Oculus'");
							return OCULUS;
						case "AngelicaShaders":
							debugLog("Detected Angelica via MODNAME='AngelicaShaders'");
							return ANGELICA;
					}
				}
			} catch (NoSuchFieldException e) {
				debugLog("MODNAME field not found");
			}

		} catch (Exception e) {
			debugLog("Error reading fields from " + classObj.getName() + ": " + e.getMessage());
		}
		return null;
	}

	/**
	 * Gets the shader loader ID for an Iris-like class.
	 *
	 * @return The shader loader ID (iris/oculus/angelica), or null if not found
	 */
	private static String getIrisLikeLoader() {
		if (cachedIrisLikeLoader != null) {
			return cachedIrisLikeLoader;
		}

		Class<?> irisClass = findIrisClass();
		if (irisClass != null) {
			String modId = getModIdFromIrisClass(irisClass);
			if (IRIS.equals(modId)) {
				debugLog("Detected IRIS via class check and MODNAME field");
				cachedIrisLikeLoader = IRIS;
				return cachedIrisLikeLoader;
			} else if (OCULUS.equals(modId)) {
				debugLog("Detected OCULUS via class check and MODNAME field");
				cachedIrisLikeLoader = OCULUS;
				return cachedIrisLikeLoader;
			} else if (ANGELICA.equals(modId)) {
				debugLog("Detected ANGELICA via class check and MODNAME field");
				cachedIrisLikeLoader = ANGELICA;
				return cachedIrisLikeLoader;
			} else {
				debugLog("Found Iris-like class but MODNAME was: " + modId);
				debugLog("Assuming IRIS as default for Iris-like class");
				cachedIrisLikeLoader = IRIS;
				return cachedIrisLikeLoader;
			}
		}

		cachedIrisLikeLoader = null;
		return null;
	}

	/**
	 * Detects shader loader by checking for known classes.
	 *
	 * @return shader loader id (iris/oculus/angelica/optifine), or null if not detected
	 */
	public static String detectShaderLoaderByClass() {
		debugLog("Attempting shader loader detection via class checks");

		String irisLikeLoader = getIrisLikeLoader();
		if (irisLikeLoader != null) {
			return irisLikeLoader;
		}

		if (checkClassExists(OPTIFINE_CLASS)) {
			debugLog("Detected OPTIFINE via class check");
			return OPTIFINE;
		}

		debugLog("No shader loader detected via class checks");
		return null;
	}
}
