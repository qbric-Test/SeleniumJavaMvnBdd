package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import utils.ConfigReader;
import utils.LoggerUtility;
import utils.WaitUtils;

import java.time.Duration;
import java.util.List;

/**
 * Base class for every page object.
 *
 * <p>Owns the {@link WebDriver}, exposes the reusable interaction primitives, and
 * centralises logging so concrete page objects stay declarative.
 *
 * <p>Selenium does not auto-wait, so every method here waits explicitly before
 * touching the DOM. {@code Thread.sleep()} appears nowhere in this framework.
 */
public abstract class BasePage {

    protected final WebDriver driver;

    protected final WaitUtils wait;

    protected final LoggerUtility log;

    /**
     * @param driver the driver for this scenario
     */
    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WaitUtils(driver);
        this.log = LoggerUtility.forClass(getClass());
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Navigates to an absolute URL, or a path relative to the configured base URL.
     *
     * @param urlOrPath absolute URL or leading-slash path
     */
    public void navigateTo(String urlOrPath) {
        String target = urlOrPath.matches("(?i)^https?://.*")
                ? urlOrPath
                : ConfigReader.baseUrl() + (urlOrPath.startsWith("/") ? urlOrPath : "/" + urlOrPath);

        log.navigation(target);
        driver.get(target);
        waitForPageLoad();
    }

    /**
     * Waits until the document has finished loading.
     */
    public void waitForPageLoad() {
        wait.forPageLoad();
    }

    /**
     * @return the current page URL
     */
    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    /**
     * @return the document title
     */
    public String getPageTitle() {
        return driver.getTitle();
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    /**
     * Waits for an element to be clickable and clicks it.
     *
     * <p>Falls back to a JavaScript click when a sticky header or an overlay
     * intercepts the pointer event — a routine problem on commercial sites with
     * floating navigation bars.
     *
     * @param locator target
     */
    public void click(By locator) {
        click(locator, locator.toString());
    }

    /**
     * Waits for an element to be clickable and clicks it.
     *
     * @param locator     target
     * @param description human-readable name used in logs
     */
    public void click(By locator, String description) {
        log.action("Click", description);
        WebElement element = wait.forClickable(locator);
        clickElement(element, description);
    }

    /**
     * Clicks an already-resolved element, with the same interception fallback.
     *
     * @param element     target
     * @param description human-readable name used in logs
     */
    public void clickElement(WebElement element, String description) {
        try {
            element.click();
        } catch (ElementClickInterceptedException e) {
            log.debug("Click on {} was intercepted; retrying via JavaScript.", description);
            scrollIntoView(element);
            javascript().executeScript("arguments[0].click();", element);
        }
    }

    /**
     * Clears a field and types into it.
     *
     * @param locator target
     * @param text    text to enter
     */
    public void type(By locator, String text) {
        type(locator, text, locator.toString());
    }

    /**
     * Clears a field and types into it.
     *
     * @param locator     target
     * @param text        text to enter
     * @param description human-readable name used in logs
     */
    public void type(By locator, String text, String description) {
        log.action("Type '" + text + "' into", description);
        WebElement field = wait.forVisible(locator);
        field.clear();
        field.sendKeys(text);
    }

    /**
     * Selects an option from a native {@code <select>} by its visible text.
     *
     * @param locator the select element
     * @param value   the visible option text
     */
    public void selectByVisibleText(By locator, String value) {
        WebElement element = wait.forVisible(locator);
        new Select(element).selectByVisibleText(value);
        log.dropdownSelection(locator.toString(), value);
    }

    /**
     * Selects an option from a native {@code <select>} by its value attribute.
     *
     * @param locator the select element
     * @param value   the option value
     */
    public void selectByValue(By locator, String value) {
        WebElement element = wait.forVisible(locator);
        new Select(element).selectByValue(value);
        log.dropdownSelection(locator.toString(), value);
    }

    /**
     * Selects an option from a custom dropdown built from a button and a list.
     *
     * <p>OLX builds its sort control from a {@code <button>} and a
     * {@code role="listbox"} rather than a native {@code <select>}, so
     * {@link Select} cannot drive it. This clicks the trigger, waits for the list,
     * then clicks the option whose visible text matches.
     *
     * @param triggerCandidates ordered candidates for the control that opens the list
     * @param optionListLocator the option list
     * @param optionsLocator    every option within the list
     * @param optionText        the option to pick
     * @param description       human-readable name used in logs
     */
    public void selectFromCustomDropdown(List<By> triggerCandidates,
                                         By optionListLocator,
                                         By optionsLocator,
                                         String optionText,
                                         String description) {
        WebElement trigger = wait.forFirstVisibleOf(triggerCandidates, ConfigReader.timeout());
        log.action("Click", description);
        clickElement(trigger, description);

        wait.forVisible(optionListLocator);

        WebElement option = wait.forElementWithText(optionsLocator, optionText, ConfigReader.timeout());
        clickElement(option, "'" + optionText + "' option");

        log.dropdownSelection(description, optionText);
    }

    /**
     * Scrolls an element into the viewport.
     *
     * <p>Uses {@code block: 'center'} rather than the default, which would leave
     * the element flush against a sticky header and therefore unclickable.
     *
     * @param element target
     */
    public void scrollIntoView(WebElement element) {
        javascript().executeScript(
                "arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", element);
    }

    /**
     * Scrolls the element matching a locator into the viewport.
     *
     * @param locator target
     */
    public void scrollIntoView(By locator) {
        scrollIntoView(wait.forPresent(locator));
    }

    // ------------------------------------------------------------------
    // State queries
    // ------------------------------------------------------------------

    /**
     * @param locator target
     * @return the trimmed visible text
     */
    public String getText(By locator) {
        return wait.forVisible(locator).getText().trim();
    }

    /**
     * @param locator   target
     * @param attribute attribute name
     * @return the attribute value, or {@code null} when absent
     */
    public String getAttribute(By locator, String attribute) {
        return wait.forVisible(locator).getAttribute(attribute);
    }

    /**
     * Reads the value of an input.
     *
     * @param locator target
     * @return the trimmed value
     */
    public String getValue(By locator) {
        String value = wait.forVisible(locator).getAttribute("value");
        return value == null ? "" : value.trim();
    }

    /**
     * Reports whether an element is displayed. Never throws.
     *
     * @param locator target
     * @return {@code true} when it becomes visible within the default timeout
     */
    public boolean isDisplayed(By locator) {
        return isDisplayed(locator, ConfigReader.timeout());
    }

    /**
     * Reports whether an element is displayed. Never throws.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return {@code true} when it becomes visible within the timeout
     */
    public boolean isDisplayed(By locator, Duration timeout) {
        return wait.isVisible(locator, timeout);
    }

    /**
     * @param locator target
     * @return how many elements match
     */
    public int getCount(By locator) {
        return driver.findElements(locator).size();
    }

    /**
     * @param locator target
     * @return every matching element
     */
    public List<WebElement> findAll(By locator) {
        return driver.findElements(locator);
    }

    // ------------------------------------------------------------------
    // Waits
    // ------------------------------------------------------------------

    /**
     * Waits until an element is visible.
     *
     * @param locator target
     * @return the visible element
     */
    public WebElement waitForElementVisible(By locator) {
        return wait.forVisible(locator);
    }

    /**
     * Waits until an element is visible and enabled.
     *
     * @param locator target
     * @return the clickable element
     */
    public WebElement waitForElementClickable(By locator) {
        return wait.forClickable(locator);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * @return the driver cast to a JavaScript executor
     */
    protected JavascriptExecutor javascript() {
        return (JavascriptExecutor) driver;
    }
}
