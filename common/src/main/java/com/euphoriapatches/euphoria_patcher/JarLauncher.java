package com.euphoriapatches.euphoria_patcher;

/**
 * Stable launcher entrypoint for double-clicking the jar.
 * Kept separate from runtime startup so AWT classes are not touched during game init.
 */
public final class JarLauncher {

    public static void touch() {
        // Intentionally empty. Called from runtime startup to keep this class reachable for minimizers.
    }

    // This is the actual call for the GUI, kept separate to not touch AWT but called to prevent minimizer cleanup.
    public static void main(String[] args) {
        GUIScreen.launch();
    }
}
