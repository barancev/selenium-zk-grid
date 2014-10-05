package ru.stqa.selenium.zkgrid.node;

import com.google.common.collect.Maps;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.*;
import shaded.org.openqa.selenium.remote.server.DefaultDriverSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;
import ru.stqa.selenium.zkgrid.common.StringSerializer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

public class Node {

  private static Logger log = LoggerFactory.getLogger(Node.class);

  private NodeParameters params;

  private String nodeId = UUID.randomUUID().toString();
  private Map<String, NodeSlot> slots = Maps.newHashMap();

  private DefaultDriverSessions sessions;

  private Curator curator;

  private ScheduledExecutorService serviceExecutor;

  private long heartBeatPeriod;
  private boolean heartBeating = true;
  private CommandHandler commandHandler;

  public static void main(String[] args) throws Exception {
    Node node = new Node(new NodeParameters());
    node.start();
  }

  public Node(NodeParameters params) {
    this.params = params;
    curator = Curator.createCurator(params.getHubConnectionString());
  }

  public void start() throws Exception {
    sessions = new DefaultDriverSessions();
    commandHandler = new CommandHandler(sessions);

    registerNode();
    registerSlots();

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new HeartBeat(), heartBeatPeriod, heartBeatPeriod, TimeUnit.SECONDS);
  }

  private void registerNode() throws Exception {
    curator.create(nodePath(nodeId));
    DistributedBarrier barrier = curator.createBarrier(nodePath(nodeId));

    DistributedQueue<String> queue = QueueBuilder.builder(
        curator.getClient(), null, new StringSerializer(), "/registrationRequests").buildQueue();
    queue.start();
    queue.put(nodeId);

    if (!barrier.waitOnBarrier(10, TimeUnit.SECONDS)) {
      throw new Error("Node can't register itself");
    }

    Map<String, Object> hubConfig = new JsonToBeanConverter().convert(Map.class, curator.getDataForPath(hubPath()));
    heartBeatPeriod = (Long) hubConfig.get("heartBeatPeriod");
  }

  private void registerSlots() throws Exception {
    for (int i = 0; i < 10; i++) {
      Capabilities capabilities = DesiredCapabilities.firefox();
      String slotId = String.valueOf(i);
      SlotInfo slotInfo = new SlotInfo(nodeId, slotId, capabilities);
      NodeSlot slot = new NodeSlot(curator, slotInfo, commandHandler);
      slots.put(slotInfo.getSlotId(), slot);
      curator.setData(nodeSlotPath(slotInfo), new BeanToJsonConverter().convert(capabilities));
      startCommandListener(slot);
    }
  }

  private void startCommandListener(final NodeSlot slot) throws Exception {
    final SlotInfo slotInfo = slot.getSlotInfo();
    final NodeCache nodeCache = new NodeCache(curator.getClient(), nodeSlotCommandPath(slotInfo), false);
    nodeCache.start();

    NodeCacheListener nodesListener = new NodeCacheListener () {
      @Override
      public void nodeChanged() throws Exception {
        String data = new String(nodeCache.getCurrentData().getData());
        Command cmd = new JsonToBeanConverter().convert(Command.class, data);
        log.info("Command received " + cmd);
        log.info("Dispatched to slot " + slot);
        slot.processCommand(cmd);
      }
    };

    nodeCache.getListenable().addListener(nodesListener);
  }

  private void unregisterFromHub() throws Exception {
    heartBeating = false;
    curator.delete(nodePath(nodeId));
  }

  private class HeartBeat implements Runnable {
    @Override
    public void run() {
      if (heartBeating) {
        try {
          curator.setData(nodeHeartBeatPath(nodeId), String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
          heartBeating = false;
          e.printStackTrace();
        }
      }
    }
  }
}
