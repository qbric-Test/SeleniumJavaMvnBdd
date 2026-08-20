# SeleniumJavaBdd

Selenium WebDriver 4 + Cucumber BDD + JUnit 5 automation framework, built on the
Page Object Model with a ThreadLocal-managed driver per scenario.

## Stack

| Concern         | Tool                                         |
| --------------- | -------------------------------------------- |
| Language        | Java 17                                      |
| Build           | Maven                                        |
| Automation      | Selenium WebDriver 4                         |
| BDD runner      | Cucumber 7 on the JUnit 5 Platform           |
| Assertions      | JUnit 5 (`org.junit.jupiter.api.Assertions`) |
| DI / context    | Cucumber PicoContainer                       |
| Logging         | SLF4J + Logback                              |
| Utilities       | Apache Commons, Jackson                      |

## Setup

```bash
mvn clean install -DskipTests
```

Driver binaries are resolved automatically by Selenium Manager, so there is
nothing else to install for Chrome or Firefox. See the Edge note below.

## Run

```bash
mvn test
```

| Command                                              | What it does                    |
| ---------------------------------------------------- | ------------------------------- |
| `mvn test`                                            | Full suite, Chrome, headed      |
| `mvn test -Dheadless=true`                            | Headless                        |
| `mvn test -Dbrowser=firefox`                          | Firefox                         |
| `mvn test -Dbrowser=edge`                             | Edge (see the note below)       |
| `mvn test -Dcucumber.filter.tags="@Smoke"`            | Tagged scenarios only           |
| `mvn test -Dcucumber.execution.parallel.enabled=true` | Parallel scenarios              |

Every key in `config.properties` can be overridden with `-D`, because
`ConfigReader` checks system properties before the file.

## Layout

```
SeleniumJavaBdd
├── pom.xml
├── src/test/java
│   ├── factory/DriverFactory.java      # browser creation, options, cleanup
│   ├── pages/BasePage.java             # reusable interaction primitives
│   ├── pages/OlxPage.java              # business actions
│   ├── locators/OlxLocators.java       # centralised By locators, no XPath
│   ├── context/TestContext.java        # per-scenario shared state
│   ├── hooks/Hooks.java                # setup, screenshot on failure, teardown
│   ├── stepdefinitions/OlxSteps.java   # Gherkin bindings + JUnit assertions
│   ├── runners/TestRunner.java         # JUnit 5 Platform suite
│   └── utils/                          # ConfigReader, LoggerUtility, WaitUtils,
│                                       # ScreenshotUtils, DriverManager
├── src/test/resources
│   ├── features/olx.feature
│   ├── config/config.properties
│   ├── junit-platform.properties       # parallelism, strict mode
│   └── logback.xml
├── reports                             # HTML / JSON / JUnit XML
└── screenshots                         # failure screenshots
```

## Reports and failure artifacts

- HTML report: `reports/cucumber-report.html`
- JSON / JUnit XML: `reports/cucumber-report.json`, `reports/cucumber-junit.xml`
- Screenshots: `screenshots/` — written on failure and embedded in the HTML
  report, alongside the URL the failure happened on
- Execution log: `logs/execution.log`

## Design notes

**No `Thread.sleep()`, anywhere.** Selenium does not auto-wait, so every
interaction routes through `WaitUtils`, which builds `WebDriverWait` instances
with `ExpectedConditions`. The implicit wait is pinned to **zero** in
`config.properties`: mixing implicit and explicit waits makes every
`WebDriverWait` poll unpredictably and can multiply timeouts.

**Stale elements are tolerated by design.** Every wait ignores
`StaleElementReferenceException`. OLX re-renders its listing grid after a sort
change, which detaches elements mid-wait; without this the suite would fail
intermittently for reasons unrelated to the behaviour under test.

**No XPath.** Every locator is a CSS selector built on stable attributes —
`href` patterns, `placeholder` text, `alt` text, ARIA roles. OLX ships hashed
class names (`_520955ba`, `b5720141`) that change on every deploy, so class
names are never used. Selecting the "Newly listed" option by its visible text is
done by iterating the option elements in Java rather than with an XPath text
predicate.

