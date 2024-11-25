package util;


import com.intellij.openapi.diagnostic.Logger;

public class CodeSmellLogger {

    private static final Logger LOG = Logger.getInstance(CodeSmellLogger.class);

    public static void info(String message) {
        LOG.info("[Code Smell] " + message);
    }

    public static void warn(String message) {
        LOG.warn("[Code Smell] " + message);
    }

    public static void error(String message, Throwable throwable) {
        LOG.error("[Code Smell] " + message, throwable);
    }
}
