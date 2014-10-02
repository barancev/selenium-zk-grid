package ru.stqa.selenium.hub;

import com.google.common.io.Files;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Properties;

/**
 * Hello world!
 */
public class Hub {

  private static class SeleniumZooKeeperServer extends ZooKeeperServerMain {
    @Override
    protected void shutdown() {
      super.shutdown();
    }
  }

  private SeleniumZooKeeperServer zooKeeperServer;

  private CuratorFramework client;

  private NodeRegistry nodeRegistry;

  public static void main(String[] args) throws Exception {
    Properties startupProperties = new Properties() {
      {
        setProperty("dataDir", Files.createTempDir().getAbsolutePath());
        setProperty("clientPort", "4444");
        setProperty("server.1", "localhost:5444:6444");
      }
    };

    final Hub hub = new Hub();
    hub.start(startupProperties);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        hub.stop();
      }
    });
  }

  private void start(Properties properties) throws Exception {
    startServer(properties);
    startClient(properties.getProperty("clientPort"));

    nodeRegistry = new NodeRegistry.Builder(client).withLostTimeout(5000).withDeadTimeout(10000).start();
  }

  private void startServer(Properties properties) {
    QuorumPeerConfig quorumConfiguration = new QuorumPeerConfig();
    try {
      quorumConfiguration.parseProperties(properties);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final ServerConfig configuration = new ServerConfig();
    zooKeeperServer = new SeleniumZooKeeperServer();
    configuration.readFrom(quorumConfiguration);

    new Thread() {
      public void run() {
        try {
          zooKeeperServer.runFromConfig(configuration);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }.start();
  }

  private void startClient(String port) throws Exception {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    client = CuratorFrameworkFactory.newClient("localhost:" + port, retryPolicy);
    client.start();
    client.create().forPath("/nodes");
  }

  private void stop() {
    client.close();
    zooKeeperServer.shutdown();
  }

}
