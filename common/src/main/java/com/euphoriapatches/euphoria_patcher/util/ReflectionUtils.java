package com.euphoriapatches.euphoria_patcher.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
     * Finds a method in the given class that matches the specified name and has a single parameter
     * that is assignable from the provided argument instance's class.
     * @param declaringClass
     * @param methodName
     * @param argInstance
     * @return
     * @throws NoSuchMethodException
     */
    public static Method findMethodForInstance(Class<?> declaringClass, String methodName, Object argInstance) throws NoSuchMethodException {
        for (Method method : declaringClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argInstance.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(declaringClass.getName() + "." + methodName + "(" + argInstance.getClass().getName() + ")");
    }

    /**
     * Tries each candidate no-arg method name in order on {@code declaringClass}, returning the
     * first one that resolves. Useful when the same method can appear under different names
     * depending on mapping/obfuscation state (e.g. named vs. intermediary vs. SRG), when you
     * already know the exact declaring class and just don't know which name it's using here.
     *
     * @param declaringClass the class to search
     * @param methodNames    candidate no-arg method names to try, in priority order
     * @return the first resolvable method
     * @throws NoSuchMethodException if none of the candidates resolve
     */
    public static Method tryMethods(Class<?> declaringClass, String... methodNames) throws NoSuchMethodException {
        NoSuchMethodException lastFailure = null;
        for (String methodName : methodNames) {
            try {
                return declaringClass.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                lastFailure = e;
            }
        }
        throw lastFailure != null ? lastFailure : new NoSuchMethodException(declaringClass.getName() + ": no candidate method names provided");
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
