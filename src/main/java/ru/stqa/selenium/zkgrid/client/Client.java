package ru.stqa.selenium.zkgrid.client;

import com.beust.jcommander.internal.Maps;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.CapabilitiesSerializer;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotAllocationResponse;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

public class Client {

  private static Logger log = LoggerFactory.getLogger(Client.class);

  private String clientId = UUID.randomUUID().toString();

  private final Curator curator;

  private SlotInfo slot;
  private String sessionId;

  public static void main(String[] args) throws Exception {
    Client wdClient = new Client("localhost:4444");
    DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    capabilities.setVersion("32");
    wdClient.startNewSession(capabilities);
    for (int i = 0; i < 1; i++) {
      wdClient.get("http://localhost/");
      Thread.sleep(2000);
    }
    wdClient.quit();
  }

  public Client(String connectionString) throws InterruptedException {
    curator = Curator.createCurator(connectionString, log);
    curator.start();
  }

  private void startNewSession(final Capabilities capabilities) throws Exception {
    DistributedBarrier barrier = curator.createBarrier(clientPath(clientId));

    DistributedQueue<Capabilities> queue = QueueBuilder.builder(
        curator.getClient(), null, new CapabilitiesSerializer(), "/newSessionRequests").buildQueue();
    queue.start();
    DesiredCapabilities capabilitiesCopy = new DesiredCapabilities(capabilities);
    capabilitiesCopy.setCapability("zk-grid.clientId", clientId);
    queue.put(capabilitiesCopy);

    if (! barrier.waitOnBarrier(10, TimeUnit.SECONDS)) {
      throw new Error("Slot allocation timeout");
    }

    SlotAllocationResponse response = new JsonToBeanConverter().convert(
        SlotAllocationResponse.class, curator.getDataForPath(clientAllocatedSlotPath(clientId)));

    if (response.getStatus() == SlotAllocationResponse.Status.OK) {
      slot = response.getSlotInfo();
      log.info("Slot allocated " + response.getSlotInfo());
    } else {
      log.info(response.getMessage());
      System.exit(0);
    }

    Response res = sendCommand(new Command(null, DriverCommand.NEW_SESSION, new HashMap<String, Object>(){{
      put("desiredCapabilities", capabilities);
    }}));

    sessionId = res.getSessionId();
  }

  private Response sendCommand(Command command) throws Exception {
    log.info("Sending command " + command);
    DistributedBarrier barrier = curator.createBarrier(nodeSlotPath(slot));

    curator.setData(nodeSlotCommandPath(slot), new BeanToJsonConverter().convert(command));

    if (! barrier.waitOnBarrier(120, TimeUnit.SECONDS)) {
      throw new Error("Command execution timeout");
    }

    Response res = new JsonToBeanConverter().convert(Response.class, curator.getDataForPath(nodeSlotResponsePath(slot)));
    log.info("Response is " + res);
    return res;
  }

  private void get(String url) throws Exception {
    Map<String, Object> parameters = Maps.newHashMap();
    parameters.put("url", url);
    Response res = sendCommand(new Command(new SessionId(sessionId), DriverCommand.GET, parameters));
  }

  private void quit() throws Exception {
    Response res = sendCommand(new Command(new SessionId(sessionId), DriverCommand.QUIT));
  }

}
