package imdb;
import java.util.concurrent.TimeUnit;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.sql.DriverManager;
import java.util.*; 

import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.openqa.selenium.chrome.ChromeDriver;

public class test {
	
	/*
	 * Go over the watchlist and verify that the tv shows we added to the watchlist
	 * are indeed in it
	 */
	public static void verifyWatchList(ChromeDriver driver, String[] tvShowsArr) {
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
		for(int i = tvShowsArr.length-1; i >= 0; i--) {			
			if (tvShowsArr[i].isEmpty()) { //this means the show rating was lower or the string is not of a tv show
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
				if (linkText.contains(tvShowsArr[i]) || tvShowsArr[i].contains(linkText)) {
					found = true;
					System.out.println("found in watchlist");
					System.out.println(tvShowsArr[i]);
				}
				index++;
			}
			if (!found) {
				System.out.println("TV Series: " + tvShowsArr[i] + " Couldn't be found");
			}			
		}	
	}
	
	/*
	 * Go over the list of tv shows and add the shows with rating >= ratingValue to watching list
	 */
	public static String[] addToWatchlist(ChromeDriver driver, String[] tvShowsArr, float ratingValue) {
		for(int i = 0; i < tvShowsArr.length; i++) {
			//enter the name from the array in the main search bar
			driver.findElement(By.id("navbar-query")).sendKeys(tvShowsArr[i]);
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
				if (str.contains((tvShowsArr[i])) && (str.contains(("TV Series").toLowerCase()) ||
						str.contains(("TV Mini-Series").toLowerCase()))) {
					found = true;
					String xpathExpressionLink = "(//div/table/tbody//*[@class='result_text']/a)[" + Integer.toString(index) + "]";
					driver.findElement(By.xpath(xpathExpressionLink)).click();
				}
				index++;
			}
			//this means that the string in the .properties file does not represent a TV Series in the IMDb DB
			if (!found) {
				tvShowsArr[i] = ""; //marking the array so we don't check it later for this string
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
				tvShowsArr[i] = ""; //marking the array so we don't check it later for this string
			}
		}
		return tvShowsArr;
	}
	
	/*
	 * Loads properties file and parses it -
	 * Creates an array of Strings (TV Series) 
	 * Also validates the rating is a valid number between 1 and 10
	 */
	public static void handlePropertiesFile(ChromeDriver driver) {		
		try (InputStream input = new FileInputStream(".\\config.properties")) {
            Properties prop = new Properties();    
            prop.load(input);
          //Parse the rating into a float
            String ratingStr = (prop.getProperty("ratings").replaceAll("^\"|\"$", "")).trim();
            try {
            	float rating = Float.parseFloat(ratingStr);
            	if (rating < 1 || rating > 10) {
            		System.out.println("Rating value provided in properties file is not in the range 1-10, terminating");
            		return;
            	}
            	//Parse the TV Series into an array of strings
                String tvShows = prop.getProperty("tv"); //get a string of the format "tvshow1","tvshow2",...
                String delims = ",";
                String[] tvShowsArr = tvShows.split(delims);
                for (int i = 0; i < tvShowsArr.length; i++) {
                	tvShowsArr[i] = tvShowsArr[i].toLowerCase().replaceAll("^\"|\"$", "").trim();
                }        
                //Call the rest of the program functions from here
                tvShowsArr = addToWatchlist(driver, tvShowsArr, rating);
                verifyWatchList(driver, tvShowsArr);		
            }catch(NumberFormatException e){
            	 System.out.println("Rating value provided in properties file is not a valid Float, terminating");
            	 return;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	
	/*
	 * Sign in using an IMDb account
	*/
	public static void signIn(ChromeDriver driver, String userName, String password){
		driver.findElement(By.linkText("Sign in")).click();
		driver.findElement(By.className("imdb-logo")).click();
		driver.findElement(By.name("email")).sendKeys(userName);
		driver.findElement(By.name("password")).clear();
		driver.findElement(By.name("password")).sendKeys(password);
		driver.findElement(By.id("signInSubmit")).click();
	}
	

	public static void main(String[] args) {
		//Setting the driver executable
		System.setProperty("webdriver.chrome.driver",".\\Driver\\chromedriver.exe");
		ChromeDriver driver = new ChromeDriver();
		//Maximize window
		driver.manage().window().maximize();
		//Open browser with desired URL
		driver.get("https://www.imdb.com");
		//The user is not logged in 
		if (driver.findElements(By.linkText("Sign in")).size() != 0) {
			String userName = "ella.steinberg93@gmail.com";
			String password = "!Qaz2wsx";
			signIn(driver, userName, password);
			handlePropertiesFile(driver);	
		//Maybe the user is already logged in
		} else {
			handlePropertiesFile(driver);
		}	
	}
	
}