**Duplicate-match handling.** OLX emits each category link twice — once hidden
in a collapsed list, once as the visible tile. `WaitUtils.forFirstDisplayed()`
and `forFirstVisibleOf()` return the first *displayed* match, not the first
match, which is what makes the click land on the real tile.

**Collection resolution.** `WaitUtils.resolveCollection()` prefers a candidate
with at least one visible element over one that merely matches elements. OLX
wraps each advert in a zero-size `<a>` that attaches before the card renders, so
a plain presence check locks onto something invisible.

**Click interception fallback.** `BasePage.clickElement()` catches
`ElementClickInterceptedException`, scrolls the element to the centre of the
viewport, and retries via JavaScript — a routine need on sites with sticky
headers.

**Parallel safety.** `DriverManager` holds the driver in a `ThreadLocal` and
`Hooks` calls `unload()` in a `finally` block. A ThreadLocal left populated on a
pooled thread leaks the driver into whatever scenario runs there next, which
produces failures that are very hard to trace.

## Verified behaviour

| Configuration                          | Result                    |
| -------------------------------------- | ------------------------- |
| Chrome, headed                          | 9/9 passed                |
| Chrome, headless (`--headless=new`)     | 9/9 passed                |
| Edge, headless, explicit driver binary  | 9/9 passed                |
| Deliberate failure                      | Screenshot written, embedded in the report, URL attached |

Firefox is implemented but was not verified: no Firefox installation was
available on the build machine.

## Note on Edge and driver resolution

Automatic Edge driver resolution is **broken upstream**, not in this framework.
Both WebDriverManager 5.9.2 and Selenium Manager (checked through Selenium
4.33.0) resolve `msedgedriver` from `msedgedriver.azureedge.net`, a CDN
Microsoft has retired — the host is now NXDOMAIN. The replacement,
`msedgedriver.microsoft.com`, is live but not yet used by either tool.

Until that is fixed upstream, supply the binary explicitly:

```bash
mvn test -Dbrowser=edge -DedgeDriverPath=C:\drivers\msedgedriver.exe
```

Download it from `https://msedgedriver.microsoft.com/<your-edge-version>/edgedriver_win64.zip`.

`config.properties` exposes three resolution strategies, and an explicit path
always wins over both:

| `driverResolver`   | Behaviour                                                 |
| ------------------ | --------------------------------------------------------- |
| `selenium`         | Selenium Manager, built into Selenium 4.6+ (**default**)  |
| `webdrivermanager` | WebDriverManager — useful for pinned versions, proxies, internal mirrors |
| *(any)* + `<x>DriverPath` | Use the supplied binary and skip auto-resolution   |

## Note on parallelism

`junit-platform.properties` ships with parallel execution **off**. The framework
is parallel-safe, but OLX is a live production site and driving several browsers
at it from one IP invites rate limiting. Enable it for your own environments:

```bash
mvn test -Dcucumber.execution.parallel.enabled=true
```

## Two things the site dictates

1. **The country control is not a `<select>`.** OLX renders it as a text input
   whose *value* is the location, so the check reads `getAttribute("value")`
   rather than using the `Select` class. The value is populated by client-side
   script shortly after render, so `OlxPage` polls for a non-empty value instead
   of reading it once.
2. **The Sort By control is not a `<select>` either.** It is a `<button>` plus a
   `role="listbox"`, which `Select` cannot drive. `BasePage` keeps
   `selectByVisibleText()` for real `<select>` elements and adds
   `selectFromCustomDropdown()` for this pattern.

## Adding a page

1. Add locators to `src/test/java/locators/<Name>Locators.java` as `By` constants.
2. Create `src/test/java/pages/<Name>Page.java` extending `BasePage`.
3. Expose it from `TestContext.setDriver(...)`.
4. Write the feature, then step definitions that delegate to the page object and
   assert on what it returns.
