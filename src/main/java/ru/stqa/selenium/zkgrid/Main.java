package ru.stqa.selenium.zkgrid;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import ru.stqa.selenium.zkgrid.hub.Hub;
import ru.stqa.selenium.zkgrid.hub.HubParameters;
import ru.stqa.selenium.zkgrid.node.Node;
import ru.stqa.selenium.zkgrid.node.NodeParameters;

public class Main {

  public static void main(String[] args) throws Exception {

    final Main main = new Main();
    new JCommander(main, args);

    if (main.role == null) {
      throw new Error("Unknown role " + main.role);
    }

    switch (main.role) {
      case HUB: {
        Hub hub = new Hub(main.hubParameters);
        hub.start();
        break;
      }
      case NODE: {
        Node node = new Node(main.nodeParameters);
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
