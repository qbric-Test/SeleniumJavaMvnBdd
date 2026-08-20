package locators;

import org.openqa.selenium.By;

import java.util.List;

/**
 * Central locator repository for OLX Pakistan.
 *
 * <p><b>No XPath.</b> Every locator is a CSS selector built on stable attributes:
 * {@code href} patterns, {@code placeholder} text, {@code alt} text and ARIA
 * roles. OLX ships hashed CSS class names ({@code _520955ba}, {@code b5720141})
 * that change on every deploy, so class names are never used.
 *
 * <p>Where several markup variants exist, the element is exposed as an ordered
 * list of candidates and {@code WaitUtils.forFirstVisibleOf()} picks whichever
 * one the current variant renders.
 *
 * <p>Selecting by visible text — picking "Newly listed" out of the sort options —
 * is done by iterating the option elements in Java rather than with an XPath
 * text predicate. That keeps this file XPath-free and avoids brittle
 * text-in-selector coupling.
 */
public final class OlxLocators {

    private OlxLocators() {
        // Constants holder.
    }

    // ------------------------------------------------------------------
    // Home page: top categories
    // ------------------------------------------------------------------

    /**
     * The Mobiles category link.
     *
     * <p>OLX emits this href twice: once as a plain text link in a collapsed list
     * and once as the visible icon tile in the top categories strip. Both match,
     * so the page object asks for the first <em>displayed</em> one.
     */
    public static final By MOBILES_CATEGORY = By.cssSelector("a[href='/mobiles_c1411']");

    /**
     * The Mobiles tile identified by its category image, which is unambiguous
     * where the browser supports {@code :has()}.
     */
    public static final By MOBILES_CATEGORY_TILE =
            By.cssSelector("a[href='/mobiles_c1411']:has(img[alt='Mobiles'])");

    /**
     * Any top-category tile, used as a last-resort fallback.
     */
    public static final By CATEGORY_TILE_IMAGE = By.cssSelector("a img[alt='Mobiles']");

    /**
     * Ordered candidates for the Mobiles category entry point.
     *
     * @return candidates, most specific first
     */
    public static List<By> mobilesCategoryCandidates() {
        return List.of(MOBILES_CATEGORY_TILE, CATEGORY_TILE_IMAGE, MOBILES_CATEGORY);
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    /**
     * The country / location selector.
     *
     * <p>OLX renders this as a text input whose <em>value</em> is the selected
     * location, not as a {@code <select>}, so the selection is read with
     * {@code getAttribute("value")} rather than through the Select class.
     */
    public static final By COUNTRY_DROPDOWN = By.cssSelector("input[placeholder='Location']");

    /**
     * The same control, read for its selected value.
     */
    public static final By SELECTED_COUNTRY = COUNTRY_DROPDOWN;

    /**
     * The main search textbox next to the country selector.
     */
    public static final By SEARCH_TEXTBOX = By.cssSelector("input[placeholder^='Find Cars']");

    /**
     * Ordered candidates for the search textbox.
     *
     * @return candidates, most specific first
     */
    public static List<By> searchTextboxCandidates() {
        return List.of(
                SEARCH_TEXTBOX,
                By.cssSelector("input[placeholder*='Mobile Phones']"),
                By.cssSelector("form input[type='text']"));
    }

    // ------------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------------

    /**
     * The "Sort by" dropdown trigger.
     *
     * <p>Rendered as a button holding the current value plus a chevron image
     * whose {@code alt} text is stable across deploys, which makes it the most
     * reliable anchor available.
     */
    public static final By SORT_BY_DROPDOWN =
            By.cssSelector("button:has(img[alt='Sort options dropdown'])");

    /**
     * Fallback for the sort trigger: the chevron image itself, whose ancestor
     * button is what actually receives the click.
     */
    public static final By SORT_BY_DROPDOWN_ICON = By.cssSelector("img[alt='Sort options dropdown']");

    /**
     * Ordered candidates for the sort trigger.
     *
     * @return candidates, most specific first
     */
    public static List<By> sortByDropdownCandidates() {
        return List.of(SORT_BY_DROPDOWN, SORT_BY_DROPDOWN_ICON);
    }

    /**
     * The option list the sort dropdown reveals.
     */
    public static final By SORT_OPTIONS_LIST = By.cssSelector("ul[role='listbox']");

    /**
     * Every option inside the open sort dropdown. The page object picks the one
     * whose visible text matches, which is why no per-option locator exists.
     */
    public static final By SORT_OPTIONS = By.cssSelector("li[role='option']");

    /**
     * The option currently marked {@code aria-selected="true"}.
     */
    public static final By SELECTED_SORT_OPTION =
            By.cssSelector("li[role='option'][aria-selected='true']");

    /**
     * The "Newly listed" option, matched structurally where {@code :has()} is
     * supported. The text-matching path in the page object is the primary route;
     * this exists so the locator inventory is complete.
     */
    public static final By NEWLY_LISTED_OPTION = By.cssSelector("li[role='option']:first-child");

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /**
     * The container wrapping the advert grid.
     */
    public static final By LISTINGS_CONTAINER = By.cssSelector("ul:has(li[aria-label='Listing'])");

    /**
     * Individual advert cards.
     */
    public static final By LISTING_CARDS = By.cssSelector("li[aria-label='Listing']");

    /**
     * Links to individual adverts. OLX item URLs always carry an
     * {@code -iid-<id>} suffix, which makes this a reliable structural anchor.
     */
    public static final By LISTING_LINKS = By.cssSelector("a[href*='-iid-']");

    /**
     * Ordered candidates for the advert cards.
     *
     * <p>The {@code aria-label} variant comes first because the item anchors
     * attach before the card renders and have zero size until their image loads,
     * which would otherwise satisfy a naive presence check.
     *
     * @return candidates, most reliable first
     */
    public static List<By> listingCardCandidates() {
        return List.of(LISTING_CARDS, By.cssSelector("article"), LISTING_LINKS);
    }

    /**
     * Ordered candidates for the listings container.
     *
     * @return candidates, most specific first
     */
    public static List<By> listingsContainerCandidates() {
        return List.of(LISTINGS_CONTAINER, By.cssSelector("main"), By.cssSelector("[role='main']"));
    }

    /**
     * The main page heading.
     */
    public static final By PAGE_HEADING = By.cssSelector("h1");

    /**
     * The loading spinner shown while a sorted result set is re-fetched.
     */
    public static final By LOADING_SPINNER =
            By.cssSelector("[class*='loader' i], [class*='spinner' i], [role='progressbar']");

    // ------------------------------------------------------------------
    // Interstitials
    // ------------------------------------------------------------------

    /**
     * Cookie and consent banners.
     */
    public static final By COOKIE_ACCEPT_BUTTON =
            By.cssSelector("#onetrust-accept-btn-handler, button[data-aut-id='btnAcceptCookies']");

    /**
     * Close controls for login and promo overlays.
     */
    public static final By MODAL_CLOSE_BUTTON =
            By.cssSelector("button[aria-label='Close'], button[data-aut-id='closeButton']");
}
