package ru.stqa.selenium.node;

import com.google.common.collect.Maps;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.common.CapabilitiesSerializer;
import ru.stqa.selenium.common.Curator;
import ru.stqa.selenium.common.StringSerializer;
import ru.stqa.selenium.hub.Hub;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static ru.stqa.selenium.common.PathUtils.*;

public class Node {

  private static Logger log = LoggerFactory.getLogger(Node.class);

  private String nodeId = UUID.randomUUID().toString();

  private final Curator curator;

  private ScheduledExecutorService serviceExecutor;
  Map<String, ExecutorService> sessionExecutors = Maps.newHashMap();

  private boolean heartBeating = true;

  public static void main(String[] args) throws Exception {
    Node node = new Node("localhost:4444");
    node.start();
  }

  public Node(String connectionString) {
    curator = Curator.createCurator(connectionString);
  }

  private void start() throws Exception {
    registerToHub();

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new HeartBeat(), 1, 1, TimeUnit.SECONDS);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          unregisterFromHub();
        } catch (Exception e) {
          e.printStackTrace();
        }
        serviceExecutor.shutdown();
      }
    });
  }

  private void registerToHub() throws Exception {
    registerNode();
    registerSlots();
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
  }

  private void registerSlots() throws Exception {
    for (int i = 0; i < 2; i++) {
      curator.setData(nodeSlotPath(nodeId, "" + i), new BeanToJsonConverter().convert(DesiredCapabilities.firefox()));
    }
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
