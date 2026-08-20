package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Explicit-wait helpers built on {@link WebDriverWait} and
 * {@link ExpectedConditions}.
 *
 * <p>Selenium has no auto-waiting, so every interaction in this framework goes
 * through one of these methods. {@code Thread.sleep()} appears nowhere.
 *
 * <p>Each wait ignores {@link StaleElementReferenceException}: OLX re-renders its
 * listing grid after a sort change, which detaches elements mid-wait. Without
 * this the suite would fail intermittently for reasons unrelated to the
 * behaviour under test.
 */
public class WaitUtils {

    private final WebDriver driver;

    private final LoggerUtility log = LoggerUtility.forClass(WaitUtils.class);

    /**
     * @param driver driver these helpers operate on
     */
    public WaitUtils(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * Builds a wait with the default timeout.
     *
     * @return a configured wait
     */
    public WebDriverWait waiter() {
        return waiter(ConfigReader.timeout());
    }

    /**
     * Builds a wait with an explicit timeout.
     *
     * @param timeout how long to wait
     * @return a configured wait
     */
    public WebDriverWait waiter(Duration timeout) {
        WebDriverWait webDriverWait = new WebDriverWait(driver, timeout, ConfigReader.pollingInterval());
        webDriverWait.ignoring(StaleElementReferenceException.class);
        return webDriverWait;
    }

    // ------------------------------------------------------------------
    // Element waits
    // ------------------------------------------------------------------

    /**
     * Waits until an element is present and visible.
     *
     * @param locator target
     * @return the visible element
     */
    public WebElement forVisible(By locator) {
        return forVisible(locator, ConfigReader.timeout());
    }

    /**
     * Waits until an element is present and visible.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return the visible element
     */
    public WebElement forVisible(By locator, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Waits until an element is visible and enabled.
     *
     * @param locator target
     * @return the clickable element
     */
    public WebElement forClickable(By locator) {
        return forClickable(locator, ConfigReader.timeout());
    }

    /**
     * Waits until an element is visible and enabled.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return the clickable element
     */
    public WebElement forClickable(By locator, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Waits until an element is present in the DOM, visible or not.
     *
     * @param locator target
     * @return the located element
     */
    public WebElement forPresent(By locator) {
        return waiter().until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * Waits until an element is invisible or absent.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return {@code true} once the element is gone
     */
    public boolean forInvisible(By locator, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /**
     * Waits until at least the expected number of elements match.
     *
     * @param locator  target
     * @param expected minimum count
     * @return the matching elements
     */
    public List<WebElement> forCountAtLeast(By locator, int expected) {
        return waiter().until(driver -> {
            List<WebElement> elements = driver.findElements(locator);
            return elements.size() >= expected ? elements : null;
        });
    }

    // ------------------------------------------------------------------
    // Candidate fallback
    // ------------------------------------------------------------------

    /**
     * Returns the first element matching the locator that is actually displayed.
     *
     * <p>Needed because a single selector often matches both a rendered control
     * and a hidden duplicate — OLX, for instance, emits every category link twice:
     * once in a collapsed text list and once as the visible icon tile.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return the first displayed match
     */
    public WebElement forFirstDisplayed(By locator, Duration timeout) {
        return waiter(timeout).until(driver -> {
            for (WebElement element : driver.findElements(locator)) {
                try {
                    if (element.isDisplayed()) {
                        return element;
                    }
                } catch (StaleElementReferenceException ignored) {
                    // The DOM changed mid-scan; the next poll will retry.
                }
            }
            return null;
        });
    }

    /**
     * Returns the first element from an ordered list of candidate locators that
     * becomes visible.
     *
     * <p>This is the locator fallback strategy: production sites ship several
     * markup variants, so a page object supplies candidates in order of
     * preference and this picks whichever the current variant renders.
     *
     * @param candidates ordered candidates, most preferred first
     * @param timeout    how long to wait
     * @return the first visible element
     * @throws TimeoutException when none becomes visible in time
     */
    public WebElement forFirstVisibleOf(List<By> candidates, Duration timeout) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("forFirstVisibleOf() requires at least one candidate");
        }

        try {
            return waiter(timeout).until(driver -> {
                for (By candidate : candidates) {
                    for (WebElement element : driver.findElements(candidate)) {
                        try {
                            if (element.isDisplayed()) {
                                return element;
                            }
                        } catch (StaleElementReferenceException ignored) {
                            // Retry on the next poll.
                        }
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "None of the " + candidates.size() + " candidate locators became visible within "
                            + timeout.toSeconds() + "s: " + candidates, e);
        }
    }

    /**
     * Returns the candidate locator that resolves to a visible collection.
     *
     * <p>Prefers a candidate with at least one <em>visible</em> element over one
     * that merely matches elements. Card grids commonly attach a zero-size
     * wrapper before the card renders, so a plain count check can lock onto the
     * wrapper and then fail the visibility assertion that follows.
     *
     * @param candidates ordered candidates
     * @param timeout    how long to wait
     * @return the resolved locator
     */
    public By resolveCollection(List<By> candidates, Duration timeout) {
        try {
            return waiter(timeout).until(driver -> {
                for (By candidate : candidates) {
                    List<WebElement> elements = driver.findElements(candidate);
                    for (WebElement element : elements) {
                        try {
                            if (element.isDisplayed()) {
                                return candidate;
                            }
                        } catch (StaleElementReferenceException ignored) {
                            // Retry on the next poll.
                        }
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            log.debug("No candidate produced a visible element; falling back to a match count.");
            for (By candidate : candidates) {
                if (!driver.findElements(candidate).isEmpty()) {
                    return candidate;
                }
            }
            throw new TimeoutException(
                    "None of the " + candidates.size() + " candidate locators matched any element within "
                            + timeout.toSeconds() + "s: " + candidates, e);
        }
    }

    /**
     * Finds an element among those matching a locator whose visible text equals
     * the supplied value, ignoring case and surrounding whitespace.
     *
     * <p>Used instead of an XPath text predicate, which keeps the locator file
     * free of XPath.
     *
     * @param locator     the candidate set, for example all listbox options
     * @param visibleText the text to match
     * @param timeout     how long to wait
     * @return the matching element
     */
    public WebElement forElementWithText(By locator, String visibleText, Duration timeout) {
        try {
            return waiter(timeout).until(driver -> {
                for (WebElement element : driver.findElements(locator)) {
                    try {
                        if (element.isDisplayed()
                                && visibleText.equalsIgnoreCase(element.getText().trim())) {
                            return element;
                        }
                    } catch (StaleElementReferenceException ignored) {
                        // Retry on the next poll.
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            throw new TimeoutException("No visible element matching " + locator
                    + " had the text '" + visibleText + "' within " + timeout.toSeconds() + "s", e);
        }
    }

    // ------------------------------------------------------------------
    // Page-level waits
    // ------------------------------------------------------------------

    /**
     * Waits until {@code document.readyState} is "complete".
     */
    public void forPageLoad() {
        waiter(ConfigReader.pageLoadTimeout()).until(driver ->
                "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
    }

    /**
     * Waits until the URL contains a fragment.
     *
     * @param fragment expected substring
     * @param timeout  how long to wait
     * @return {@code true} once it matches
     */
    public boolean forUrlContains(String fragment, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.urlContains(fragment));
    }

    /**
     * Waits until the URL matches a regular expression.
     *
     * @param regex   pattern applied to the whole URL
     * @param timeout how long to wait
     * @return {@code true} once it matches
     */
    public boolean forUrlMatches(String regex, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.urlMatches(regex));
    }

    /**
     * Waits until the document title contains a fragment.
     *
     * @param fragment expected substring
     * @param timeout  how long to wait
     * @return {@code true} once it matches
     */
    public boolean forTitleContains(String fragment, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.titleContains(fragment));
    }

    /**
     * Waits for an arbitrary condition.
     *
     * @param condition the condition
     * @param timeout   how long to wait
     * @param <T>       result type
     * @return the satisfying value
     */
    public <T> T until(ExpectedCondition<T> condition, Duration timeout) {
        return waiter(timeout).until(condition);
    }

    // ------------------------------------------------------------------
    // Non-throwing probes
    // ------------------------------------------------------------------

    /**
     * Reports whether an element becomes visible, without throwing.
     *
     * @param locator target
     * @param timeout how long to wait
     * @return {@code true} when visible within the timeout
     */
    public boolean isVisible(By locator, Duration timeout) {
        try {
            forVisible(locator, timeout);
            return true;
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Reports whether any candidate becomes visible, without throwing.
     *
     * @param candidates ordered candidates
     * @param timeout    how long to wait
     * @return {@code true} when one is visible within the timeout
     */
    public boolean isAnyVisible(List<By> candidates, Duration timeout) {
        try {
            forFirstVisibleOf(candidates, timeout);
            return true;
        } catch (TimeoutException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Finds an element without waiting, returning empty when it is absent.
     *
     * @param locator target
     * @return the element, when present
     */
    public Optional<WebElement> findIfPresent(By locator) {
        List<WebElement> elements = driver.findElements(locator);
        return elements.isEmpty() ? Optional.empty() : Optional.of(elements.get(0));
    }
}
