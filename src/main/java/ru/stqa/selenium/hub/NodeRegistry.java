package ru.stqa.selenium.hub;

import com.google.common.collect.Maps;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.utils.ZKPaths;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.JsonToBeanConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.common.PathUtils.*;

public class NodeRegistry {

  private static Logger log = LoggerFactory.getLogger(NodeRegistry.class);

  public static class Builder {

    private CuratorFramework client;

    private long lostTimeout = 5000;
    private long deadTimeout = 10000;

    public Builder(CuratorFramework client) {
      this.client = client;
    }

    public Builder withLostTimeout(long lostTimeout) {
      this.lostTimeout = lostTimeout;
      return this;
    }

    public Builder withDeadTimeout(long deadTimeout) {
      this.deadTimeout = deadTimeout;
      return this;
    }

    public NodeRegistry create() throws Exception {
      log.debug("Creating NodeRegistry");
      NodeRegistry registry = new NodeRegistry(client);
      registry.setLostTimeout(lostTimeout);
      registry.setDeadTimeout(deadTimeout);
      registry.start();
      log.debug("NodeRegistry created");
      return registry;
    }
  }

  private CuratorFramework client;

  private long lostTimeout = 5000;
  private long deadTimeout = 10000;

  private Map<String, Capabilities> nodes = Maps.newHashMap();

  private ScheduledExecutorService serviceExecutor;

  private NodeRegistry(CuratorFramework client) {
    this.client = client;
  }

  private void setLostTimeout(long lostTimeout) {
    this.lostTimeout = lostTimeout;
  }

  private void setDeadTimeout(long deadTimeout) {
    this.deadTimeout = deadTimeout;
  }

  private void start() throws Exception {
    startNodeRegistrationListener();
    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new NodeHeartBeatWatcher(), 0, lostTimeout / 2, TimeUnit.MILLISECONDS);
  }

  private void startNodeRegistrationListener() throws Exception {
    PathChildrenCacheListener nodesListener = new PathChildrenCacheListener() {
      @Override
      public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        switch (event.getType()) {
          case CHILD_ADDED: {
            String nodeId = ZKPaths.getNodeFromPath(event.getData().getPath());
            String data = new String(client.getData().watched().forPath(nodeSlotsPath(nodeId)));
            DesiredCapabilities capabilities = new JsonToBeanConverter().convert(DesiredCapabilities.class, data);
            addNode(nodeId, capabilities);
            break;
          }

          case CHILD_UPDATED: {
            System.out.println("Node changed: " + ZKPaths.getNodeFromPath(event.getData().getPath()));
            break;
          }

          case CHILD_REMOVED: {
            String nodeId = ZKPaths.getNodeFromPath(event.getData().getPath());
            removeNode(nodeId);
            break;
          }
        }
      }
    };

    PathChildrenCache nodesCache = new PathChildrenCache(client, "/nodes", false);
    nodesCache.start();
    nodesCache.getListenable().addListener(nodesListener);
  }

  public void addNode(String nodeId, Capabilities capabilities) {
    nodes.put(nodeId, capabilities);
    log.info("Node added " + nodeId + ": " + capabilities);
  }

  public void removeNode(String nodeId) {
    nodes.remove(nodeId);
    log.info("Node removed " + nodeId);
  }

  public Slot findFreeSlot(Capabilities capabilities) {
    for (Map.Entry<String, Capabilities> entry : nodes.entrySet()) {
      return new Slot(entry.getKey());
    }
    return null;
  }

  private class NodeHeartBeatWatcher implements Runnable {
    @Override
    public void run() {
      try {
        for (String nodeId : client.getChildren().forPath("/nodes")) {
          if (client.checkExists().forPath(nodeHeartBeatPath(nodeId)) != null) {
            long timestamp = Long.parseLong(new String(client.getData().forPath(nodeHeartBeatPath(nodeId))));
            long now = System.currentTimeMillis();
            if (now - timestamp > deadTimeout) {
              log.info("Node is dead " + nodeId);
              client.delete().deletingChildrenIfNeeded().forPath(nodePath(nodeId, ""));

            } else if (now - timestamp > lostTimeout) {
              log.info("Node is lost " + nodeId);

            } else {
              log.debug("Node is alive " + nodeId);
            }

          } else {
            log.info("Node has no heartbeat " + nodeId);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
