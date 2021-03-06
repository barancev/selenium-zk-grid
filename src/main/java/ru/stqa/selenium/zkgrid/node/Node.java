package ru.stqa.selenium.zkgrid.node;

import com.google.common.collect.Maps;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.*;
import ru.stqa.selenium.zkgrid.common.CuratorStateListener;
import shaded.org.openqa.selenium.remote.server.DefaultDriverSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;
import ru.stqa.selenium.zkgrid.common.StringSerializer;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

public class Node {

  private static Logger log = LoggerFactory.getLogger(Node.class);

  private NodeParameters params;
  private final NodeConfiguration config;

  private String nodeId = UUID.randomUUID().toString();
  private Map<String, NodeSlot> slots = Maps.newHashMap();

  private DefaultDriverSessions sessions;

  private Curator curator;

  private ScheduledExecutorService serviceExecutor;
  private ScheduledFuture<?> heartBeatingFuture;

  private long heartBeatPeriod;
  private CommandHandler commandHandler;

  public static void main(String[] args) throws Exception {
    Node node = new Node(new NodeParameters());
    node.start();
  }

  public Node(NodeParameters params) throws IOException {
    this.params = params;
    this.config = params.getNodeConfiguration();
  }

  public void start() throws Exception {
    serviceExecutor = Executors.newSingleThreadScheduledExecutor();

    curator = Curator.createCurator(params.getHubConnectionString(), log);
    curator.start();

    sessions = new DefaultDriverSessions();
    commandHandler = new CommandHandler(sessions);
    
    createSlots();

    registerToTheHub();
    startHeartBeating();
    registerSlots();

    curator.addStateListener(new NodeCuratorStateListener());
  }

  private void createSlots() throws Exception {
    for (NodeConfiguration.SlotConfiguration slotConfig : config.slots) {
      for (int count = 1; count <= slotConfig.maxInstances; count++) {
        Capabilities capabilities = slotConfig.getCapabilities();
        String slotId = String.valueOf(slotConfig.getName() + "-" + count);
        SlotInfo slotInfo = new SlotInfo(nodeId, slotId, capabilities);
        NodeSlot slot = new NodeSlot.Builder(curator, slotInfo, commandHandler)
            .withCommandExecutionTimeout(config.commandExecutionTimeout, TimeUnit.SECONDS)
            .withClientInactivityTimeout(config.clientInactivityTimeout, TimeUnit.SECONDS)
            .create();
        slots.put(slotInfo.getSlotId(), slot);
      }
    }
  }

  private void startHeartBeating() {
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        if (heartBeatingFuture == null) {
          heartBeatingFuture = serviceExecutor.scheduleAtFixedRate(
              new HeartBeat(), 0, heartBeatPeriod, TimeUnit.MILLISECONDS);
        }
      }
    });
  }

  private void stopHeartBeating() {
    if (heartBeatingFuture != null) {
      heartBeatingFuture.cancel(true);
      heartBeatingFuture = null;
    }
  }

  private void registerToTheHub() {
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        log.info("Registering node to the hub");
        try {
          if (curator.checkExists(nodePath(nodeId))) {
            log.info("Node is already registered to the hub");
            return;
          }
          curator.create(nodePath(nodeId));
          DistributedBarrier barrier = curator.createBarrier(nodePath(nodeId));

          DistributedQueue<String> queue = QueueBuilder.builder(
              curator.getClient(), null, new StringSerializer(), "/registrationRequests").buildQueue();
          queue.start();
          queue.put(nodeId);

          if (! barrier.waitOnBarrier(10, TimeUnit.SECONDS)) {
            throw new Error("The hub did not clear the registration barrier");
          }

          Map<String, Object> hubConfig = new JsonToBeanConverter().convert(Map.class, curator.getDataForPath(hubPath()));
          heartBeatPeriod = (Long) hubConfig.get("heartBeatPeriod");
        } catch (Exception ex) {
          throw new Error("Node can't register itself", ex);
        }
        log.info("Node registered to the hub");
      }
    });
  }

  private void registerSlots() {
    for (final NodeSlot slot : slots.values()) {
      serviceExecutor.submit(new Runnable() {
        @Override
        public void run() {
          slot.registerToTheHub();
        }
      });
    }
  }

  private void destroyAllSessions() {
    for (NodeSlot slot : slots.values()) {
      slot.destroySession();
    }
  }

  private void unregisterFromHub() throws Exception {
    curator.delete(nodePath(nodeId));
  }

  private class HeartBeat implements Runnable {
    @Override
    public void run() {
      log.debug("Heart beat");
      try {
        curator.setData(nodeHeartBeatPath(nodeId), String.valueOf(System.currentTimeMillis()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private class NodeCuratorStateListener implements CuratorStateListener {
    @Override
    public void connectionEstablished() {
      registerToTheHub();
      startHeartBeating();
      registerSlots();
    }

    @Override
    public void connectionSuspended() {
      stopHeartBeating();
    }

    @Override
    public void connectionRestored() {
      startHeartBeating();
    }

    @Override
    public void connectionLost() {
      stopHeartBeating();
      destroyAllSessions();
    }
  }

}
