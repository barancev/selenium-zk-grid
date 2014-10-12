package ru.stqa.selenium.zkgrid.hub;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.CapabilitiesSerializer;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotAllocationResponse;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

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
      SlotAllocationResponse response = nodeRegistry.findFreeMatchingSlot(capabilities);
      if (response.getStatus() == SlotAllocationResponse.Status.OK) {
        log.info("Slot found " + response.getSlotInfo());
        curator.setData(nodeSlotStatePath(response.getSlotInfo()), "busy");
        response.getSlotInfo().setBusy(true);
      } else {
        log.info("No slot found");
      }
      curator.setData(clientAllocatedSlotPath(clientId), new BeanToJsonConverter().convert(response));
      curator.clearBarrier(clientPath(clientId));
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
      System.out.println("!!!" + connectionState);
    }
  }

}
