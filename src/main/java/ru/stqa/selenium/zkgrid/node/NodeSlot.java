package ru.stqa.selenium.zkgrid.node;

import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.openqa.selenium.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.concurrent.*;

import static ru.stqa.selenium.zkgrid.common.PathUtils.*;

public class NodeSlot {

  private static Logger log = LoggerFactory.getLogger(NodeSlot.class);

  private Curator curator;
  private final SlotInfo slotInfo;
  private CommandHandler commandHandler;

  private final ScheduledExecutorService serviceExecutor;
  private Future<?> currentCommand;
  private boolean executingCommand;
  private String sessionId;

  public NodeSlot(Curator curator, SlotInfo slotInfo, CommandHandler commandHandler) {
    this.curator = curator;
    this.slotInfo = slotInfo;
    this.commandHandler = commandHandler;

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
  }

  public SlotInfo getSlotInfo() {
    return slotInfo;
  }

  public void registerToTheHub() {
    log.info("Registering slot {} to the hub", slotInfo);
    try {
      if (curator.checkExists(nodeSlotPath(slotInfo))) {
        log.info("Slot {} is already registered to the hub", slotInfo);
        return;
      }
      curator.setData(nodeSlotPath(slotInfo), new BeanToJsonConverter().convert(slotInfo.getCapabilities()));
      startCommandListener();
    } catch (Exception ex) {
      throw new Error("Can't register slot " + slotInfo, ex);
    }
    log.info("Slot {} registered to the hub", slotInfo);
  }

  private void startCommandListener() throws Exception {
    final NodeCache nodeCache = new NodeCache(curator.getClient(), nodeSlotCommandPath(slotInfo), false);
    nodeCache.start();

    NodeCacheListener nodesListener = new NodeCacheListener () {
      @Override
      public void nodeChanged() throws Exception {
        if (executingCommand) {
          log.warn("A new command detected, but the slot {} is busy executing the previous command");
          return;
        }

        String data = new String(nodeCache.getCurrentData().getData());
        Command cmd = new JsonToBeanConverter().convert(Command.class, data);
        log.info("Command received {}", cmd);
        log.info("Dispatched to slot {}", slotInfo);
        processCommand(cmd);
      }
    };

    nodeCache.getListenable().addListener(nodesListener);
  }

  private void processCommand(final Command cmd) throws Exception {
    currentCommand = serviceExecutor.submit(new Runnable() {
      public void run() {
        try {
          if (sessionId != null && !sessionId.equals(cmd.getSessionId().toString())) {
            log.warn("!!! Command dispatched to a wrong slot");
          }

          setBusyState();

          executingCommand = true;

          Response res = commandHandler.handleCommand(cmd);

          if (DriverCommand.NEW_SESSION.equals(cmd.getName())) {
            log.info("NewSession command executed, " + res.getStatus());
            if (ErrorCodes.SUCCESS == res.getStatus()) {
              sessionId = res.getSessionId();
              log.info("sessionId = " + sessionId);

            } else {
              setFreeState();
            }

          } else if (DriverCommand.QUIT.equals(cmd.getName())) {
            sessionId = null;
            setFreeState();
          }

          curator.setData(nodeSlotResponsePath(slotInfo), new BeanToJsonConverter().convert(res));
          curator.clearBarrier(nodeSlotPath(slotInfo));
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          executingCommand = false;
        }
      }
    });
  }

  public void destroySession() {
    if (sessionId == null) {
      log.info("No session on slot {}", slotInfo);
      return;
    }
    if (executingCommand) {
      currentCommand.cancel(true);
    }
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        log.info("Killing session {} on slot {}", sessionId, slotInfo);
        Response res = commandHandler.handleCommand(new Command(new SessionId(sessionId), DriverCommand.QUIT));
        sessionId = null;
      }
    });
  }

  private void setBusyState() {
    try {
      curator.setData(nodeSlotStatePath(slotInfo), "busy");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setFreeState() {
    serviceExecutor.schedule(new Runnable() {
      @Override
      public void run() {
        try {
          curator.setData(nodeSlotStatePath(slotInfo), "free");
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }, 5, TimeUnit.SECONDS);
  }

}
