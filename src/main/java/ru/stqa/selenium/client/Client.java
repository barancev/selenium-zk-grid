package ru.stqa.selenium.client;

import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.DesiredCapabilities;
import ru.stqa.selenium.common.CapabilitiesSerializer;
import ru.stqa.selenium.common.Curator;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.stqa.selenium.common.PathUtils.*;

public class Client {

  private String clientId = UUID.randomUUID().toString();

  private final Curator curator;

  private String sessionId;

  public static void main(String[] args) throws Exception {
    Client wdClient = new Client("localhost:4444");
    wdClient.startNewSession(DesiredCapabilities.firefox());
    //client.sendCommand("test");
    //client.quit();
  }

  public Client(String connectionString) throws InterruptedException {
    curator = Curator.createCurator(connectionString);
  }

  private void startNewSession(Capabilities capabilities) throws Exception {
    DistributedBarrier barrier = curator.createBarrier(clientPath(clientId));

    DistributedQueue<Capabilities> queue = QueueBuilder.builder(
        curator.getClient(), null, new CapabilitiesSerializer(), "/newSessionRequests").buildQueue();
    queue.start();
    DesiredCapabilities capabilitiesCopy = new DesiredCapabilities(capabilities);
    capabilitiesCopy.setCapability("zk-grid.clientId", clientId);
    queue.put(capabilitiesCopy);

    if (! barrier.waitOnBarrier(10, TimeUnit.SECONDS)) {
      throw new Error("Session creation timeout");
    }

    sessionId = curator.getDataForPath(clientNewSessionIdPath(clientId));
    System.out.println("Session created " + sessionId);
  }

  private void sendCommand(String command) throws Exception {
//    client.create().creatingParentsIfNeeded().forPath(sessionRequestPath(sessionId), command.getBytes());
//    String result = new String(client.getData().watched().forPath(sessionResponsePath(sessionId)));
//    System.out.println(result);
//    client.delete().forPath(sessionRequestPath(sessionId));
//    client.delete().forPath(sessionResponsePath(sessionId));
  }

  private void quit() throws Exception {
    sendCommand("quit");
//    client.delete().forPath(sessionPath(sessionId));
  }

}
