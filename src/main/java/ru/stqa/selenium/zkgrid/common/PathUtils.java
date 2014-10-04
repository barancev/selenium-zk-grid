package ru.stqa.selenium.zkgrid.common;

public class PathUtils {

  public static String nodePath(String nodeId) {
    return nodePath(nodeId, "");
  }

  public static String nodeHeartBeatPath(String nodeId) {
    return nodePath(nodeId, "/heartbeat");
  }

  public static String nodeSlotsPath(String nodeId) {
    return nodePath(nodeId, "/slots");
  }

  public static String nodeSlotPath(SlotInfo slot) {
    return nodeSlotPath(slot, "");
  }

  public static String nodeSlotPath(SlotInfo slot, String subPath) {
    return nodeSlotPath(slot.getNodeId(), slot.getSlotId(), subPath);
  }

  public static String nodeSlotPath(String nodeId, String slotId) {
    return nodeSlotPath(nodeId, slotId, "");
  }

  public static String nodeSlotPath(String nodeId, String slotId, String subPath) {
    return nodePath(nodeId, "/slots/" + slotId + subPath);
  }

  public static String nodeSlotCommandPath(SlotInfo slot) {
    return nodeSlotPath(slot, "/command");
  }

  public static String nodeSlotResponsePath(SlotInfo slot) {
    return nodeSlotPath(slot, "/response");
  }

  public static String nodeSlotStatePath(SlotInfo slot) {
    return nodeSlotPath(slot, "/state");
  }

  public static String nodePath(String nodeId, String subPath) {
    return "/nodes/" + nodeId + subPath;
  }

  public static String clientPath(String clientId) {
    return clientPath(clientId, "");
  }

  public static String clientAllocatedSlotPath(String clientId) {
    return clientPath(clientId, "/slot");
  }

  public static String clientPath(String clientId, String subPath) {
    return "/client/" + clientId + subPath;
  }

}
