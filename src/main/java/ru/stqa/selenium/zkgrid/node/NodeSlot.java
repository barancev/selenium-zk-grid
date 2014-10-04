package ru.stqa.selenium.zkgrid.node;

import org.openqa.selenium.remote.*;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotPath;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotResponsePath;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotStatePath;

public class NodeSlot {

  private Curator curator;
  private final SlotInfo slotInfo;
  private CommandHandler commandHandler;

  private final ScheduledExecutorService serviceExecutor;
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
    serviceExecutor.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        try {
          if (executingCommand) {
            System.out.println("!!! Executing previous command, ignore");
            return null;
          }

          if (sessionId != null && !sessionId.equals(cmd.getSessionId().toString())) {
            System.out.println("!!! Command dispatched to a wrong slot");
            return null;
          }

          setBusyState();

          executingCommand = true;

          Response res = commandHandler.handleCommand(cmd);

          if (DriverCommand.NEW_SESSION.equals(cmd.getName())) {
            if (ErrorCodes.SUCCESS_STRING.equals(res.getState())) {
              sessionId = res.getSessionId();
              setBusyState();

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
          return null;
        } finally {
          executingCommand = false;
        }
      }
    });
  }

  private void setBusyState() throws Exception {
    curator.setData(nodeSlotStatePath(slotInfo), "busy");
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
