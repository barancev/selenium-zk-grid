package ru.stqa.selenium.zkgrid;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.internal.Lists;
import com.google.common.io.Files;
import ru.stqa.selenium.zkgrid.hub.Hub;
import ru.stqa.selenium.zkgrid.node.Node;

import java.util.List;
import java.util.Properties;

public class Main {

  public static void main(String[] args) throws Exception {

    final Main main = new Main();
    new JCommander(main, args);

    if (main.role == null) {
      throw new Error("Unknown role " + main.role);
    }

    switch (main.role) {
      case HUB: {
        Properties properties = new Properties(){{
          setProperty("dataDir", Files.createTempDir().getAbsolutePath());
          setProperty("clientPort", main.hubParameters.getPort());
          setProperty("server.1", "localhost:5444:6444");
        }};
        Hub hub = new Hub(properties);
        hub.start();
        break;
      }
      case NODE: {
        Node node = new Node(main.nodeParameters.hubConnectionString);
        node.start();
        break;
      }
      default: {
        throw new Error("Unknown role " + main.role);
      }
    }
  }

  @Parameter(names = "-role", description = "\"hub\" or \"node\"", converter = RoleConverter.class)
  private Role role;

  @ParametersDelegate
  private HubParameters hubParameters = new HubParameters();

  @ParametersDelegate
  private NodeParameters nodeParameters = new NodeParameters();

  @Parameter
  List<String> otherParameters = Lists.newArrayList();

  private enum Role {
    HUB, NODE
  }

  public static class RoleConverter implements IStringConverter<Role> {
    @Override
    public Role convert(String s) {
      return Role.valueOf(s.toUpperCase());
    }
  }
}
