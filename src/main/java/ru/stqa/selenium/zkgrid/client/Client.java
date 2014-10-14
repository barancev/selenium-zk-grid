package ru.stqa.selenium.zkgrid.client;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.*;

public class Client {

  public static void main(String[] args) throws Exception {
    DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    WebDriver driver = new RemoteWebDriver(new CuratorCommandExecutor("localhost:4444"), capabilities);
    for (int i = 0; i < 1; i++) {
      driver.get("http://localhost/");
      Thread.sleep(2000);
    }
    driver.quit();
  }

}
