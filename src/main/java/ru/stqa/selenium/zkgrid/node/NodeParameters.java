package ru.stqa.selenium.zkgrid.node;

import com.beust.jcommander.Parameter;

public class NodeParameters {

  @Parameter(names = "-hub", description = "(node) The hub address in format host:port")
  private String hubConnectionString = "localhost:4444";

  public String getHubConnectionString() {
    return hubConnectionString;
  }
}
