package utils;

import org.openqa.selenium.WebDriver;

/**
 * Holds the WebDriver for the current thread.
 *
 * <p>Cucumber runs each scenario on one thread, so a {@link ThreadLocal} gives
 * every scenario its own isolated driver. This is what makes parallel execution
 * safe: no static driver is ever shared between scenarios.
 *
 * <p>{@link #unload()} must be called in the After hook. A ThreadLocal left
 * populated on a pooled thread leaks the driver reference into whatever scenario
 * runs on that thread next, which produces failures that are very hard to trace.
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
        // Utility class.
    }

    /**
     * Binds a driver to the current thread.
     *
     * @param driver the driver for this scenario
     */
    public static void setDriver(WebDriver driver) {
        DRIVER.set(driver);
    }

    /**
     * Returns the driver bound to the current thread.
     *
     * @return the driver
     * @throws IllegalStateException when no driver has been set, which means the
     *                               Before hook did not run or already tore down
     */
    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No WebDriver bound to thread '" + Thread.currentThread().getName()
                            + "'. The Before hook must run before any step uses the driver.");
        }
        return driver;
    }

    /**
     * @return {@code true} when a driver is bound to the current thread
     */
    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    /**
     * Clears the driver reference for the current thread.
     */
    public static void unload() {
        DRIVER.remove();
    }
}
