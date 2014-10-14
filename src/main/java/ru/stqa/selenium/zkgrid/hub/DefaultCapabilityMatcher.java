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
  public boolean matches(Capabilities providedCapabilities, Capabilities requiredCapabilities) {
    if (providedCapabilities == null || requiredCapabilities == null) {
      return false;
    }
    for (String key : toConsider) {
      Object requiredValue = requiredCapabilities.getCapability(key);
      if (requiredValue != null && !"".equals(requiredValue)) {
        if (key.equals(CapabilityType.PLATFORM)) {
          if (! providedCapabilities.getPlatform().is(requiredCapabilities.getPlatform())) {
            return false;
          }
        } else if (key.equals(CapabilityType.VERSION)) {
          String requiredVersion = requiredCapabilities.getVersion();
          if (requiredVersion.endsWith("+")) {
            requiredVersion = requiredVersion.substring(0, requiredVersion.length()-1);
            if (versionCompare(providedCapabilities.getVersion(), requiredVersion) < 0) {
              return false;
            }
          } else if (requiredVersion.endsWith("-")) {
            requiredVersion = requiredVersion.substring(0, requiredVersion.length()-1);
            if (versionCompare(providedCapabilities.getVersion(), requiredVersion) > 0) {
              return false;
            }
          } else {
            if (versionCompare(providedCapabilities.getVersion(), requiredVersion) != 0) {
              return false;
            }
          }
        } else {
          if (!providedCapabilities.getCapability(key).equals(requiredValue)) {
            return false;
          }
        }
      } else {
        // null value matches anything.
      }
    }
    return true;
  }

  /**
   * Compares two version strings.
   *
   * Use this instead of String.compareTo() for a non-lexicographical
   * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
   *
   * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
   *
   * @param str1 a string of ordinal numbers separated by decimal points.
   * @param str2 a string of ordinal numbers separated by decimal points.
   * @return The result is a negative integer if str1 is _numerically_ less than str2.
   *         The result is a positive integer if str1 is _numerically_ greater than str2.
   *         The result is zero if the strings are _numerically_ equal.
   */
  private static Integer versionCompare(String str1, String str2) {
    if (str1 == null || "".equals(str1)) {
      str1 = "0";
    }
    if (str2 == null || "".equals(str2)) {
      str2 = "0";
    }
    List<String> vals1 = Lists.newArrayList(str1.split("\\."));
    List<String> vals2 = Lists.newArrayList(str2.split("\\."));
    int sizeDiff = vals1.size() - vals2.size();
    if (sizeDiff > 0) {
      for (int i = 0; i < sizeDiff; i++) {
        vals2.add("0");
      }
    }
    if (sizeDiff < 0) {
      for (int i = 0; i < -sizeDiff; i++) {
        vals1.add("0");
      }
    }
    for (int i = 0; i < vals1.size(); i++) {
      int diff = Integer.valueOf(vals1.get(i)).compareTo(Integer.valueOf(vals2.get(i)));
      if (diff != 0) {
        return diff;
      }

    }
    return 0;
  }
}
