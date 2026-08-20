package utils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

/**
 * Singleton configuration reader.
 *
 * <p>Values are read from {@code src/test/resources/config/config.properties}. A
 * matching JVM system property always wins, so any value can be overridden on
 * the command line without editing the file:
 *
 * <pre>
 *   mvn test -Dbrowser=edge -Dheadless=true
 * </pre>
 *
 * <p>The properties file is parsed once per JVM and shared by every thread in a
 * parallel run.
 */
public final class ConfigReader {

    private static final String CONFIG_FILE = "config/config.properties";

    private static final Properties PROPERTIES = load();

    private ConfigReader() {
        // Utility class.
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input =
                     ConfigReader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new IllegalStateException(
                        "Configuration file not found on the classpath: " + CONFIG_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read configuration file: " + CONFIG_FILE, e);
        }
        return properties;
    }

    /**
     * Returns a required property. System properties take precedence.
     *
     * @param key property name
     * @return the trimmed value
     * @throws IllegalStateException when the key is absent or blank
     */
    public static String get(String key) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration value for key: " + key);
        }
        return value.trim();
    }

    /**
     * Returns a property, or the supplied default when it is absent.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static String get(String key, String defaultValue) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /**
     * Returns a property parsed as an int.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Configuration value for '" + key + "' is not a number: " + value, e);
        }
    }

    /**
     * Returns a property parsed as a boolean.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    // ------------------------------------------------------------------
    // Typed accessors used across the framework
    // ------------------------------------------------------------------

    /**
     * @return the base URL, without a trailing slash
     */
    public static String baseUrl() {
        String url = get("baseUrl");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String environment() {
        return get("environment", "qa");
    }

    public static String browser() {
        return get("browser", "chrome").toLowerCase(Locale.ROOT);
    }

    public static boolean headless() {
        return getBoolean("headless", false);
    }

    public static boolean maximize() {
        return getBoolean("maximize", true);
    }

    public static int windowWidth() {
        return getInt("windowWidth", 1920);
    }

    public static int windowHeight() {
        return getInt("windowHeight", 1080);
    }

    /**
     * @return the default explicit-wait timeout
     */
    public static Duration timeout() {
        return Duration.ofSeconds(getInt("timeout", 30));
    }

    /**
     * @return the page-load timeout
     */
    public static Duration pageLoadTimeout() {
        return Duration.ofSeconds(getInt("pageLoadTimeout", 60));
    }

    /**
     * @return how often a WebDriverWait re-evaluates its condition
     */
    public static Duration pollingInterval() {
        return Duration.ofMillis(getInt("pollingIntervalMillis", 250));
    }

    /**
     * @return the implicit wait, expected to be zero
     */
    public static Duration implicitWait() {
        return Duration.ofSeconds(getInt("implicitWait", 0));
    }

    public static boolean screenshotOnFailure() {
        return getBoolean("screenshotOnFailure", true);
    }

    public static String reportsDir() {
        return get("reportsDir", "reports");
    }

    public static String screenshotsDir() {
        return get("screenshotsDir", "screenshots");
    }
}
