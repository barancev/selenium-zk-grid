package ru.stqa.selenium.zkgrid.client;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.*;

public class Client implements Runnable {

  private Capabilities capabilities;

  public static void main(String[] args) throws Exception {
    for (int i = 0; i < 2; i++) {
      new Thread(new Client(DesiredCapabilities.firefox())).start();
    }
  }

  public Client(Capabilities capabilities) {
    this.capabilities = capabilities;
  }

  @Override
  public void run() {
    try {
      WebDriver driver = new RemoteWebDriver(new CuratorCommandExecutor("localhost:4444"), capabilities);
      for (int i = 0; i < 30; i++) {
        driver.get("http://localhost/");
        Thread.sleep(20000);
      }
      driver.quit();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
