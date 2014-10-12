package ru.stqa.selenium.zkgrid.hub;

import com.google.common.collect.Maps;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.utils.ZKPaths;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.JsonToBeanConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.NodeInfo;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

public class NodeRegistry {

  private static Logger log = LoggerFactory.getLogger(NodeRegistry.class);

  public static class Builder {
    private Curator curator;

    private long lostTimeout;
    private long deadTimeout;
    private Class<? extends CapabilityMatcher> capabilityMatcher;

    public Builder(Curator curator) {
      this.curator = curator;
    }

    public Builder withLostTimeout(long lostTimeout, TimeUnit timeUnit) {
      this.lostTimeout = timeUnit.toMillis(lostTimeout);
      return this;
    }

    public Builder withDeadTimeout(long deadTimeout, TimeUnit timeUnit) {
      this.deadTimeout = timeUnit.toMillis(deadTimeout);
      return this;
    }

    public Builder withCapabilityMatcher(String capabilityMatcherClass) throws ClassNotFoundException {
      this.capabilityMatcher =
          (Class<? extends CapabilityMatcher>) this.getClass().getClassLoader().loadClass(capabilityMatcherClass);
      return this;
    }

    public NodeRegistry create() throws Exception {
      log.debug("Creating NodeRegistry");
      NodeRegistry registry = new NodeRegistry(curator);
      registry.setLostTimeout(lostTimeout);
      registry.setDeadTimeout(deadTimeout);
      registry.setCapabilityMatcher(capabilityMatcher.newInstance());
      registry.start();
      log.debug("NodeRegistry created");
      return registry;
    }
  }

  private Curator curator;

  private long lostTimeout = 5000;
  private long deadTimeout = 10000;
  private CapabilityMatcher capabilityMatcher;

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

  private void setCapabilityMatcher(CapabilityMatcher capabilityMatcher) {
    this.capabilityMatcher = capabilityMatcher;
  }

  private void start() throws Exception {
    startNodesDeregistrationListener();
    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new NodeHeartBeatWatcher(), 0, lostTimeout / 2, TimeUnit.MILLISECONDS);
  }

  public void addNode(String nodeId) throws Exception {
    nodes.put(nodeId, new NodeInfo(nodeId));
    startSlotRegistrationListener(nodeId);
    log.info("Node {} added to the registry", nodeId);
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
            SlotInfo slot = getNode(nodeId).addSlot(slotId, capabilities);
            startSlotStateListener(slot);
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

  private void startSlotStateListener(final SlotInfo slot) throws Exception {
    final NodeCache nodeCache = new NodeCache(curator.getClient(), nodeSlotStatePath(slot), false);
    nodeCache.start();

    NodeCacheListener nodesListener = new NodeCacheListener () {
      @Override
      public void nodeChanged() throws Exception {
        String data = new String(nodeCache.getCurrentData().getData());
        log.info("Slot {} state changed to {}", slot, data);
        if ("busy".equals(data)) {
          slot.setBusy(true);
        } else {
          slot.setBusy(false);
        }
      }
    };

    nodeCache.getListenable().addListener(nodesListener);
  }

  private NodeInfo getNode(String nodeId) {
    return nodes.get(nodeId);
  }


  public void removeNode(String nodeId) {
    nodes.remove(nodeId);
    log.info("Node {} removed", nodeId);
  }

  public SlotInfo findFreeMatchingSlot(Capabilities requiredCapabilities) {
    for (NodeInfo node : nodes.values()) {
      for (SlotInfo slot : node.getSlots()) {
        if (slot.isBusy()) {
          continue;
        }
        if (capabilityMatcher.matches(slot.getCapabilities(), requiredCapabilities)) {
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
              log.info("Node {} is dead", nodeId);
              client.delete().deletingChildrenIfNeeded().forPath(nodePath(nodeId, ""));

            } else if (now - timestamp > lostTimeout) {
              log.info("Node {} is lost", nodeId);

            } else {
              log.debug("Node {} is alive", nodeId);
            }

          } else {
            log.info("Node {} has no heartbeat", nodeId);
            curator.setData(nodeHeartBeatPath(nodeId), String.valueOf(System.currentTimeMillis()));
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
