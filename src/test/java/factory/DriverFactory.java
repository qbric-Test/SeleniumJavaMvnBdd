package factory;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import utils.ConfigReader;
import utils.DriverManager;
import utils.LoggerUtility;

import java.util.List;
import java.util.Locale;

/**
 * Creates and configures WebDriver instances.
 *
 * <p>The browser is read from configuration, so switching engines needs no code
 * change:
 *
 * <pre>
 *   mvn test -Dbrowser=edge -Dheadless=true
 * </pre>
 *
 * <p>The created driver is bound to the current thread through
 * {@link DriverManager}, which is what allows scenarios to run in parallel.
 */
public final class DriverFactory {

    private static final LoggerUtility LOG = LoggerUtility.forClass(DriverFactory.class);

    private DriverFactory() {
        // Utility class.
    }

    /**
     * Creates a driver for the configured browser, applies timeouts and window
     * sizing, and binds it to the current thread.
     *
     * @return the created driver
     */
    public static WebDriver initDriver() {
        String browser = ConfigReader.browser();
        boolean headless = ConfigReader.headless();

        WebDriver driver = create(browser, headless);
        LOG.browserLaunched(browser, headless);

        configure(driver);
        DriverManager.setDriver(driver);

        return driver;
    }

    /**
     * Builds the driver for a named browser.
     *
     * @param browser  chrome, firefox or edge
     * @param headless whether to run without a visible window
     * @return the created driver
     * @throws IllegalArgumentException for an unsupported browser name
     */
    private static WebDriver create(String browser, boolean headless) {
        String engine = browser.toLowerCase(Locale.ROOT);
        resolveDriverBinary(engine);

        return switch (engine) {
            case "chrome" -> new ChromeDriver(chromeOptions(headless));
            case "firefox" -> new FirefoxDriver(firefoxOptions(headless));
            case "edge" -> new EdgeDriver(edgeOptions(headless));
            default -> throw new IllegalArgumentException(
                    "Unsupported browser: '" + browser + "'. Use chrome, firefox or edge.");
        };
    }

    /**
     * Resolves the driver binary for the chosen browser.
     *
     * <p>Two strategies are supported, selected by the {@code driverResolver}
     * config key:
     *
     * <ul>
     *   <li><b>selenium</b> (default) — do nothing here and let Selenium Manager,
     *       built into Selenium 4.6+, download and cache the driver. This is the
     *       supported path today and the only one that currently works for Edge:
     *       WebDriverManager still resolves msedgedriver from
     *       {@code msedgedriver.azureedge.net}, a CDN Microsoft has retired, so it
     *       fails with an UnknownHostException.</li>
     *   <li><b>webdrivermanager</b> — delegate to WebDriverManager, which is worth
     *       keeping for setups that need pinned driver versions, an internal
     *       mirror, or proxy support.</li>
     * </ul>
     *
     * @param engine the lower-cased browser name
     */
    private static void resolveDriverBinary(String engine) {
        String resolver = ConfigReader.get("driverResolver", "selenium").toLowerCase(Locale.ROOT);

        // An explicitly supplied binary always wins, whichever resolver is set.
        // Needed for air-gapped CI, pinned driver versions, and Edge — see below.
        String explicitPath = ConfigReader.get(driverPathKey(engine), "");
        if (!explicitPath.isBlank()) {
            System.setProperty(driverSystemProperty(engine), explicitPath);
            LOG.debug("Using the driver binary supplied at {}", explicitPath);
            return;
        }

        if (!"webdrivermanager".equals(resolver)) {
            LOG.debug("Driver binary resolution delegated to Selenium Manager");
            return;
        }

        LOG.debug("Resolving the driver binary with WebDriverManager");
        switch (engine) {
            case "chrome" -> WebDriverManager.chromedriver().setup();
            case "firefox" -> WebDriverManager.firefoxdriver().setup();
            case "edge" -> WebDriverManager.edgedriver().setup();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser: '" + engine + "'. Use chrome, firefox or edge.");
        }
    }

