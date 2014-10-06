package ru.stqa.selenium.zkgrid.common;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;

public class Curator {

  public static Curator createCurator(String connectionString, Logger log) {
    Curator curator = new Curator(connectionString, log);
    curator.start();
    return curator;
  }

  private Logger log;
  private String connectionString;
  private CuratorFramework client;

  public Curator(String connectionString, Logger log) {
    this.connectionString = connectionString;
    this.log = log;
  }

  public void start() {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 5);
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

  public void create(String path) throws Exception {
    if (client.checkExists().forPath(path) == null) {
      client.create().creatingParentsIfNeeded().forPath(path);
    }
  }

  public void delete(String path) throws Exception {
    client.delete().deletingChildrenIfNeeded().forPath(path);
  }

  public void setData(String path, String data) throws Exception {
    if (client.checkExists().forPath(path) == null) {
      client.create().creatingParentsIfNeeded().forPath(path, data.getBytes());
    } else {
      client.setData().forPath(path, data.getBytes());
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

  private class CuratorConnectionListener implements ConnectionStateListener {
    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
      switch (connectionState) {
        case CONNECTED: {
          log.warn("Connection to ZK server established");
          break;
        }
        case SUSPENDED: {
          log.warn("Connection to ZK server suspended");
          break;
        }
        case RECONNECTED: {
          log.warn("Connection to ZK server reconnected");
          break;
        }
        case LOST: {
          log.warn("Connection to ZK server lost");
          //client.close();
          break;
        }
      }
    }
  }
}
