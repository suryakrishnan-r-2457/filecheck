package com.dlp.discovery.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for setting process priority across different platforms.
 * Currently supports Windows via JNA.
 */
public class ProcessPriority {
    private static final Logger logger = LoggerFactory.getLogger(ProcessPriority.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean JNA_AVAILABLE;
    
    // JNA classes loaded reflectively to avoid hard dependency
    private static Class<?> kernel32Class;
    private static Class<?> winBaseClass;
    private static Object kernel32Instance;
    
    static {
        boolean jnaFound = false;
        try {
            // Try to load JNA classes
            kernel32Class = Class.forName("com.sun.jna.platform.win32.Kernel32");
            winBaseClass = Class.forName("com.sun.jna.platform.win32.WinBase");
            
            // Get Kernel32 INSTANCE
            kernel32Instance = kernel32Class.getField("INSTANCE").get(null);
            jnaFound = true;
            logger.debug("JNA library loaded successfully");
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            logger.info("JNA library not available, process priority adjustment will be disabled: {}", e.getMessage());
        } catch (NoClassDefFoundError e) {
            logger.info("JNA library not available (NoClassDefFoundError), process priority adjustment will be disabled");
        }
        JNA_AVAILABLE = jnaFound;
    }
    
    /**
     * Sets the current process priority to BELOW_NORMAL on Windows.
     * On non-Windows platforms or when JNA is not available, this method logs an info message and returns.
     * 
     * @return true if priority was successfully set, false otherwise
     */
    public static boolean setBelowNormal() {
        if (!IS_WINDOWS) {
            logger.info("Not running on Windows, skipping process priority adjustment");
            return false;
        }
        
        if (!JNA_AVAILABLE) {
            logger.info("JNA library not available, cannot set process priority");
            return false;
        }
        
        try {
            // Get BELOW_NORMAL_PRIORITY_CLASS constant (0x00004000)
            int belowNormalPriority = winBaseClass.getField("BELOW_NORMAL_PRIORITY_CLASS").getInt(null);
            
            // Get current process handle (-1 is the pseudo-handle for current process)
            java.lang.reflect.Method getCurrentProcessMethod = kernel32Class.getMethod("GetCurrentProcess");
            Object processHandle = getCurrentProcessMethod.invoke(kernel32Instance);
            
            // Set process priority
            java.lang.reflect.Method setPriorityMethod = kernel32Class.getMethod(
                "SetPriorityClass", 
                Class.forName("com.sun.jna.platform.win32.WinNT$HANDLE"),
                int.class
            );
            
            boolean success = (boolean) setPriorityMethod.invoke(kernel32Instance, processHandle, belowNormalPriority);
            
            if (success) {
                logger.info("Successfully set process priority to BELOW_NORMAL");
                return true;
            } else {
                // Get last error for debugging
                try {
                    java.lang.reflect.Method getLastErrorMethod = kernel32Class.getMethod("GetLastError");
                    int errorCode = (int) getLastErrorMethod.invoke(kernel32Instance);
                    logger.warn("Failed to set process priority to BELOW_NORMAL, error code: {}", errorCode);
                } catch (Exception e) {
                    logger.warn("Failed to set process priority to BELOW_NORMAL");
                }
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error setting process priority: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Checks if process priority adjustment is supported on this platform.
     * 
     * @return true if running on Windows with JNA available, false otherwise
     */
    public static boolean isSupported() {
        return IS_WINDOWS && JNA_AVAILABLE;
    }
}
