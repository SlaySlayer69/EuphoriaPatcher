package mc.euphoria_patches.installer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // Set system properties first
        System.setProperty("apple.awt.application.appearance", "system");

        try {
            // First install FlatLaf - this must happen BEFORE any Swing components are created
            // Detect dark mode
            boolean dark = DarkModeDetector.isDarkMode();
            NewInstaller.dark = dark;

            // Install the appropriate look and feel
            if (dark) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }

            // Apply colors after LAF is set
            Color accentColor = dark ? new Color(199, 21, 133) : new Color(230, 24, 150);

            if (dark) {
                // Make background darker
                UIManager.put("Panel.background", new Color(24, 24, 30));
                UIManager.put("ComboBox.background", new Color(45, 45, 45));
                UIManager.put("TextField.background", new Color(45, 45, 45));
                UIManager.put("Button.background", new Color(50, 50, 50));
                // Set darker background for the entire window
                UIManager.put("@background", new Color(30, 30, 30));
            }

            UIManager.put("hyperlink.foreground", new Color(199, 21, 133));
            UIManager.put("Button.outlineColor", accentColor);
            UIManager.put("Button.focusedBorderColor", accentColor.darker());
            UIManager.put("Button.hoverBorderColor", accentColor);
            UIManager.put("Button.default.focusColor", new Color(199, 21, 133, 50));
            UIManager.put("OptionPane.buttonBackground", new Color(60, 60, 60));
            UIManager.put("OptionPane.background", dark ? new Color(30, 30, 30) : null);
            UIManager.put("OptionPane.messageForeground", dark ? Color.WHITE : null);
            UIManager.put("Button.default.background", accentColor);
            UIManager.put("Button.default.outlineColor", accentColor.darker());
            UIManager.put("Button.default.foreground", Color.WHITE);
            UIManager.put("Component.focusColor", accentColor);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Button.focusColor", accentColor);
            UIManager.put("OptionPane.buttonType", "roundRect");
            UIManager.put("Button.innerFocusWidth", 0);
            UIManager.put("Button.innerFocusColor", new Color(0, 0, 0, 0)); // Transparent
            UIManager.put("Button.hoverBorderColor", accentColor);
            UIManager.put("Button.focusedBorderColor", accentColor);
            UIManager.put("Button.default.borderColor", accentColor);
            UIManager.put("Button.default.focusColor", accentColor);
            UIManager.put("Button.default.focusedBorderColor", accentColor);
            UIManager.put("CheckBox.icon.checkmarkColor", accentColor);
            UIManager.put("CheckBox.icon.selectedBackground", dark ? new Color(60, 60, 60) : Color.WHITE);
            UIManager.put("CheckBox.icon.selectedBorderColor", accentColor);
            UIManager.put("CheckBox.icon.focusedBorderColor", accentColor);
            UIManager.put("CheckBox.icon.selectedFocusedBorderColor", accentColor);
            UIManager.put("CheckBox.icon.hoverBorderColor", accentColor);
            UIManager.put("ComboBox.selectionBackground", accentColor);
            UIManager.put("ComboBox.selectionForeground", Color.WHITE);

            // Now launch the UI when everything is ready
            System.out.println("Launching installer...");
            SwingUtilities.invokeLater(() -> {
                try {
                    new NewInstaller().setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                        "Error initializing application: " + e.getMessage(),
                        "Initialization Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (Exception e) {
            // Fall back to the system look and feel if FlatLaf fails
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                System.out.println("Failed to load FlatLaf, falling back to system L&F");
                e.printStackTrace();

                // Still try to launch the app
                SwingUtilities.invokeLater(() -> {
                    try {
                        new NewInstaller();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Fatal error: Cannot initialize application UI. " + ex.getMessage(),
                    "Fatal Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}