package ru.stqa.selenium.zkgrid.hub;

import com.beust.jcommander.Parameter;

public class HubParameters {

  @Parameter(names = "-port", description = "(hub) The port nodes should connect to")
  private int port = 4444;

  @Parameter(names = "-heartBeatPeriod", description = "(hub) Nodes heartbeat period, in seconds")
  private long heartBeatPeriod = 2;

  public int getPort() {
    return port;
  }

  public long getHeartBeatPeriod() {
    return heartBeatPeriod;
  }
}
