package hooks;

import context.TestContext;
import factory.DriverFactory;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import utils.ConfigReader;
import utils.DriverManager;
import utils.LoggerUtility;
import utils.ScreenshotUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Scenario lifecycle: driver setup before, screenshot capture and teardown after.
 */
public class Hooks {

    private static final LoggerUtility LOG = LoggerUtility.forClass(Hooks.class);

    private final TestContext context;

    /**
     * @param context per-scenario context, injected by PicoContainer
     */
    public Hooks(TestContext context) {
        this.context = context;
    }

    /**
     * Loads configuration, launches the browser, sizes the window, sets the
     * implicit wait to zero and initialises the page objects.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void setUp(Scenario scenario) {
        LOG.scenarioStart(scenario.getName());
        LOG.info("Config    | env={} | browser={} | headless={} | baseUrl={}",
                ConfigReader.environment(),
                ConfigReader.browser(),
                ConfigReader.headless(),
                ConfigReader.baseUrl());

        createOutputDirectories();

        context.setScenario(scenario);

        // DriverFactory applies the window sizing and the zero implicit wait, and
        // binds the driver to this thread through DriverManager.
        WebDriver driver = DriverFactory.initDriver();
        context.setDriver(driver);
    }

    /**
     * Captures a screenshot on failure, attaches it to the report, quits the
     * browser and clears the ThreadLocal driver.
     *
     * <p>Teardown runs in a {@code finally} block so a failure while capturing
     * evidence can never leave a browser process running.
     *
     * @param scenario the finished scenario
     */
    @After(order = 0)
    public void tearDown(Scenario scenario) {
        try {
            if (scenario.isFailed() && ConfigReader.screenshotOnFailure()) {
                captureFailureEvidence(scenario);
            }
        } catch (RuntimeException e) {
            LOG.warn("Artifact capture failed during teardown: {}", e.getMessage());
        } finally {
            // Quits the driver and clears the ThreadLocal. Leaving the ThreadLocal
            // populated would leak this driver into the next scenario that runs on
            // this thread.
            DriverFactory.quitDriver();
            LOG.scenarioEnd(scenario.getName(), scenario.getStatus().name());
        }
    }

    /**
     * Writes a screenshot to disk and attaches it, plus the failing URL, to the
     * Cucumber report.
     *
     * @param scenario the failed scenario
     */
    private void captureFailureEvidence(Scenario scenario) {
        if (!DriverManager.hasDriver()) {
            return;
        }

        WebDriver driver = DriverManager.getDriver();

        byte[] screenshot = ScreenshotUtils.capture(driver, scenario.getName());
        if (screenshot != null) {
            scenario.attach(screenshot, "image/png", scenario.getName());
        }

        try {
            scenario.attach("URL at failure: " + driver.getCurrentUrl(), "text/plain", "page-url");
        } catch (RuntimeException e) {
            LOG.debug("Could not read the current URL during teardown: {}", e.getMessage());
        }

        LOG.failure("Scenario failed: " + scenario.getName(), null);
    }

    /**
     * Creates the reports and screenshots directories when they are missing.
     */
    private void createOutputDirectories() {
        try {
            Files.createDirectories(Paths.get(ConfigReader.reportsDir()));
            Files.createDirectories(Paths.get(ConfigReader.screenshotsDir()));
        } catch (IOException e) {
            LOG.warn("Could not create the output directories: {}", e.getMessage());
        }
    }
}
