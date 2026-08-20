package context;

import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import pages.OlxPage;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario state shared between hooks, step definitions and page objects.
 *
 * <p>PicoContainer creates one instance per scenario and injects that same
 * instance into every class declaring it as a constructor parameter. That is
 * what lets {@code Hooks} build the driver while {@code OlxSteps} uses it,
 * without static state — which, together with the ThreadLocal in
 * {@code DriverManager}, is what makes parallel execution safe.
 */
public class TestContext {

    private final Map<String, Object> scenarioData = new HashMap<>();

    private WebDriver driver;

    private Scenario scenario;

    private OlxPage olxPage;

    // ------------------------------------------------------------------
    // Driver
    // ------------------------------------------------------------------

    /**
     * @return the driver for this scenario
     * @throws IllegalStateException when the Before hook has not run
     */
    public WebDriver getDriver() {
        if (driver == null) {
            throw new IllegalStateException(
                    "No WebDriver in the test context: the Before hook has not initialised it yet.");
        }
        return driver;
    }

    /**
     * Stores the driver and builds the page objects bound to it.
     *
     * @param driver the driver created by the factory
     */
    public void setDriver(WebDriver driver) {
        this.driver = driver;
        this.olxPage = new OlxPage(driver);
    }

    // ------------------------------------------------------------------
    // Scenario
    // ------------------------------------------------------------------

    /**
     * @return the running scenario
     */
    public Scenario getScenario() {
        return scenario;
    }

    /**
     * @param scenario the running scenario, supplied by the Before hook
     */
    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    /**
     * @return the running scenario's name, or a placeholder before it is set
     */
    public String getScenarioName() {
        return scenario == null ? "scenario" : scenario.getName();
    }

    // ------------------------------------------------------------------
    // Page objects
    // ------------------------------------------------------------------

    /**
     * @return the OLX page object for this scenario
     * @throws IllegalStateException when the driver has not been created yet
     */
    public OlxPage getOlxPage() {
        if (olxPage == null) {
            throw new IllegalStateException(
                    "Page objects are not available: the Before hook has not created a driver yet.");
        }
        return olxPage;
    }

    // ------------------------------------------------------------------
    // Free-form data sharing between steps
    // ------------------------------------------------------------------

    /**
     * Stores a value for a later step in the same scenario.
     *
     * @param key   lookup key
     * @param value value to keep
     */
    public void set(String key, Object value) {
        scenarioData.put(key, value);
    }

    /**
     * Reads a value stored earlier in the same scenario.
     *
     * @param key  lookup key
     * @param type expected type
     * @param <T>  expected type
     * @return the stored value, or {@code null} when absent
     */
    public <T> T get(String key, Class<T> type) {
        return type.cast(scenarioData.get(key));
    }

    /**
     * Reads a value a previous step is required to have set.
     *
     * @param key  lookup key
     * @param type expected type
     * @param <T>  expected type
     * @return the stored value
     * @throws IllegalStateException when the key is absent
     */
    public <T> T require(String key, Class<T> type) {
        Object value = scenarioData.get(key);
        if (value == null) {
            throw new IllegalStateException("Scenario context is missing the required key: " + key);
        }
        return type.cast(value);
    }
}
