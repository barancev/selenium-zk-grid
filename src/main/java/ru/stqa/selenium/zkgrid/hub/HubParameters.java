package ru.stqa.selenium.zkgrid.hub;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HubParameters {

  @Parameter(names = "-port", description = "(hub) The port nodes should connect to")
  private int port = 4444;

  @Parameter(names = "-hubConfig", description = "(hub) The hub configuration file")
  private String hubConfig = null;

  public int getPort() {
    return port;
  }

  public HubConfiguration getHubConfiguration() throws IOException {
    InputStream in;
    if (hubConfig != null) {
      in = new FileInputStream(hubConfig);
    } else {
      in = this.getClass().getResourceAsStream("/defaultHubConfig.json");
    }
    return new Gson().fromJson(new InputStreamReader(in), HubConfiguration.class);
  }
}
