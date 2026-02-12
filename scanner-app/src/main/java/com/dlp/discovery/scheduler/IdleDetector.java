package com.dlp.discovery.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for detecting user idle time across different platforms.
 * Currently supports Windows via JNA.
 */
public class IdleDetector {
    private static final Logger logger = LoggerFactory.getLogger(IdleDetector.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean JNA_AVAILABLE;
    
    // JNA classes loaded reflectively to avoid hard dependency
    private static Class<?> kernel32Class;
    private static Object kernel32Instance;
    
    static {
        boolean jnaFound = false;
        try {
            // Try to load JNA classes
            kernel32Class = Class.forName("com.sun.jna.platform.win32.Kernel32");
            
            // Get Kernel32 INSTANCE
            kernel32Instance = kernel32Class.getField("INSTANCE").get(null);
            jnaFound = true;
            logger.debug("JNA library loaded successfully for idle detection");
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            logger.info("JNA library not available, idle detection will be disabled: {}", e.getMessage());
        } catch (NoClassDefFoundError e) {
            logger.info("JNA library not available (NoClassDefFoundError), idle detection will be disabled");
        }
        JNA_AVAILABLE = jnaFound;
    }
    
    /**
     * Checks if the user has been idle for at least the specified threshold.
     * On Windows, uses GetLastInputInfo to determine idle time.
     * On non-Windows platforms or when JNA is not available, returns false.
     * 
     * @param thresholdMinutes the idle time threshold in minutes
     * @return true if user has been idle for at least the threshold, false otherwise
     */
    public static boolean isIdle(int thresholdMinutes) {
        if (!IS_WINDOWS) {
            logger.debug("Not running on Windows, idle detection not supported");
            return false;
        }
        
        if (!JNA_AVAILABLE) {
            logger.debug("JNA library not available, cannot detect idle time");
            return false;
        }
        
        try {
            long idleTimeMillis = getIdleTimeMillis();
            long thresholdMillis = thresholdMinutes * 60L * 1000L;
            boolean idle = idleTimeMillis >= thresholdMillis;
            
            logger.debug("User idle time: {} ms, threshold: {} ms, isIdle: {}", 
                idleTimeMillis, thresholdMillis, idle);
            
            return idle;
        } catch (Exception e) {
            logger.error("Error detecting idle time: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the user's idle time in milliseconds using GetLastInputInfo on Windows.
     * 
     * @return idle time in milliseconds, or 0 if unable to determine
     */
    private static long getIdleTimeMillis() throws Exception {
        // Create LASTINPUTINFO structure using reflection
        Class<?> lastInputInfoClass = Class.forName("com.sun.jna.platform.win32.WinUser$LASTINPUTINFO");
        Object lastInputInfo = lastInputInfoClass.getDeclaredConstructor().newInstance();
        
        // Get the current tick count
        java.lang.reflect.Method getTickCountMethod = kernel32Class.getMethod("GetTickCount");
        int tickCount = (int) getTickCountMethod.invoke(kernel32Instance);
        
        // Call GetLastInputInfo
        java.lang.reflect.Method getLastInputInfoMethod = kernel32Class.getMethod(
            "GetLastInputInfo", 
            lastInputInfoClass
        );
        
        boolean success = (boolean) getLastInputInfoMethod.invoke(kernel32Instance, lastInputInfo);
        
        if (!success) {
            logger.warn("GetLastInputInfo failed");
            return 0;
        }
        
        // Get dwTime field from LASTINPUTINFO structure
        java.lang.reflect.Field dwTimeField = lastInputInfoClass.getField("dwTime");
        int lastInputTime = dwTimeField.getInt(lastInputInfo);
        
        // Calculate idle time (tick count wraps around, so handle overflow)
        long idleTime = tickCount - lastInputTime;
        
        // Handle tick count overflow (wraps at ~49.7 days)
        if (idleTime < 0) {
            idleTime += 0x100000000L;
        }
        
        return idleTime;
    }
    
    /**
     * Checks if idle detection is supported on this platform.
     * 
     * @return true if running on Windows with JNA available, false otherwise
     */
    public static boolean isSupported() {
        return IS_WINDOWS && JNA_AVAILABLE;
    }
}
