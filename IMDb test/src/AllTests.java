import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import static org.junit.Assert.assertEquals;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;


@TestMethodOrder(OrderAnnotation.class)
public class AllTests {
	static WebDriver driver;
	static String[] tvSeriesArr = null;
	static float ratingValue = 0.0f;
	
	@BeforeAll
	/*
	 * Initialize the web driver and go to the IMDb home page
	 */
	public static void initDriver() {
		System.setProperty("webdriver.chrome.driver",".\\Driver\\chromedriver.exe");//Setting the driver executable
		driver = new ChromeDriver();//Maximize window
		driver.manage().window().maximize();
		driver.get("https://www.imdb.com");//Open browser with desired URL
	}
	
	@Test
	@Order(1)
	/*
	 * Loads properties file and parses it -
	 * Creates an array of Strings (TV Series) 
	 * Also validates the rating is a valid number between 1 and 10
	 */
	public void handlePropertiesFile() {		
		try (InputStream input = new FileInputStream(".\\config.properties")) {
            Properties prop = new Properties();    
            prop.load(input);
            //Parse the rating into a float
            String ratingStr = (prop.getProperty("ratings").replaceAll("^\"|\"$", "")).trim();
            try {
            	float rating = Float.parseFloat(ratingStr);
            	if (rating < 1 || rating > 10) {
            		System.out.println("Rating value provided in properties file is not in the range 1-10, terminating");
            		System.exit(1);
            	}
            	//Parse the TV Series into an array of strings
                String tvShows = prop.getProperty("tv"); //get a string of the format "tvshow1","tvshow2",...
                String delims = ",";
                tvSeriesArr = tvShows.split(delims);
                for (int i = 0; i < tvSeriesArr.length; i++) {
                	tvSeriesArr[i] = tvSeriesArr[i].toLowerCase().replaceAll("^\"|\"$", "").trim();
                }         
            }catch(NumberFormatException e){
            	 System.out.println("Rating value provided in properties file is not a valid Float, terminating");
            	 System.exit(1);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}

	@Test
	@Order(2)
	/*
	 * Checks whether user is signed in
	 * Otherwise - signs in with an IMDb account
	 */
	public void register() {
		if (driver.findElements(By.linkText("Sign in")).size() != 0) { //the user is not logged in 
			String userName = "ella.steinberg93@gmail.com";
			String password = "!Qaz2wsx";
			driver.findElement(By.linkText("Sign in")).click();
			driver.findElement(By.className("imdb-logo")).click();
			driver.findElement(By.name("email")).sendKeys(userName);
			driver.findElement(By.name("password")).clear();
			driver.findElement(By.name("password")).sendKeys(password);
			driver.findElement(By.id("signInSubmit")).click();
		} 
		//otherwise - user is logged in - nothing to be done at this point	
	}

	@Test
	@Order(3)
	/*
	 * Go over the list of tv shows and add the shows with rating >= ratingValue to watching list
	 */
	public void addToWatchlist() {
		for(int i = 0; i < tvSeriesArr.length; i++) {
			//enter the name from the array in the main search bar
			driver.findElement(By.id("navbar-query")).sendKeys(tvSeriesArr[i]);
			driver.findElement(By.className("magnifyingglass")).click();
			//find the number of expected results - so we'll know when to stop
			int iCount = driver.findElements(By.xpath("//div/table/tbody//*[@class='result_text']")).size();			
			//we only want to click on a TV Series - sometimes there are movies/tv episodes with the same name
			//we look for the listing of the relevant name with the text "TV Series" or "TV Mini-Series" in brackets
			String str = "";
			boolean found = false;
			int index = 1;
			while (!found && index <= iCount){
				String xpathExpression = "(//div/table/tbody//*[@class='result_text'])[" + Integer.toString(index) + "]";
				str = driver.findElement(By.xpath(xpathExpression)).getText().toLowerCase();
				if (str.contains((tvSeriesArr[i])) && (str.contains(("TV Series").toLowerCase()) ||
						str.contains(("TV Mini-Series").toLowerCase()))) {
					found = true;
					String xpathExpressionLink = "(//div/table/tbody//*[@class='result_text']/a)[" + Integer.toString(index) + "]";
					driver.findElement(By.xpath(xpathExpressionLink)).click();
				}
				index++;
			}
			//this means that the string in the .properties file does not represent a TV Series in the IMDb DB
			if (!found) {
				System.out.println(tvSeriesArr[i] + "couldn't be found on IMDb");
				tvSeriesArr[i] = ""; //marking the array so we don't check it later for this string
				continue;
			}
			//check if the rating value is >= the rating value from the provided file
			//if it is - add the show to the watchlist
			String currRating = driver.findElement(By.className("rating")).getText();
			String delim = "/";
			String[] ratings = currRating.split(delim);
			if (Float.parseFloat(ratings[0]) >= ratingValue) {
				WebDriverWait wait = new WebDriverWait(driver, 20);
				wait.until(ExpectedConditions.elementToBeClickable(By.className("wl-ribbon")));
				//check if the show is not in watchlist
				if (driver.findElements(By.className("inWL")).size() == 0) {
					driver.findElement(By.className("not-inWL")).click(); //add to watchlist
				}
			} 
			else {
				tvSeriesArr[i] = ""; //marking the array so we don't check it later for this string
			}
		}
	}
	
	@Test
	@Order(4)
	/*
	 * Go over the watchlist and verify that the tv shows we added to the watchlist
	 * are indeed in it
	 */
	public void verifyWatchList() {
		//go to watchlist
		driver.findElement(By.linkText("Watchlist")).click();
		//this will be used to know how many times to click 'load more' during the test
		String titleCount = driver.findElement(By.className("lister-details")).getText();
		String delims = " Titles";
		String[] countStrArr = titleCount.split(delims);
		float watchlistCount = Float.parseFloat(countStrArr[0]);
		int numElementsInArr = (int) Math.ceil(watchlistCount/60);
		boolean[] loadMore = new boolean[numElementsInArr];
		Arrays.fill(loadMore, Boolean.FALSE);
		int loadMoreInd = 0;	
		//sort by added date - for efficiency
		Select sortBy = new Select(driver.findElement(By.id("lister-sort-by-options")));
		sortBy.selectByVisibleText("Date Added");
		//we wait for the page to be completely loaded with the "Date Added" sorting
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//for every tv show in our array - 
		//go over the watchlist and look for it - stop when found
		for(int i = 0; i < tvSeriesArr.length; i++) {			
			if (tvSeriesArr[i].isEmpty()) { //this means the show rating was lower or the string is not of a tv show
				continue;
			}
			//look for the tv show in the watchlist
			boolean found = false;
			int index = 1;
			while (!found && index <= watchlistCount) {
				//every 60 entries we need to click 'load more'
				if (index % 60 == 1 && index >= 61) {
					int indexInLoadArr = index/60 - 1;
					for (int j = 0; j <= indexInLoadArr; j++) {
						if (!loadMore[j]) {
							driver.findElement(By.className("load-more")).click();
							loadMore[j] = true;
						}
					}
					loadMoreInd = indexInLoadArr;
				}
				String xpathExpression = "(//div//*[@class='lister-item-header']/a)[" + Integer.toString(index) + "]";
				WebDriverWait waity = new WebDriverWait(driver, 20);
				waity.until(ExpectedConditions.elementToBeClickable(By.xpath(xpathExpression)));
				String linkText = driver.findElement(By.xpath(xpathExpression)).getText().toLowerCase();				
				if (linkText.contains(tvSeriesArr[i]) || tvSeriesArr[i].contains(linkText)) {
					found = true;
					System.out.println("found in watchlist");
					System.out.println(tvSeriesArr[i]);
				}
				index++;
			}
			if (!found) {
				System.out.println("TV Series: " + tvSeriesArr[i] + " Couldn't be found");
			}
			assertEquals(found, true);
			
		}
	}
	
	@AfterAll
	public static void tearDown() {
		driver.quit();
	}	
}
