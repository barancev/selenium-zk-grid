package ru.stqa.selenium.zkgrid.node;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.util.List;

public class NodeConfiguration {

  long clientInactivityTimeout = 120;
  long commandExecutionTimeout = 60;

  List<SlotConfiguration> slots;

  static class SlotConfiguration {
    String name;
    String browserName;
    String version;

    String browserBinary;

    long maxInstances;

    public String getName() {
      return name != null ? name : browserName;
    }

    public Capabilities getCapabilities() {
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setCapability(CapabilityType.BROWSER_NAME, browserName);
      if (version != null) {
        capabilities.setCapability(CapabilityType.VERSION, version);
      }
      if (browserBinary != null) {
        if ("firefox".equals(browserName)) {
          capabilities.setCapability(FirefoxDriver.BINARY, new FirefoxBinary(new File(browserBinary)));
        }
      }
      capabilities.setPlatform(Platform.getCurrent());
      return capabilities;
    }
  }
}
