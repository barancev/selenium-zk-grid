package ru.stqa.selenium.zkgrid.hub;

import com.google.common.io.Files;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;

import java.util.Properties;

/**
 * Hello world!
 */
public class Hub {

  private static Logger log = LoggerFactory.getLogger(Hub.class);

  private static class SeleniumZooKeeperServer extends ZooKeeperServerMain {
    @Override
    protected void shutdown() {
      super.shutdown();
    }
  }

  private SeleniumZooKeeperServer zooKeeperServer;

  private Curator curator;

  private Properties properties;

  private NodeRegistry nodeRegistry;

  public static void main(String[] args) throws Exception {
    Properties startupProperties = new Properties() {
      {
        setProperty("dataDir", Files.createTempDir().getAbsolutePath());
        setProperty("clientPort", "4444");
        setProperty("server.1", "localhost:5444:6444");
      }
    };

    final Hub hub = new Hub(startupProperties);
    hub.start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        hub.stop();
      }
    });
  }

  public Hub(Properties properties) {
    this.properties = properties;
  }

  private void start() throws Exception {
    startServer(properties);
    startCurator(properties.getProperty("clientPort"));

    nodeRegistry = new NodeRegistry.Builder(curator).withLostTimeout(5000).withDeadTimeout(10000).create();
    new RegistrationRequestProcessor(curator, nodeRegistry).start();
    new NewSessionRequestProcessor(curator, nodeRegistry).start();
  }

  private void startServer(Properties properties) {
    log.info("Starting ZooKeeper server: " + properties);
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

  private void startCurator(String port) throws Exception {
    curator = Curator.createCurator("localhost:" + port);
    curator.getClient().create().forPath("/nodes");
  }

  private void stop() {
    curator.getClient().close();
    zooKeeperServer.shutdown();
  }

}
