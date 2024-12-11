package util;


import com.intellij.openapi.diagnostic.Logger;

/**
 * Logger for code smells
 */
public class CodeSmellLogger {

    /**
     * The logger
     */
    private static final Logger LOG = Logger.getInstance(CodeSmellLogger.class);

    /**
     * Logs an info message
     *
     * @param message The message to log
     */
    public static void info(String message) {
        LOG.info("[Code Smell] " + message);
    }

    /**
     * Logs a warning message
     *
     * @param message The message to log
     */
    public static void warn(String message) {
        LOG.warn("[Code Smell] " + message);
    }

    /**
     * Logs an error message
     *
     * @param message The message to log
     * @param throwable The error to throw
     */
    public static void error(String message, Throwable throwable) {
        LOG.error("[Code Smell] " + message, throwable);
    }
}
