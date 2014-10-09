package ru.stqa.selenium.zkgrid.node;

import org.openqa.selenium.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.concurrent.*;

import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotPath;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotResponsePath;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotStatePath;

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

  public void processCommand(final Command cmd) throws Exception {
    currentCommand = serviceExecutor.submit(new Runnable() {
      public void run() {
        try {
          if (executingCommand) {
            log.warn("!!! Executing previous command, ignore");
          }

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
            if (ErrorCodes.SUCCESS_STRING.equals(res.getState())) {
              sessionId = null;
              setFreeState();
            }
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
      log.info("No session on slot " + slotInfo);
      return;
    }
    if (executingCommand) {
      currentCommand.cancel(true);
    }
    serviceExecutor.submit(new Runnable() {
      @Override
      public void run() {
        log.info("Killing session " + sessionId + " on slot " + slotInfo);
        commandHandler.handleCommand(new Command(new SessionId(sessionId), DriverCommand.QUIT));
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
