package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.util.EnumMap;

public class ModChecker {
	private static final String IRIS = ShaderLoader.IRIS;
	private static final String OCULUS = ShaderLoader.OCULUS;
	private static final String OPTIFINE = ShaderLoader.OPTIFINE;
	private static final String ANGELICA = ShaderLoader.ANGELICA;

	private static Class<?> cachedIrisClass = null;
	private static boolean irisClassSearched = false;
	private static String cachedIrisLikeLoader = null;

	private static final String MODERN_IRIS_CLASS = "net.irisshaders.iris.Iris";
	private static final String LEGACY_IRIS_CLASS = "net.coderbot.iris.Iris";

	@SuppressWarnings("SpellCheckingInspection")
	public enum ModNames {
		IRIS(Registers.irisLike(ModChecker.IRIS)),
		OCULUS(Registers.irisLike(ModChecker.OCULUS)),
		ANGELICA(Registers.irisLike(ModChecker.ANGELICA)),
		PHOTONICS(Registers.classes("at.redi2go.photonics.client.Photonics")),
		ASTROCRAFT(Registers.classes("mod.lwhrvw.astrocraft.Astrocraft")),
		OPTIFINE(Registers.classes("optifine.OptiFineTweaker")),
		FABRIC_SKYBOXES(Registers.classes("io.github.amerebagatelle.fabricskyboxes.SkyboxManager", "me.flashyreese.mods.nuit.SkyboxManager")),
		FORGE_SKYBOXES(Registers.classes("com.foopy.forgeskyboxes.SkyboxManager")),
		SKYBOXIFY(Registers.classes("btw.lowercase.skyboxify.SkyboxifyClient", "btw.lowercase.optiboxes.OptiBoxesClient")),
		STELLAR_VIEW(Registers.classes("net.povstalec.stellarview.StellarView")),
		CELESTIAL(Registers.classes("fishcute.celestial.Celestial")),
		HORIZON(Registers.classes("com.jeff.horizon.SkyboxManager")),
		SPYGLASS_ASTRONOMY(Registers.classes("com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient"));

		private final Registers register;

		ModNames(Registers register) {
			this.register = register;
		}

		boolean isPresent() {
			return this.register.isPresent();
		}
	}

	private static final EnumMap<ModNames, Boolean> modNameCache = new EnumMap<>(ModNames.class);

	private static final class Registers {
		private final String shaderLoaderId;
		private final String[] classNames;

		private Registers(String shaderLoaderId, String... classNames) {
			this.shaderLoaderId = shaderLoaderId;
			this.classNames = classNames;
		}

		static Registers classes(String... classNames) {
			return new Registers(null, classNames);
		}

		static Registers irisLike(String shaderLoaderId) {
			return new Registers(shaderLoaderId, ModChecker.MODERN_IRIS_CLASS, ModChecker.LEGACY_IRIS_CLASS);
		}

		boolean isPresent() {
			if (shaderLoaderId != null) {
				return shaderLoaderId.equals(getIrisLikeLoader());
			}
			return hasAnyClass(classNames);
		}
	}

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

	public static boolean isModPresent(ModNames modName) {
		Boolean cached = modNameCache.get(modName);
		if (cached != null) {
			debugLog("Cached mod found for " + modName + ": " + cached);
			return cached;
		}
		boolean present = modName.isPresent();
		modNameCache.put(modName, present);
		return present;
	}

	private static boolean hasAnyClass(String... classNames) {
		for (String className : classNames) {
			if (checkClassExists(className)) {
				return true;
			}
		}
		return false;
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

		if (isModPresent(ModNames.OPTIFINE)) {
			debugLog("Detected OPTIFINE via class check");
			return OPTIFINE;
		}

		debugLog("No shader loader detected via class checks");
		return null;
	}
}
