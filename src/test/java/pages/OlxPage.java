package pages;

import locators.OlxLocators;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import utils.ConfigReader;

import java.time.Duration;
import java.util.List;

/**
 * Page object for OLX Pakistan: home page, category listing pages and the sort
 * control they share.
 *
 * <p>Methods express business actions and return the observed state. Assertions
 * live in the step definitions, so a failure is reported against the Gherkin step
 * that caused it.
 */
public class OlxPage extends BasePage {

    private static final Duration SHORT_WAIT = Duration.ofSeconds(3);

    /**
     * @param driver the driver for this scenario
     */
    public OlxPage(WebDriver driver) {
        super(driver);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Opens the OLX Pakistan home page and clears any first-visit overlay.
     */
    public void openHomePage() {
        navigateTo(ConfigReader.baseUrl());
        dismissInterstitials();
    }

    /**
     * Clicks the Mobiles tile in the top categories strip and waits for the
     * category page.
     *
     * <p>The category link appears twice in the DOM — once hidden in a collapsed
     * list, once as the visible tile — so this asks for the first displayed
     * match rather than the first match.
     */
    public void clickMobilesCategory() {
        WebElement tile = wait.forFirstVisibleOf(
                OlxLocators.mobilesCategoryCandidates(), ConfigReader.timeout());

        scrollIntoView(tile);
        log.action("Click", "Mobiles category tile");
        clickElement(tile, "Mobiles category tile");

        // OLX category URLs carry a "_c<id>" suffix, e.g. /mobiles_c1411.
        try {
            wait.forUrlMatches(".*_c\\d+.*", ConfigReader.timeout());
        } catch (TimeoutException e) {
            log.debug("Category URL pattern not observed; verifying page content directly.");
        }

        waitForPageLoad();
        dismissInterstitials();
    }

    // ------------------------------------------------------------------
    // State the step definitions assert on
    // ------------------------------------------------------------------

    /**
     * Reports whether the Mobiles category page rendered.
     *
     * <p>Two signals are required: the advert grid is populated, and the header
     * search control is present. Together they rule out an error page or an empty
     * shell that still returns HTTP 200.
     *
     * @return {@code true} when the page loaded successfully
     */
    public boolean verifyMobilesPageLoaded() {
        waitForPageLoad();

        int cards = getListingCount();
        boolean searchPresent = wait.isAnyVisible(
                OlxLocators.searchTextboxCandidates(), ConfigReader.timeout());

        log.assertion("Mobiles page loaded (" + cards + " card(s), search=" + searchPresent + ")");
        return cards > 0 && searchPresent;
    }

    /**
     * Reports whether the document title contains a fragment.
     *
     * <p>Waits for the title rather than reading it once: the browser reports the
     * previous document's title for a short window after a client-side
     * navigation.
     *
     * @param expectedTitle expected substring
     * @return {@code true} when the title contains it
     */
    public boolean verifyTitleContains(String expectedTitle) {
        try {
            wait.forTitleContains(expectedTitle, ConfigReader.timeout());
        } catch (TimeoutException e) {
            log.assertion("Page title is '" + getPageTitle() + "' (expected to contain '"
                    + expectedTitle + "')");
            return false;
        }

        log.assertion("Page title is '" + getPageTitle() + "'");
        return true;
    }

    /**
     * Reports whether the country selector holds the expected country.
     *
     * <p>OLX renders this control as a text input rather than a native select, so
     * the "selected" country is its value.
     *
     * @param expectedCountry expected country, for example "Pakistan"
     * @return {@code true} when the value matches
     */
    public boolean verifyCountrySelected(String expectedCountry) {
        String actual = getSelectedCountry();
        log.assertion("Country dropdown holds '" + actual + "'");
        return expectedCountry.equalsIgnoreCase(actual);
    }

    /**
     * @return the country currently held by the location selector
     */
    public String getSelectedCountry() {
        // The value is populated by client-side script shortly after render, so
        // poll for a non-empty value rather than reading it once.
        try {
            wait.until(driver -> {
                String value = driver.findElement(OlxLocators.SELECTED_COUNTRY).getAttribute("value");
                return (value != null && !value.isBlank()) ? value : null;
            }, ConfigReader.timeout());
        } catch (TimeoutException e) {
            log.debug("Country selector stayed empty for the full timeout.");
        }

        return getValue(OlxLocators.SELECTED_COUNTRY);
    }

    /**
     * Reports whether the search textbox placeholder matches exactly.
     *
     * @param expectedPlaceholder expected placeholder text
     * @return {@code true} when it matches
     */
    public boolean verifySearchPlaceholder(String expectedPlaceholder) {
        String actual = getSearchPlaceholder();
        log.assertion("Search placeholder is '" + actual + "'");
        return expectedPlaceholder.equals(actual);
    }

    /**
     * @return the placeholder text of the search textbox
     */
    public String getSearchPlaceholder() {
        WebElement searchBox = wait.forFirstVisibleOf(
                OlxLocators.searchTextboxCandidates(), ConfigReader.timeout());
        String placeholder = searchBox.getAttribute("placeholder");
        return placeholder == null ? "" : placeholder;
    }

    // ------------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------------

    /**
     * Opens the Sort By dropdown and selects an option.
     *
     * @param option visible option label, for example "Newly listed"
     */
    public void selectSortByOption(String option) {
        selectFromCustomDropdown(
                OlxLocators.sortByDropdownCandidates(),
                OlxLocators.SORT_OPTIONS_LIST,
                OlxLocators.SORT_OPTIONS,
                option,
                "Sort By dropdown");

        // OLX reflects the choice in the query string, e.g. ?sorting=desc-creation.
        try {
            wait.forUrlContains("sorting=", ConfigReader.timeout());
        } catch (TimeoutException e) {
            log.debug("Sorting query parameter not observed; verifying the control directly.");
        }
    }

    /**
     * @return the label shown on the Sort By control, e.g. "Sort by: Newly listed"
     */
    public String getSelectedSortOption() {
        WebElement trigger = wait.forFirstVisibleOf(
                OlxLocators.sortByDropdownCandidates(), ConfigReader.timeout());
        return trigger.getText().trim();
    }

    /**
     * Reports whether the Sort By control reflects the chosen option.
     *
     * @param expectedOption expected option label
     * @return {@code true} when the control shows it
     */
    public boolean verifySortOptionSelected(String expectedOption) {
        String actual = getSelectedSortOption();
        log.assertion("Sort control shows '" + actual.replaceAll("\\s+", " ") + "'");
        return actual.toLowerCase().contains(expectedOption.toLowerCase());
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /**
     * Waits for the advert grid to finish refreshing after a sort change.
     *
     * <p>The spinner is transient and easy to miss between polls, so the
     * definitive signal is rendered advert cards rather than the disappearance of
     * a loading indicator. The spinner check is best-effort and never fails the
     * scenario.
     */
    public void waitForListingsRefresh() {
        log.info("Waiting for the listings to refresh");

        try {
            wait.forInvisible(OlxLocators.LOADING_SPINNER, SHORT_WAIT);
        } catch (TimeoutException e) {
            log.debug("Loading spinner still present after {}s; relying on the card count instead.",
                    SHORT_WAIT.toSeconds());
        }

        By cards = wait.resolveCollection(
                OlxLocators.listingCardCandidates(), ConfigReader.timeout());
        List<WebElement> rendered = wait.forCountAtLeast(cards, 1);

        waitForPageLoad();
        log.info("Listings refreshed: {} card(s) rendered", rendered.size());
    }

    /**
     * Reports whether the advert grid is populated.
     *
     * @return {@code true} when at least one advert card is displayed
     */
    public boolean verifyListingsDisplayed() {
        int count = getListingCount();
        log.assertion(count + " mobile listing(s) displayed");
        return count > 0;
    }

    /**
     * @return the number of advert cards currently rendered
     */
    public int getListingCount() {
        try {
            By cards = wait.resolveCollection(
                    OlxLocators.listingCardCandidates(), ConfigReader.timeout());
            return getCount(cards);
        } catch (TimeoutException e) {
            log.debug("No listings resolved: {}", e.getMessage());
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Closes cookie banners and promo overlays when they appear.
     *
     * <p>Deliberately non-fatal: none of these overlays is guaranteed to render,
     * so a missing one must never fail a scenario.
     */
    public void dismissInterstitials() {
        for (By locator : List.of(OlxLocators.COOKIE_ACCEPT_BUTTON, OlxLocators.MODAL_CLOSE_BUTTON)) {
            try {
                if (wait.isVisible(locator, Duration.ofSeconds(1))) {
                    clickElement(driver.findElement(locator), "overlay dismiss button");
                    log.debug("Dismissed an overlay");
                    return;
                }
            } catch (RuntimeException e) {
                // The overlay is optional; try the next candidate.
            }
        }
    }
}
