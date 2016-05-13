package appsInfoCrawler;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.google.common.base.Strings;
import org.openqa.selenium.*;
import server.DBWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static utils.SafariDriverUtils.*;

/**
 * Main class for crawling apps per category.
 */
public class MainCrawler {
    private static final int BATCH_SIZE = 50;
    private static final String DB_NAME = "appInfos";
    private static final String COLLLECTION_NAME = "infos";
    private static final Logger log = Logger.getLogger(MainCrawler.class.getName());

    private static final String playUrlMain = "https://play.google.com/store/apps";
    private static final String playUrlNew = "https://play.google.com/store/apps/new";
    private static final String playUrlTop = "https://play.google.com/store/apps/top";
    private static final String playUrlCategories = "https://play.google.com/store/apps/category/";
    private static final String[] categories = {"ANDROID_WEAR", "BOOKS_AND_REFERENCE", "BUSINESS", "COMICS",
            "COMMUNICATION", "EDUCATION", "ENTERTAINMENT", "FINANCE", "HEALTH_AND_FITNESS", "LIBRARIES_AND_DEMO",
            "LIFESTYLE", "APP_WALLPAPER", "MEDIA_AND_VIDEO", "MEDICAL", "MUSIC_AND_AUDIO", "NEWS_AND_MAGAZINES",
            "PERSONALIZATION", "PHOTOGRAPHY", "PRODUCTIVITY", "SHOPPING", "SOCIAL", "SPORTS", "TOOLS", "TRANSPORTATION",
            "TRAVEL_AND_LOCAL", "WEATHER", "APP_WIDGETS", "GAME", "FAMILY"};
    private static List<String> playLinks = new ArrayList<String>();

    static {
        playLinks.add(playUrlMain);
        playLinks.add(playUrlNew);
        playLinks.add(playUrlTop);
        for (String category : categories) {
            playLinks.add(playUrlCategories + category);
        }
    }

    private final ExtendedAppInfoCrawler extendedAppInfoCrawler;
    private final DBWriter dbWriter;
    private WebDriver driver;

    public MainCrawler() {
        extendedAppInfoCrawler = new ExtendedAppInfoCrawler();
        dbWriter = new DBWriter(DB_NAME, COLLLECTION_NAME);
        driver = createSafariDriver();
    }

    public void crawl() {
        String url = playUrlNew;
        System.out.println("Crawling url category: " + url);
        List<ExtendedAppInfo> appInfos = extendedAppInfoCrawler.crawlWithSeeMore(url);
        dbWriter.writeAppInfosToDb(appInfos, COLLLECTION_NAME);
        cleanup();
    }

    public void crawlAppUrls() {
        Set<String> moreLinks = findAllMoreLinks();
        for (String moreLink : moreLinks) {
            findAppLinks(moreLink);
        }
        driver.quit();
        cleanup();
    }

    private void findAppLinks(String moreLink) {
        goToUrl(driver, moreLink);
        sleep(2000);

        Set<String> appUrlsSet = new HashSet();
        while (true) {
            List<WebElement> webElementLinks = driver.findElements(By.className("title"));
            int oldSize = appUrlsSet.size();
            for (WebElement webElementLink : webElementLinks) {
                String href = webElementLink.getAttribute("href");
                if (!Strings.isNullOrEmpty(href)) {
                    appUrlsSet.add(webElementLink.getAttribute("href"));
                }
            }
            if (oldSize == appUrlsSet.size()) {
                if (tryClickingOnSeeMoreButton()) {
                    continue;
                }
                if (tryClickingOnFooterLink()) {
                    break;
                }
            }
            scrollPage(driver);
            sleep(2000);
        }
        List<String> appUrlsList = new ArrayList();
        appUrlsList.addAll(appUrlsSet);
        for (int i = 0; i < appUrlsList.size(); i += BATCH_SIZE) {
            int end = (i + BATCH_SIZE) < appUrlsList.size() ? (i + BATCH_SIZE) : appUrlsList.size();
            int linksWritten = dbWriter.writeAppLinksToDb(appUrlsList.subList(i, end));
            log.info("We have written " + linksWritten + " new links to the db.");
        }
    }

    private boolean tryClickingOnSeeMoreButton() {
        try {
            log.info("Trying to click on See More button.");
            WebElement seeMoreButton = driver.findElement(By.id("show-more-button"));
            String displayValue = seeMoreButton.getCssValue("display");
            if (!"none".equals(displayValue)) {
                scrollPage(driver, 300);
                log.info("See More button is shown, clicking on it.");
                sleep(2000);
                JavascriptExecutor executor = (JavascriptExecutor) driver;
                executor.executeScript("arguments[0].click();", seeMoreButton);
                sleep(2000);

                for (int i = 0; i < 3; i++) {
                    seeMoreButton = driver.findElement(By.id("show-more-button"));
                    displayValue = seeMoreButton.getCssValue("display");
                    if ("none".equals(displayValue)) {
                        return true;
                    }
                    executor.executeScript("arguments[0].click();", seeMoreButton);
                    sleep(2000);
                }
                return false;
            }
        } catch (ElementNotFoundException e) {
            log.info("See More button not found.");
        } catch (ElementNotVisibleException e) {
            log.info("See More button is not clickable.");
        }
        return false;
    }

    private boolean tryClickingOnFooterLink() {
        try {
            log.info("Trying to click on footer link.");
            WebElement footerLink = driver.findElement(By.className("footer-link"));
            footerLink.click();
            log.info("Could click on footer link, reached end of page.");
            return true;
        } catch (ElementNotFoundException e) {
            log.info("Could not find footer link.");
        } catch (ElementNotVisibleException e) {
            log.info("Cannot click on footer link yet.");
        } catch (WebDriverException e) {
            log.info("WebDriverException: " + e.getMessage());
        }
        return false;
    }

    private Set<String> findAllMoreLinks() {
        Set<String> moreLinks = new HashSet<String>();
        for (String playLink : playLinks) {
            moreLinks.addAll(findMoreLinks(playLink));
            // TODO(aci): add See More support here too
        }
        return moreLinks;
    }

    private Set<String> findMoreLinks(String url) {
        goToUrl(driver, url);
        Set<String> moreLinks = new HashSet();
        while (true) {
            moreLinks.addAll(collectMoreLinks());
            if (tryClickingOnFooterLink()) {
                break;
            }
            scrollPage(driver);
            sleep(2000);
        }
        return moreLinks;
    }

    private Set<String> collectMoreLinks() {
        Set<String> moreLinks = new HashSet();
        List<WebElement> seeMoreLinks = driver.findElements(By.className("see-more"));
        for (WebElement seeMoreLink : seeMoreLinks) {
            String href = seeMoreLink.getAttribute("href");
            if (!Strings.isNullOrEmpty(href)) {
                moreLinks.add(href);
            }
        }
        return moreLinks;
    }

    private void printResults(List<ExtendedAppInfo> appInfos) {
        System.out.println("--------------------------------------------");
        for (ExtendedAppInfo appInfo : appInfos) {
            System.out.println(appInfo);
            System.out.println();
        }
        System.out.println("--------------------------------------------");
    }

    public void cleanup() {
        extendedAppInfoCrawler.finish();
    }

    public static void main(String[] args) {
        MainCrawler mainCrawler = new MainCrawler();
        mainCrawler.crawlAppUrls();
    }
}
