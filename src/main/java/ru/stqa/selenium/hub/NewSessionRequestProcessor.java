package ru.stqa.selenium.hub;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.common.CapabilitiesSerializer;
import ru.stqa.selenium.common.Curator;
import ru.stqa.selenium.common.SlotInfo;

import static ru.stqa.selenium.common.PathUtils.*;

public class NewSessionRequestProcessor {

  private static Logger log = LoggerFactory.getLogger(NewSessionRequestProcessor.class);

  private Curator curator;
  private NodeRegistry nodeRegistry;

  public NewSessionRequestProcessor(Curator curator, NodeRegistry nodeRegistry) {
    this.curator = curator;
    this.nodeRegistry = nodeRegistry;
  }

  public void start() throws Exception {
    DistributedQueue<Capabilities> queue = QueueBuilder.builder(
        curator.getClient(), new NewSessionRequestConsumer(), new CapabilitiesSerializer(), "/newSessionRequests").buildQueue();
    queue.start();
  }

  private class NewSessionRequestConsumer implements QueueConsumer<Capabilities> {
    @Override
    public void consumeMessage(Capabilities capabilities) throws Exception {
      log.info("Request for new session " + capabilities);
      String clientId = (String) capabilities.getCapability("zk-grid.clientId");
      SlotInfo slot = nodeRegistry.findFreeMatchingSlot(capabilities);
      if (slot != null) {
        log.info("Slot found " + slot.getSlotId());
        curator.setData(clientNewSessionIdPath(clientId), new BeanToJsonConverter().convert(slot));
        slot.setBusy(true);
      } else {
        log.info("No slot found");
        curator.setData(clientNewSessionIdPath(clientId), "{}");
      }
      curator.clearBarrier(clientPath(clientId));
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
      System.out.println("!!!" + connectionState);
    }
  }

}
