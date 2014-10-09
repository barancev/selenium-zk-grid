package ru.stqa.selenium.zkgrid.node;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class NodeParameters {

  @Parameter(names = "-hub", description = "(node) The hub address in format host:port")
  private String hubConnectionString = "localhost:4444";

  @Parameter(names = "-nodeConfig", description = "(node) The node configuration file")
  private String nodeConfig = null;

  public String getHubConnectionString() {
    return hubConnectionString;
  }

  public NodeConfiguration getCodeConfiguration() throws IOException {
    InputStream in;
    if (nodeConfig != null) {
      in = new FileInputStream(nodeConfig);
    } else {
      in = this.getClass().getResourceAsStream("/defaultNodeConfig.json");
    }
    return new Gson().fromJson(new InputStreamReader(in), NodeConfiguration.class);
  }
}
