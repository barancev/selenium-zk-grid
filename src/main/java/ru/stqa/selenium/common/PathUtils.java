package ru.stqa.selenium.common;

public class PathUtils {

  public static String nodeHeartBeatPath(String nodeId) {
    return nodePath(nodeId, "/heartbeat");
  }

  public static String nodeSlotsPath(String nodeId) {
    return nodePath(nodeId, "/slots");
  }

  public static String nodePath(String nodeId, String subPath) {
    return "/nodes/" + nodeId + subPath;
  }

}
