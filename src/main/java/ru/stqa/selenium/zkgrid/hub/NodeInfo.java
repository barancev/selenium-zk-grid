package ru.stqa.selenium.zkgrid.hub;

import com.google.common.collect.Maps;
import org.openqa.selenium.remote.DesiredCapabilities;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.Collection;
import java.util.Map;

public class NodeInfo {

  private String nodeId;

  private Map<String, SlotInfo> slots = Maps.newHashMap();

  public NodeInfo(String nodeId) {
    this.nodeId = nodeId;
  }

  public synchronized SlotInfo addSlot(String slotId, DesiredCapabilities capabilities) {
    SlotInfo slot = new SlotInfo(nodeId, slotId, capabilities);
    slots.put(slotId, slot);
    return slot;
  }

  public synchronized void removeSlot(String slotId) {
    slots.remove(slotId);
  }

  public synchronized Collection<SlotInfo> getSlots() {
    return slots.values();
  }
}