    /**
     * @param engine the lower-cased browser name
     * @return the config key holding an explicit driver binary path
     */
    private static String driverPathKey(String engine) {
        return switch (engine) {
            case "chrome" -> "chromeDriverPath";
            case "firefox" -> "geckoDriverPath";
            case "edge" -> "edgeDriverPath";
            default -> "driverPath";
        };
    }

    /**
     * @param engine the lower-cased browser name
     * @return the system property Selenium reads for a driver binary path
     */
    private static String driverSystemProperty(String engine) {
        return switch (engine) {
            case "chrome" -> "webdriver.chrome.driver";
            case "firefox" -> "webdriver.gecko.driver";
            case "edge" -> "webdriver.edge.driver";
            default -> "webdriver.driver";
        };
    }

    private static ChromeOptions chromeOptions(boolean headless) {
        ChromeOptions options = new ChromeOptions();

        if (headless) {
            // The modern headless mode; the legacy one behaves differently enough
            // to produce failures that do not reproduce headed.
            options.addArguments("--headless=new");
        }

        options.addArguments(commonArguments());
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        return options;
    }

    private static FirefoxOptions firefoxOptions(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();

        if (headless) {
            options.addArguments("-headless");
        }

        options.addPreference("dom.webnotifications.enabled", false);
        options.addPreference("dom.push.enabled", false);

        return options;
    }

    private static EdgeOptions edgeOptions(boolean headless) {
        EdgeOptions options = new EdgeOptions();

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments(commonArguments());
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        return options;
    }

    /**
     * Arguments shared by the Chromium-based browsers.
     *
     * <p>Note that {@code --start-maximized} is deliberately absent: window
     * sizing is applied through the WebDriver window API in
     * {@link #configure(WebDriver)}, and combining the two produces a
     * window/screen metric mismatch that bot detection flags.
     */
    private static List<String> commonArguments() {
        return List.of(
                "--disable-notifications",
                "--disable-popup-blocking",
                "--disable-infobars",
                "--remote-allow-origins=*");
    }

    /**
     * Applies timeouts and window sizing.
     *
     * <p>The implicit wait is set to zero on purpose. Mixing implicit and
     * explicit waits makes every {@code WebDriverWait} poll unpredictably and can
     * multiply timeouts, so the framework uses explicit waits exclusively.
     *
     * @param driver the driver to configure
     */
    private static void configure(WebDriver driver) {
        driver.manage().timeouts()
                .implicitlyWait(ConfigReader.implicitWait())
                .pageLoadTimeout(ConfigReader.pageLoadTimeout());

        if (ConfigReader.headless()) {
            // A headless window has no real screen to maximise against, so an
            // explicit size is the only way to get a predictable viewport.
            driver.manage().window().setSize(
                    new Dimension(ConfigReader.windowWidth(), ConfigReader.windowHeight()));
        } else if (ConfigReader.maximize()) {
            driver.manage().window().maximize();
        } else {
            driver.manage().window().setSize(
                    new Dimension(ConfigReader.windowWidth(), ConfigReader.windowHeight()));
        }

        LOG.debug("Driver configured (implicitWait={}s, pageLoadTimeout={}s)",
                ConfigReader.implicitWait().toSeconds(),
                ConfigReader.pageLoadTimeout().toSeconds());
    }

    /**
     * Quits the driver bound to the current thread and clears the ThreadLocal.
     *
     * <p>Guarded so a driver that already crashed cannot leave the ThreadLocal
     * populated, which would leak into the next scenario on this thread.
     */
    public static void quitDriver() {
        try {
            if (DriverManager.hasDriver()) {
                DriverManager.getDriver().quit();
                LOG.cleanup("WebDriver session closed");
            }
        } catch (RuntimeException e) {
            LOG.warn("Ignored failure while quitting the driver: {}", e.getMessage());
        } finally {
            DriverManager.unload();
        }
    }
}
