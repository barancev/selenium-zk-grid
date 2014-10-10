package ru.stqa.selenium.zkgrid.hub;

import org.openqa.selenium.Capabilities;

public class DefaultCapabilityMatcher implements CapabilityMatcher {
  @Override
  public boolean match(Capabilities requiredCapabilities, Capabilities actualCapabilities) {
    return true;
  }
}
