package ru.stqa.selenium.zkgrid;

import com.beust.jcommander.Parameter;

public class HubParameters {

  @Parameter(names = "-port", description = "(hub) The port nodes should connect to")
  private String port = "4444";

  public String getPort() {
    return port;
  }
}
