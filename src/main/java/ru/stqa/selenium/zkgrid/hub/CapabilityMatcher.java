package ru.stqa.selenium.zkgrid.hub;

import org.openqa.selenium.Capabilities;

public interface CapabilityMatcher {
  boolean matches(Capabilities actualCapabilities, Capabilities requiredCapabilities);
}
