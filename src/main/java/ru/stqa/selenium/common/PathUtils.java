package ru.stqa.selenium.common;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.retry.ExponentialBackoffRetry;

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

  public static String nodePath(String nodeId, String subPath) {
    return "/nodes/" + nodeId + subPath;
  }

  public static String clientPath(String clientId) {
    return clientPath(clientId, "");
  }

  public static String clientNewSessionIdPath(String clientId) {
    return clientPath(clientId, "/newSession");
  }

  public static String clientPath(String clientId, String subPath) {
    return "/client/" + clientId + subPath;
  }

  public static String sessionPath(String sessionId) {
    return sessionPath(sessionId, "");
  }

  public static String sessionRequestPath(String sessionId) {
    return sessionPath(sessionId, "/request");
  }

  public static String sessionResponsePath(String sessionId) {
    return sessionPath(sessionId, "/response");
  }

  public static String sessionPath(String sessionId, String subPath) {
    return "/session/" + sessionId + subPath;
  }

}
