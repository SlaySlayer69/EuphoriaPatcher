package com.euphoriapatches.euphoria_patcher.util;

import java.lang.reflect.Field;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

public class ReflectionUtils {

    private static void debugLog(String message) {
		EuphoriaLogger.debugLog("[ReflectionUtils] " + message);
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
            boolean exists = ReflectionUtils.class.getClassLoader().getResource(resourceName) != null;;
            debugLog("Class check for " + className + ": " + (exists ? "found" : "not found"));
            return exists;
        } catch (Exception e) {
            debugLog("Exception checking class " + className + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the value of a field from an object.
     *
     * @param target    The object from which to retrieve the field value
     * @param fieldName The name of the field to retrieve
     * @return The value of the field, or null if not found
     */
    public static Object getFieldValue(Object target, String fieldName) {
        Class<?> clazz;
        Object instance = target;

        try {
            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;

            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;

            } else {
                clazz = target.getClass();
            }

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    return field.get(instance);

                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }

        } catch (ClassNotFoundException e) {
            debugLog("Class not found: " + target);
            return null;
        } catch (Exception e) {
            debugLog("Error accessing field " + fieldName + ": " + e.getMessage());
            return null;
        }

        debugLog("Field " + fieldName + " not found in class hierarchy");
        return null;
    }
}
