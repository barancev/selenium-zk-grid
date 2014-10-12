package ru.stqa.selenium.zkgrid.hub;

import com.google.common.base.Throwables;
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

  private long lostTimeout;
  private long deadTimeout;
  private CapabilityMatcher capabilityMatcher;

  private Map<String, NodeInfo> nodes = Maps.newHashMap();

  private ScheduledExecutorService serviceExecutor;

  private NodeRegistry(Curator curator) {
    this.curator = curator;

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
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
  }

  public void addNode(String nodeId) throws Exception {
    nodes.put(nodeId, new NodeInfo(nodeId));
    startNodeHeartBeatWatcher(nodeId);
    startSlotRegistrationListener(nodeId);
    log.info("Node {} added to the registry", nodeId);
  }

  private void startNodeHeartBeatWatcher(final String nodeId) {
    serviceExecutor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          if (curator.checkExists(nodeHeartBeatPath(nodeId))) {
            long timestamp = Long.parseLong(curator.getDataForPath(nodeHeartBeatPath(nodeId)));
            long now = System.currentTimeMillis();
            if (now - timestamp > deadTimeout) {
              log.info("Node {} is dead", nodeId);
              curator.delete(nodePath(nodeId, ""));

            } else if (now - timestamp > lostTimeout) {
              log.info("Node {} is lost", nodeId);

            } else {
              log.debug("Node {} is alive", nodeId);
            }

          } else {
            log.info("Node {} has no heartbeat", nodeId);
            curator.setData(nodeHeartBeatPath(nodeId), String.valueOf(System.currentTimeMillis()));
          }
        } catch (Exception ex) {
          Throwables.propagate(ex);
        }
      }
    }, lostTimeout/2, lostTimeout/2, TimeUnit.MILLISECONDS);
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

}
