package ru.stqa.selenium.zkgrid.common;

import com.google.common.collect.Lists;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;

import java.util.List;

public class Curator {

  public static Curator createCurator(String connectionString, Logger log) {
    Curator curator = new Curator(connectionString, log);
    return curator;
  }

  private Logger log;
  private String connectionString;
  private CuratorFramework client;
  private List<CuratorStateListener> listeners = Lists.newArrayList();

  public Curator(String connectionString, Logger log) {
    this.connectionString = connectionString;
    this.log = log;
  }

  public void start() {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
    client.getConnectionStateListenable().addListener(new CuratorConnectionListener());
    client.start();
    log.info("Curator started");
  }

  public CuratorFramework getClient() {
    return client;
  }

  public String getDataForPath(String path) throws Exception {
    return new String(client.getData().forPath(path));
  }

  public boolean checkExists(String path) throws Exception {
    return client.checkExists().forPath(path) != null;
  }

  public void create(String path) throws Exception {
    if (! checkExists(path)) {
      client.create().creatingParentsIfNeeded().forPath(path);
    }
  }

  public void delete(String path) throws Exception {
    client.delete().deletingChildrenIfNeeded().forPath(path);
  }

  public void setData(String path, String data) throws Exception {
    if (checkExists(path)) {
      client.setData().forPath(path, data.getBytes());
    } else {
      client.create().creatingParentsIfNeeded().forPath(path, data.getBytes());
    }
  }

  public DistributedBarrier createBarrier(String parent) throws Exception {
    String barrierPath = parent + "/barrier";
    client.create().creatingParentsIfNeeded().forPath(barrierPath);
    DistributedBarrier barrier = new DistributedBarrier(client, barrierPath);
    barrier.setBarrier();
    return barrier;
  }

  public void clearBarrier(String parent) throws Exception {
    String barrierPath = parent + "/barrier";
    new DistributedBarrier(client, barrierPath).removeBarrier();
  }

  public void addStateListener(CuratorStateListener listener) {
    listeners.add(listener);
  }

  public void removeStateListener(CuratorStateListener listener) {
    listeners.remove(listener);
  }

  private class CuratorConnectionListener implements ConnectionStateListener {
    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
      switch (connectionState) {
        case CONNECTED: {
          log.warn("Connection to ZK server established");
          for (CuratorStateListener listener : listeners) {
            listener.connectionEstablished();
          }
          break;
        }
        case SUSPENDED: {
          log.warn("Connection to ZK server suspended");
          for (CuratorStateListener listener : listeners) {
            listener.connectionSuspended();
          }
          break;
        }
        case RECONNECTED: {
          log.warn("Connection to ZK server reconnected");
          for (CuratorStateListener listener : listeners) {
            listener.connectionRestored();
          }
          break;
        }
        case LOST: {
          log.warn("Connection to ZK server lost");
          for (CuratorStateListener listener : listeners) {
            listener.connectionLost();
          }
          client.close();
          start();
          break;
        }
      }
    }
  }
}
