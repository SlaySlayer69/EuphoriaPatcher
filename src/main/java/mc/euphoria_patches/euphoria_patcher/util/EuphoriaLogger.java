package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class EuphoriaLogger {
    public static Logger logger = LogManager.getLogger("euphoriaPatches");
    private static final String ERROR_LOG_FILE_NAME = "_0EUPHORIA_PATCHES_ERROR_LOGS.txt";
    private final Path errorLogFilePath = EuphoriaPatcher.shaderpacks.resolve(ERROR_LOG_FILE_NAME);
    private boolean isSodiumInstalled;
    private boolean shouldCreateErrorLog = true;
    
    // For error shader generation
    private List<String> errorMessages = new ArrayList<>();
    private int lastProcessedErrorCount = 0;
    private boolean hasErrors = false;
    private Timer errorShaderTimer = null;
    private boolean errorShaderScheduled = false;
    private static final int ERROR_COLLECTION_DELAY_MS = 5000; // 5 seconds
    private final Object errorCollectionLock = new Object();


    public EuphoriaLogger() {
        this.isSodiumInstalled = false;
    }
    
    /**
     * Checks if Sodium is available and sets up Sodium logging if it is
     */
    public void checkAndSetupSodiumLogging() {
        isSodiumInstalled = SodiumConsole.isSodiumAvailable();
        if (isSodiumInstalled) {
            debugLog("Sodium found, using Sodium logging!");
        }
    }

    /**
     * Main logging method with custom fade timer
     */
    public void log(int messageLevel, int messageFadeTimer, String message) {
        String loggingMessage = "EuphoriaPatcher: " + message;
        if (messageLevel == -1) loggingMessage = "\n\n" + loggingMessage + "\n";
        
        if (isSodiumInstalled && messageFadeTimer > 0) {
            SodiumConsole.logMessage(messageLevel, messageFadeTimer, loggingMessage);
        }
        
        switch (messageLevel) {
            case -1:
            case 0:
            case 1:
                logger.info(loggingMessage);
                break;
            case 2:
                logger.warn(loggingMessage);
                if(messageFadeTimer > 0) {
                    appendToErrorLogFile("[WARNING] " + loggingMessage);
                    collectErrorMessage(loggingMessage);
                }
                break;
            case 3:
                logger.error(loggingMessage);
                if(messageFadeTimer > 0) {
                    appendToErrorLogFile("[ERROR] " + loggingMessage);
                    collectErrorMessage(loggingMessage);
                }
                break;
            default:
                System.out.println(loggingMessage);
                break;
        }
        
        if (message.contains("Have fun developing Euphoria Patches!") || message.contains("Thank you for using Euphoria Patches - SpacEagle17")) {
            deleteErrorLogFile();
        }
    }

    /**
     * Simplified logging method that uses default fade timer
     */
    public void log(int messageLevel, String message) {
        // Map message levels to standard fade times
        int[] fadeTimers = {0, 4, 8, 16}; // Default, Info, Warning, Error
        int messageFadeTimer = messageLevel >= 0 && messageLevel < fadeTimers.length ?
                fadeTimers[messageLevel] : 0;
        log(messageLevel, messageFadeTimer, message);
    }

    /**
     * Collects error messages for the error shader with intelligent batching
     */
    private void collectErrorMessage(String message) {
        synchronized (errorCollectionLock) {
            errorMessages.add(message);
            hasErrors = true;
            
            // Debug to check message collection
            debugLog("Collected error #" + errorMessages.size() + ": " + message);
            debugLog("lastProcessedErrorCount = " + lastProcessedErrorCount);
            
            // Schedule error shader generation if this is the first error
            if (errorMessages.size() == 1) {
                debugLog("First error, scheduling initial generation");
                scheduleErrorShaderGeneration();
            } else if (errorMessages.size() >= 10 && errorMessages.size() - lastProcessedErrorCount >= 5) {
                // If we have 10+ errors with 5+ new ones, don't wait - generate immediately
                debugLog("Many errors collected, generating immediately");
                cancelScheduledErrorShader();
                ErrorShaderGenerator.generateErrorShader(errorMessages);
                lastProcessedErrorCount = errorMessages.size();
                // Reschedule to catch any new errors
                scheduleErrorShaderGeneration();
            } else if (!errorShaderScheduled && errorMessages.size() > lastProcessedErrorCount) {
                // CRITICAL FIX: If we have new errors but no timer is scheduled, schedule one
                debugLog("New errors detected and no timer active, scheduling update");
                scheduleErrorShaderGeneration();
            }
        }
    }
    
    /**
     * Schedules the error shader generation after a delay to collect multiple errors
     */
    private void scheduleErrorShaderGeneration() {
        synchronized (errorCollectionLock) {
            if (errorShaderScheduled) {
                debugLog("Timer already scheduled, skipping");
                return; // Already scheduled
            }
            
            // Cancel any existing timer
            cancelScheduledErrorShader();
            
            // Create new timer
            errorShaderTimer = new Timer("ErrorShaderTimer", true); // true = daemon thread
            errorShaderTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (errorCollectionLock) {
                        debugLog("Timer firing: messages=" + errorMessages.size() + 
                                 ", lastProcessed=" + lastProcessedErrorCount);
                        
                        if (hasErrors && errorMessages.size() > lastProcessedErrorCount) {
                            // Only regenerate if we have new errors
                            debugLog("Generating shader with " + errorMessages.size() + " messages");
                            ErrorShaderGenerator.generateErrorShader(errorMessages);
                            lastProcessedErrorCount = errorMessages.size();
                            
                            // We're done with this scheduled task
                            errorShaderScheduled = false;
                            
                            // IMPORTANT: Only reschedule if we expect more errors
                            if (errorMessages.size() % 5 == 0) { // Arbitrary check - reschedule periodically
                                debugLog("Rescheduling timer for potential future errors");
                                scheduleErrorShaderGeneration();
                            }
                        } else {
                            // Even if no new errors, we should keep checking periodically
                            debugLog("No new errors detected, rescheduling anyway");
                            errorShaderScheduled = false;
                            scheduleErrorShaderGeneration();
                        }
                    }
                }
            }, ERROR_COLLECTION_DELAY_MS);
            
            errorShaderScheduled = true;
            debugLog("Scheduled error shader generation in " + (ERROR_COLLECTION_DELAY_MS / 1000) + " seconds");
        }
    }
    
    /**
     * Cancels any scheduled error shader generation
     */
    private void cancelScheduledErrorShader() {
        if (errorShaderTimer != null) {
            errorShaderTimer.cancel();
            errorShaderTimer = null;
            errorShaderScheduled = false;
        }
    }

    /**
     * Checks if the error log file exists and adds a restart separator
     */
    public void checkErrorLogFileAndAddSeparator() {
        if (!shouldCreateErrorLog) return;
        try {
            if (Files.exists(errorLogFilePath)) {
                String separator = "\n--------------------------------------\n" +
                                    "Restart happened\n" +
                                    "--------------------------------------\n";
                Files.write(errorLogFilePath, separator.getBytes(),
                            java.nio.file.StandardOpenOption.APPEND);
                log(0, "Added restart separator to error log file");
            }
        } catch (IOException e) {
            log(0,"Failed to add restart separator to error log file: " + e.getMessage());
        }
    }

    /**
     * Appends a message to the error log file with timestamp
     */
    private void appendToErrorLogFile(String message) {
        if (!shouldCreateErrorLog) return;
        try {
            // Create parent directories if they don't exist
            if (!Files.exists(errorLogFilePath.getParent())) {
                Files.createDirectories(errorLogFilePath.getParent());
            }
            
            // Format with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String logEntry = timestamp + " " + message + System.lineSeparator();
            
            // Create or append to file
            java.nio.file.StandardOpenOption option = Files.exists(errorLogFilePath) 
                ? java.nio.file.StandardOpenOption.APPEND 
                : java.nio.file.StandardOpenOption.CREATE;
                
            Files.write(errorLogFilePath, logEntry.getBytes(), option);
        } catch (IOException e) {
            log(0,"Failed to write to error log file: " + e.getMessage());
        }
    }

    /**
     * Deletes the error log file when shader has been successfully installed
     */
    private void deleteErrorLogFile() {
        synchronized (errorCollectionLock) {
            try {
                if (Files.exists(errorLogFilePath)) {
                    Files.delete(errorLogFilePath);
                    log(0,"Deleted error log file as shader was successfully installed");
                    shouldCreateErrorLog = false;
                }
                
                // Also delete error shader if it exists
                Path errorShaderPath = EuphoriaPatcher.shaderpacks.resolve("_§c0EuphoriaPatches Error Shader");
                if (Files.exists(errorShaderPath)) {
                    FileUtils.deleteDirectory(errorShaderPath.toFile());
                    log(0, "Deleted error shader as installation was successful");
                }
                
                // Clear error messages and cancel any pending timer
                errorMessages.clear();
                hasErrors = false;
                lastProcessedErrorCount = 0;
                cancelScheduledErrorShader();
            } catch (IOException e) {
                log(0,"Failed to delete error log file: " + e.getMessage());
            }
        }
    }

    public static void debugLog(String message) {
        if (EuphoriaPatcher.doDebugLogging) {
            EuphoriaPatcher.log(0, message);
        }
    }
}