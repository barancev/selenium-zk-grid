package ru.stqa.selenium.common;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class Curator {

  public static Curator createCurator(String connectionString) {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 5);
    CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
    client.start();
    return new Curator(client);
  }

  private CuratorFramework client;

  public Curator(CuratorFramework client) {
    this.client = client;
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

}
