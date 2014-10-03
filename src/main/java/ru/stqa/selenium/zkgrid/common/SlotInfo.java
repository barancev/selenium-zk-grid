package ru.stqa.selenium.zkgrid.common;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.DesiredCapabilities;

public class SlotInfo {

  private String nodeId;
  private String slotId;
  private DesiredCapabilities capabilities;

  private boolean busy = false;

  public SlotInfo() {
  }

  public SlotInfo(String nodeId, String slotId, DesiredCapabilities capabilities) {
    this.nodeId = nodeId;
    this.slotId = slotId;
    this.capabilities = capabilities;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getSlotId() {
    return slotId;
  }

  public void setSlotId(String slotId) {
    this.slotId = slotId;
  }

  public DesiredCapabilities getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(DesiredCapabilities capabilities) {
    this.capabilities = capabilities;
  }

  public boolean isBusy() {
    return busy;
  }

  public void setBusy(boolean busy) {
    this.busy = busy;
  }

  public boolean match(Capabilities capabilities) {
    return true;
  }

  @Override
  public String toString() {
    return "n{nodeId=" + nodeId + ", slotId=" + slotId + "}";
  }
}
