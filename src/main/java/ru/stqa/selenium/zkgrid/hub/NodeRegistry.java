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
import ru.stqa.selenium.zkgrid.common.SlotAllocationResponse;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.Map;
import java.util.concurrent.*;

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
  private Map<String, Future<?>> heartBeaters = Maps.newHashMap();

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

  public void registerNode(final String nodeId) {
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          nodes.put(nodeId, new NodeInfo(nodeId));
          startNodeHeartBeatWatcher(nodeId);
          startSlotRegistrationListener(nodeId);
          log.info("Node {} added to the registry", nodeId);
          curator.clearBarrier(nodePath(nodeId));
        } catch (Exception ex) {
          throw Throwables.propagate(ex);
        }
      }
    });
  }

  public void unregisterNode(final String nodeId) {
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          nodes.remove(nodeId);
          heartBeaters.get(nodeId).cancel(false);
          heartBeaters.remove(nodeId);
          curator.delete(nodePath(nodeId, ""));
          log.info("Node {} removed from the registry", nodeId);
        } catch (Exception ex) {
          throw Throwables.propagate(ex);
        }
      }
    });
  }

  private void startNodeHeartBeatWatcher(final String nodeId) {
    Future<?> heartBeater = serviceExecutor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          if (curator.checkExists(nodeHeartBeatPath(nodeId))) {
            long timestamp = Long.parseLong(curator.getDataForPath(nodeHeartBeatPath(nodeId)));
            long now = System.currentTimeMillis();
            if (now - timestamp > deadTimeout) {
              unregisterNode(nodeId);

            } else if (now - timestamp > lostTimeout) {
              log.info("Node {} is lost", nodeId);

            } else {
              log.debug("Node {} is alive", nodeId);
            }

          } else {
            log.warn("Node {} has no heartbeat", nodeId);
            curator.setData(nodeHeartBeatPath(nodeId), String.valueOf(System.currentTimeMillis()));
          }
        } catch (Exception ex) {
          throw Throwables.propagate(ex);
        }
      }
    }, lostTimeout / 2, lostTimeout / 2, TimeUnit.MILLISECONDS);
    heartBeaters.put(nodeId, heartBeater);
  }

  private void startNodesDeregistrationListener() throws Exception {
    PathChildrenCacheListener nodesListener = new PathChildrenCacheListener() {
      @Override
      public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        switch (event.getType()) {
          case CHILD_REMOVED: {
            String nodeId = ZKPaths.getNodeFromPath(event.getData().getPath());
            unregisterNode(nodeId);
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

  public SlotAllocationResponse findFreeMatchingSlot(Capabilities requiredCapabilities) {
    int matchingSlots = 0;
    for (NodeInfo node : nodes.values()) {
      for (SlotInfo slot : node.getSlots()) {
        if (capabilityMatcher.matches(slot.getCapabilities(), requiredCapabilities)) {
          if (!slot.isBusy()) {
            return new SlotAllocationResponse(SlotAllocationResponse.Status.OK, slot);
          } else {
            matchingSlots++;
          }
        }
      }
    }
    if (matchingSlots == 0) {
      return new SlotAllocationResponse(SlotAllocationResponse.Status.NO_MATCHING_SLOT, null,
          "There are no matching slots found");
    } else {
      return new SlotAllocationResponse(SlotAllocationResponse.Status.NO_FREE_SLOT, null,
          "There are "+matchingSlots+" matching slots, but they are all busy");
    }
  }

}
