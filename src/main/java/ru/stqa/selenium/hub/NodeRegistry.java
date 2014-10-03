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
import ru.stqa.selenium.common.Curator;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.common.PathUtils.*;

public class NodeRegistry {

  private static Logger log = LoggerFactory.getLogger(NodeRegistry.class);

  public static class Builder {

    private Curator curator;

    private long lostTimeout = 5000;
    private long deadTimeout = 10000;

    public Builder(Curator curator) {
      this.curator = curator;
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
      NodeRegistry registry = new NodeRegistry(curator);
      registry.setLostTimeout(lostTimeout);
      registry.setDeadTimeout(deadTimeout);
      registry.start();
      log.debug("NodeRegistry created");
      return registry;
    }
  }

  private Curator curator;

  private long lostTimeout = 5000;
  private long deadTimeout = 10000;

  private Map<String, NodeInfo> nodes = Maps.newHashMap();

  private ScheduledExecutorService serviceExecutor;

  private NodeRegistry(Curator curator) {
    this.curator = curator;
  }

  private void setLostTimeout(long lostTimeout) {
    this.lostTimeout = lostTimeout;
  }

  private void setDeadTimeout(long deadTimeout) {
    this.deadTimeout = deadTimeout;
  }

  private void start() throws Exception {
    startNodesDeregistrationListener();
    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new NodeHeartBeatWatcher(), 0, lostTimeout / 2, TimeUnit.MILLISECONDS);
  }

  public void addNode(String nodeId) throws Exception {
    nodes.put(nodeId, new NodeInfo(nodeId));
    startSlotRegistrationListener(nodeId);
    log.info("Node added " + nodeId);
  }

  private void startNodesDeregistrationListener() throws Exception {
    PathChildrenCacheListener nodesListener = new PathChildrenCacheListener() {
      @Override
      public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        switch (event.getType()) {
          case CHILD_REMOVED: {
            String nodeId = ZKPaths.getNodeFromPath(event.getData().getPath());
            removeNode(nodeId);
            break;
          }
        }
      }
    };

    PathChildrenCache nodesCache = new PathChildrenCache(curator.getClient(), "/nodes", false);
    nodesCache.start();
    nodesCache.getListenable().addListener(nodesListener);
  }

  private void startSlotRegistrationListener(final String nodeId) throws Exception {
    curator.create(nodeSlotsPath(nodeId));

    PathChildrenCacheListener nodesListener = new PathChildrenCacheListener() {
      @Override
      public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        switch (event.getType()) {
          case CHILD_ADDED: {
            String slotId = ZKPaths.getNodeFromPath(event.getData().getPath());
            String data = curator.getDataForPath(nodeSlotPath(nodeId, slotId));
            DesiredCapabilities capabilities = new JsonToBeanConverter().convert(DesiredCapabilities.class, data);
            log.info("Slot registration request " + slotId + " " + capabilities);
            getNode(nodeId).addSlot(slotId, capabilities);
            break;
          }

          case CHILD_REMOVED: {
            String slotId = ZKPaths.getNodeFromPath(event.getData().getPath());
            log.info("Slot removed " + slotId);
            getNode(nodeId).removeSlot(slotId);
            break;
          }
        }
      }
    };

    PathChildrenCache nodesCache = new PathChildrenCache(curator.getClient(), nodeSlotsPath(nodeId), false);
    nodesCache.start();
    nodesCache.getListenable().addListener(nodesListener);
  }

  private NodeInfo getNode(String nodeId) {
    return nodes.get(nodeId);
  }


  public void removeNode(String nodeId) {
    nodes.remove(nodeId);
    log.info("Node removed " + nodeId);
  }

  public SlotInfo findFreeMatchingSlot(Capabilities capabilities) {
    for (NodeInfo node : nodes.values()) {
      for (SlotInfo slot : node.getSlots()) {
        if (!slot.isBuzy() && slot.match(capabilities)) {
          return slot;
        }
      }
    }
    return null;
  }

  private class NodeHeartBeatWatcher implements Runnable {
    @Override
    public void run() {
      try {
        CuratorFramework client = curator.getClient();
        for (String nodeId : nodes.keySet()) {
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
