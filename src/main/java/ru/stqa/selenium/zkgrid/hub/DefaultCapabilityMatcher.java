package ru.stqa.selenium.zkgrid.hub;

import com.beust.jcommander.internal.Lists;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.CapabilityType;

import java.util.List;

public class DefaultCapabilityMatcher implements CapabilityMatcher {

  private List<String> toConsider = Lists.newArrayList();

  public DefaultCapabilityMatcher() {
    toConsider.add(CapabilityType.PLATFORM);
    toConsider.add(CapabilityType.BROWSER_NAME);
    toConsider.add(CapabilityType.VERSION);
    toConsider.add("applicationName");
  }

  @Override
  public boolean matches(Capabilities actualCapabilities, Capabilities requiredCapabilities) {
    if (actualCapabilities == null || requiredCapabilities == null) {
      return false;
    }
    for (String key : toConsider) {
      if (requiredCapabilities.getCapability(key) != null) {
        if (key.equals(CapabilityType.PLATFORM)) {
          if (! actualCapabilities.getPlatform().is(requiredCapabilities.getPlatform())) {
            return false;
          }
        } else {
          if (!requiredCapabilities.getCapability(key).equals(actualCapabilities.getCapability(key))) {
            return false;
          }
        }
      } else {
        // null value matches anything.
      }
    }
    return true;
  }
}
