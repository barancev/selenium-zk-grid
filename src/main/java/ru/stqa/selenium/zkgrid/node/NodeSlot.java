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
  private final ExecutorService commandExecutor;

  private Future<Response> currentCommand;
  private boolean executingCommand;
  private String sessionId;
  private long commandExecutionTimeout;
  private long clientInactivityTimeout;
  private Future<?> clientInactivityWatcher;

  public static class Builder {
    private Curator curator;
    private SlotInfo slotInfo;
    private CommandHandler commandHandler;

    private long commandExecutionTimeout;
    private long clientInactivityTimeout;

    public Builder(Curator curator, SlotInfo slotInfo, CommandHandler commandHandler) {
      this.curator = curator;
      this.slotInfo = slotInfo;
      this.commandHandler = commandHandler;
    }

    public Builder withCommandExecutionTimeout(long lostTimeout, TimeUnit timeUnit) {
      this.commandExecutionTimeout = timeUnit.toMillis(lostTimeout);
      return this;
    }

    public Builder withClientInactivityTimeout(long inactivityTimeout, TimeUnit timeUnit) {
      this.clientInactivityTimeout = timeUnit.toMillis(inactivityTimeout);
      return this;
    }

    public NodeSlot create() throws Exception {
      log.debug("Creating NodeSlot");
      NodeSlot slot = new NodeSlot(curator, slotInfo, commandHandler);
      slot.setCommandExecutionTimeout(commandExecutionTimeout);
      slot.setClientInactivityTimeout(clientInactivityTimeout);
      log.debug("NodeSlot created");
      return slot;
    }
  }

  private NodeSlot(Curator curator, SlotInfo slotInfo, CommandHandler commandHandler) {
    this.curator = curator;
    this.slotInfo = slotInfo;
    this.commandHandler = commandHandler;

    serviceExecutor = Executors.newSingleThreadScheduledExecutor();
    commandExecutor = Executors.newSingleThreadExecutor();
  }

  public void setCommandExecutionTimeout(long commandExecutionTimeout) {
    this.commandExecutionTimeout = commandExecutionTimeout;
  }

  public void setClientInactivityTimeout(long clientInactivityTimeout) {
    this.clientInactivityTimeout = clientInactivityTimeout;
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
        log.info("Slot {} received a command {}", slotInfo, cmd);
        processCommand(cmd);
      }
    };

    nodeCache.getListenable().addListener(nodesListener);
  }

  private void processCommand(final Command cmd) throws Exception {
    if (clientInactivityWatcher != null) {
      clientInactivityWatcher.cancel(false);
    }
    serviceExecutor.submit(new Runnable() {
      public void run() {
        try {
          if (sessionId != null && !sessionId.equals(cmd.getSessionId().toString())) {
            log.warn("!!! Command dispatched to a wrong slot");
          }

          executingCommand = true;

          setBusyState();

          currentCommand = commandExecutor.submit(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
              return commandHandler.handleCommand(cmd);
            }
          });

          Response res;
          try {
            res = currentCommand.get(commandExecutionTimeout, TimeUnit.MILLISECONDS);
          } catch (TimeoutException to) {
            res = new Response();
            res.setStatus(ErrorCodes.TIMEOUT);
            res.setValue(to);
          }

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

          clientInactivityWatcher = serviceExecutor.schedule(
              new Runnable() {
                @Override
                public void run() {
                  destroySession();
                }
              }, clientInactivityTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          executingCommand = false;
          currentCommand = null;
        }
      }
    });
  }

  public void destroySession() {
    if (sessionId == null) {
      log.info("No session on slot {}", slotInfo);
      return;
    }
    if (executingCommand && currentCommand != null) {
      currentCommand.cancel(true);
    }
    commandExecutor.submit(new Runnable() {
      @Override
      public void run() {
        log.info("Killing session {} on slot {}", sessionId, slotInfo);
        commandHandler.handleCommand(new Command(new SessionId(sessionId), DriverCommand.QUIT));
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
