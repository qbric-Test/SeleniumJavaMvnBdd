package utils;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Captures screenshots and writes them under the configured screenshots
 * directory.
 *
 * <p>Returns the raw PNG bytes as well as writing the file, so the caller can
 * attach the same image to the Cucumber report without reading it back off disk.
 */
public final class ScreenshotUtils {

    private static final LoggerUtility LOG = LoggerUtility.forClass(ScreenshotUtils.class);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT);

    private ScreenshotUtils() {
        // Utility class.
    }

    /**
     * Captures a screenshot and writes it to the screenshots directory.
     *
     * @param driver       the driver to capture from
     * @param scenarioName name used to build the file name
     * @return the PNG bytes, or {@code null} when the capture failed
     */
    public static byte[] capture(WebDriver driver, String scenarioName) {
        if (driver == null) {
            return null;
        }

        try {
            byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path target = buildPath(scenarioName);

            Files.createDirectories(target.getParent());
            FileUtils.writeByteArrayToFile(target.toFile(), image);

            LOG.artifact("Screenshot", target.toAbsolutePath().toString());
            return image;
        } catch (IOException | RuntimeException e) {
            // A screenshot failure must never mask the real test failure.
            LOG.warn("Could not capture a screenshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Captures a screenshot as a {@link File}, for callers that need a path.
     *
     * @param driver       the driver to capture from
     * @param scenarioName name used to build the file name
     * @return the written file, or {@code null} when the capture failed
     */
    public static File captureAsFile(WebDriver driver, String scenarioName) {
        if (driver == null) {
            return null;
        }

        try {
            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File target = buildPath(scenarioName).toFile();

            FileUtils.copyFile(source, target);
            LOG.artifact("Screenshot", target.getAbsolutePath());

            return target;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not capture a screenshot: {}", e.getMessage());
            return null;
        }
    }

    private static Path buildPath(String scenarioName) {
        String fileName = slugify(scenarioName) + "_" + LocalDateTime.now().format(STAMP) + ".png";
        return Paths.get(ConfigReader.screenshotsDir(), fileName);
    }

    /**
     * Converts a scenario name into a file-system-safe slug.
     *
     * @param value the scenario name
     * @return the slug, capped at 80 characters
     */
    private static String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }
}
