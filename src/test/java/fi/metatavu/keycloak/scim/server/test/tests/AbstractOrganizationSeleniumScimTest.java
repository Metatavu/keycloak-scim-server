package fi.metatavu.keycloak.scim.server.test.tests;

import fi.metatavu.keycloak.scim.server.test.utils.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractOrganizationSeleniumScimTest extends AbstractOrganizationScimTest {

    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(20);

    @Container
    @SuppressWarnings({"try", "resource"})
    protected final BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
        .withNetwork(network)
        .withNetworkAliases("chrome")
        .withCapabilities(new ChromeOptions())
        .withRecordingMode(SeleniumUtils.getRecordingMode(), SeleniumUtils.getRecordingPath())
        .withExposedPorts(4444)
        .withEnv("SE_ENABLE_TRACING", "false")
        .withLogConsumer(outputFrame -> System.out.printf("CHROME: %s", outputFrame.getUtf8String()));

    /**
     * Logs in with external identity provider
     *
     * @param realm realm name to log in into
     * @param driver web driver
     * @param username username
     */
    @SuppressWarnings("SameParameterValue")
    protected void loginExternalIdp(RemoteWebDriver driver, String realm, String username, String password) {
        driver.get(getAccountUrl(realm));

        // We should land on the organization login page
        waitText(driver, By.id("kc-header-wrapper"), "organizations", false);

        // Type the username and click login
        waitInputAndType(driver, By.id("username"), username);
        waitButtonAndClick(driver, By.id("kc-login"));

        // We should be redirected to the external identity provider login page
        waitText(driver, By.id("kc-header-wrapper"), "external", false);

        // Type the username and password and click login
        waitInputAndType(driver, By.id("username"), username);
        waitInputAndType(driver, By.id("password"), password);
        waitButtonAndClick(driver, By.id("kc-login"));
        waitPresent(driver, By.id("username"), Duration.ofSeconds(10));
    }

    /**
     * Returns the URL for the account page
     *
     * @param realm realm name
     * @return URL for the account page
     */
    protected String getAccountUrl(String realm) {
        return String.format("http://scim-keycloak:8080/realms/%s/account", realm);
    }

    /**
     * Waits for an element to be clickable and clicks it.
     * <p>
     * If the element is not clickable within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     */
    protected void waitButtonAndClick(WebDriver driver, By by) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_DURATION);
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        element.click();
    }

    /**
     * Waits for an element to have a specific text.
     * <p>
     * If the element does not have the text within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     */
    @SuppressWarnings("SameParameterValue")
    protected void waitText(WebDriver driver, By by, String text, boolean caseSensitive) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_DURATION);

        if (caseSensitive) {
            wait.until(ExpectedConditions.textToBe(by, text));
        } else {
            wait.until(ExpectedConditions.textMatches(by, Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));
        }
    }

    /**
     * Waits for an element to be present.
     * <p>
     * If the element is not present within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     * @param duration maximum duration to wait
     */
    protected void waitPresent(WebDriver driver, By by, Duration duration) {
        WebDriverWait wait = new WebDriverWait(driver, duration);
        wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    /**
     * Waits for an element to be present.
     * <p>
     * If the element is not present within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     */
    @SuppressWarnings("unused")
    protected void waitPresent(WebDriver driver, By by) {
        waitPresent(driver, by, DEFAULT_DURATION);
    }

    /**
     * Waits for an element to be clickable and types text into it.
     * <p>
     * If the element is not clickable within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     * @param text text to type
     */
    protected void waitInputAndType(WebDriver driver, By by, String text) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_DURATION);
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        element.sendKeys(text);
    }

    /**
     * Waits for an input to have a specific value.
     * <p>
     * If the element is not clickable within default duration, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     * @param text text to type
     */
    protected void waitAndAssertInputValue(WebDriver driver, By by, String text) {
        WebDriverWait wait = new WebDriverWait(driver, DEFAULT_DURATION);
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        assertEquals(text, element.getAttribute("value"));
    }

    /**
     * Returns a By locator for a data-testid attribute
     *
     * @param dataTestId data-testid value
     * @return By locator
     */
    @SuppressWarnings("unused")
    protected By byDataTestId(String dataTestId) {
        return By.cssSelector("[data-testid='" + dataTestId + "']");
    }
}
