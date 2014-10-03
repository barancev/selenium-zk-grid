package ru.stqa.selenium.zkgrid.node;

import org.openqa.selenium.remote.*;
import ru.stqa.selenium.zkgrid.common.Curator;
import ru.stqa.selenium.zkgrid.common.SlotInfo;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotPath;
import static ru.stqa.selenium.zkgrid.common.PathUtils.nodeSlotResponsePath;

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

          executingCommand = true;
          Response res = commandHandler.handleCommand(cmd);

          if (DriverCommand.NEW_SESSION.equals(cmd.getName())) {
            if (ErrorCodes.SUCCESS_STRING.equals(res.getState())) {
              sessionId = res.getSessionId();
            }
          } else if (DriverCommand.QUIT.equals(cmd.getName())) {
            if (ErrorCodes.SUCCESS_STRING.equals(res.getState())) {
              sessionId = null;
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
}
