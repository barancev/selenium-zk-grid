package ru.stqa.selenium.zkgrid.client;

import com.google.common.base.Throwables;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.CapabilitiesSerializer;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotAllocationResponse;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.openqa.selenium.remote.DriverCommand.GET_ALL_SESSIONS;
import static org.openqa.selenium.remote.DriverCommand.NEW_SESSION;
import static org.openqa.selenium.remote.DriverCommand.QUIT;
import static ru.stqa.selenium.zkgrid.common.PathUtils.*;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotResponsePath;

public class CuratorCommandExecutor implements CommandExecutor {

  private static Logger log = LoggerFactory.getLogger(CuratorCommandExecutor.class);

  private String clientId = UUID.randomUUID().toString();

  private final Curator curator;
  private SlotInfo slot;
  private String sessionId;

  public CuratorCommandExecutor(String connectionString) throws InterruptedException {
    curator = Curator.createCurator(connectionString, log);
    curator.start();
  }

  @Override
  public Response execute(Command command) throws IOException {
    if (sessionId == null) {
      if (QUIT.equals(command.getName())) {
        return new Response();
      }

      if (!GET_ALL_SESSIONS.equals(command.getName()) && !NEW_SESSION.equals(command.getName())) {
        throw new SessionNotFoundException("Session ID is null. Using WebDriver after calling quit()?");
      }
    }

    if (NEW_SESSION.equals(command.getName())) {
      try {
        slot = allocateSlot((Capabilities) command.getParameters().get("desiredCapabilities"));
      } catch (Exception ex) {
        throw Throwables.propagate(ex);
      }
    }

    Response res;
    try {
      res = sendCommand(command);
    } catch (Exception ex) {
      throw Throwables.propagate(ex);
    }

    if (NEW_SESSION.equals(command.getName())) {
      if (res.getStatus() == ErrorCodes.SUCCESS) {
        sessionId = res.getSessionId();
      }
    } else if (QUIT.equals(command.getName())) {
      if (res.getStatus() == ErrorCodes.SUCCESS) {
        sessionId = null;
      }
    }

    return res;
  }

  private SlotInfo allocateSlot(final Capabilities capabilities) throws Exception {
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
      return slot;
    } else {
      throw new SessionNotCreatedException(response.getMessage());
    }
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

}
