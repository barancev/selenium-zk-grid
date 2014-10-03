package ru.stqa.selenium.hub;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.DesiredCapabilities;

public class SlotInfo {

  private String nodeId;
  private String slotId;
  private final DesiredCapabilities capabilities;

  private boolean buzy = false;

  public SlotInfo(String nodeId, String slotId, DesiredCapabilities capabilities) {
    this.nodeId = nodeId;
    this.slotId = slotId;
    this.capabilities = capabilities;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getSlotId() {
    return slotId;
  }

  public DesiredCapabilities getCapabilities() {
    return capabilities;
  }

  public boolean isBuzy() {
    return buzy;
  }

  public void setBuzy(boolean buzy) {
    this.buzy = buzy;
  }

  public boolean match(Capabilities capabilities) {
    return true;
  }

}
