package ru.stqa.selenium.common;

import com.google.common.collect.Maps;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Collection;
import java.util.Map;

public class NodeInfo {

  private String nodeId;

  private Map<String, SlotInfo> slots = Maps.newHashMap();

  public NodeInfo(String nodeId) {
    this.nodeId = nodeId;
  }

  public void addSlot(String slotId, DesiredCapabilities capabilities) {
    slots.put(slotId, new SlotInfo(nodeId, slotId, capabilities));
  }

  public void removeSlot(String slotId) {
    slots.remove(slotId);
  }

  public Collection<SlotInfo> getSlots() {
    return slots.values();
  }
}
