package ru.stqa.selenium.hub;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.openqa.selenium.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.common.CapabilitiesSerializer;
import ru.stqa.selenium.common.Curator;
import ru.stqa.selenium.common.StringSerializer;

import static ru.stqa.selenium.common.PathUtils.clientNewSessionIdPath;
import static ru.stqa.selenium.common.PathUtils.clientPath;
import static ru.stqa.selenium.common.PathUtils.nodePath;

public class RegistrationRequestProcessor {

  private static Logger log = LoggerFactory.getLogger(RegistrationRequestProcessor.class);

  private Curator curator;
  private NodeRegistry nodeRegistry;

  public RegistrationRequestProcessor(Curator curator, NodeRegistry nodeRegistry) {
    this.curator = curator;
    this.nodeRegistry = nodeRegistry;
  }

  public void start() throws Exception {
    DistributedQueue<String> queue = QueueBuilder.builder(
        curator.getClient(), new RegistrationRequestConsumer(), new StringSerializer(), "/registrationRequests").buildQueue();
    queue.start();
  }

  private class RegistrationRequestConsumer implements QueueConsumer<String> {
    @Override
    public void consumeMessage(String nodeId) throws Exception {
      log.info("Registration request from " + nodeId);
      nodeRegistry.addNode(nodeId);
      curator.clearBarrier(nodePath(nodeId));
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
      System.out.println("!!!" + connectionState);
    }
  }
}