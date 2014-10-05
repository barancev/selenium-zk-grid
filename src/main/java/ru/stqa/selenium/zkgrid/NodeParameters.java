package ru.stqa.selenium.zkgrid;

import com.beust.jcommander.Parameter;

public class NodeParameters {

  @Parameter(names = "-hub", description = "(node) The hub address in format host:port")
  String hubConnectionString = "localhost:4444";

}
