package ru.stqa.selenium.zkgrid.common;

import org.openqa.selenium.Capabilities;

public class SlotInfo {

  private String nodeId;
  private String slotId;
  private Capabilities capabilities;

  private boolean busy = false;

  public SlotInfo() {
  }

  public SlotInfo(String nodeId, String slotId, Capabilities capabilities) {
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

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(Capabilities capabilities) {
    this.capabilities = capabilities;
  }

  public boolean isBusy() {
    return busy;
  }

  public void setBusy(boolean busy) {
    this.busy = busy;
  }

  @Override
  public String toString() {
    return "{nodeId=" + nodeId + ", slotId=" + slotId + "}";
  }
}
