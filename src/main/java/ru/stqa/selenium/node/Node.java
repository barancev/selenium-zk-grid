package ru.stqa.selenium.node;

import com.google.common.collect.Maps;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static ru.stqa.selenium.common.PathUtils.*;

public class Node {

  private String nodeId = UUID.randomUUID().toString();
  private Capabilities capabilities;

  private CuratorFramework client;

  private ScheduledExecutorService serviceExecutor;
  Map<String, ExecutorService> sessionExecutors = Maps.newHashMap();

  private boolean heartBeating = true;

  public static void main(String[] args) throws Exception {
    Node node = new Node("localhost:4444", DesiredCapabilities.firefox());
    node.start();
  }

  public Node(String zooKeeperConnectionString, Capabilities capabilities) {
    this.capabilities = capabilities;
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    client = CuratorFrameworkFactory.newClient(zooKeeperConnectionString, retryPolicy);
  }

  private void start() throws Exception {
    client.start();
    registerToHub();

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    serviceExecutor.scheduleAtFixedRate(new HeartBeat(), 1, 1, TimeUnit.SECONDS);

    Thread.sleep(5000);
    unregisterFromHub();

    serviceExecutor.shutdown();
  }

  private void registerToHub() throws Exception {
    client.create().creatingParentsIfNeeded().forPath(nodeSlotsPath(nodeId),
        new BeanToJsonConverter().convert(capabilities).getBytes());
  }

  private void unregisterFromHub() throws Exception {
    heartBeating = false;
    client.delete().deletingChildrenIfNeeded().forPath(nodePath(nodeId, ""));
  }

  private class HeartBeat implements Runnable {
    @Override
    public void run() {
      if (heartBeating) {
        try {
          String timestamp = String.valueOf(System.currentTimeMillis());
          if (client.checkExists().forPath(nodeHeartBeatPath(nodeId)) == null) {
            client.create().forPath(nodeHeartBeatPath(nodeId), timestamp.getBytes());
          } else {
            client.setData().forPath(nodeHeartBeatPath(nodeId), timestamp.getBytes());
          }
        } catch (Exception e) {
          heartBeating = false;
          e.printStackTrace();
        }
      }
    }
  }
}
