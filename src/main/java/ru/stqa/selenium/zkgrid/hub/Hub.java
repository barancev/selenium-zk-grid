package ru.stqa.selenium.zkgrid.hub;

import com.beust.jcommander.internal.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;

import java.util.Map;
import java.util.Properties;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

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

  private HubParameters params;

  private SeleniumZooKeeperServer zooKeeperServer;

  private Curator curator;

  private NodeRegistry nodeRegistry;

  public static void main(String[] args) throws Exception {
    final Hub hub = new Hub(new HubParameters());
    hub.start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        hub.stop();
      }
    });
  }

  public Hub(final HubParameters params) {
    this.params = params;
  }

  public void start() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("dataDir", Files.createTempDir().getAbsolutePath());
    properties.setProperty("clientPort", String.valueOf(params.getPort()));
    startServer(properties);
    startCurator(properties.getProperty("clientPort"));

    Map<String, Object> config = Maps.newHashMap();
    config.put("heartBeatPeriod", params.getHeartBeatPeriod());
    curator.setData(hubPath(), new Gson().toJson(config));

    nodeRegistry = new NodeRegistry.Builder(curator).withLostTimeout(10000).withDeadTimeout(20000).create();
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
    curator = Curator.createCurator("localhost:" + port, log);
    curator.getClient().create().forPath("/nodes");
  }

  private void stop() {
    curator.getClient().close();
    zooKeeperServer.shutdown();
  }

}
