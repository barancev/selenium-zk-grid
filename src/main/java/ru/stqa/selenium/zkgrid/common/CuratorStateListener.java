package ru.stqa.selenium.zkgrid.common;

public interface CuratorStateListener {

  void connectionEstablished();
  void connectionSuspended();
  void connectionRestored();
  void connectionLost();

}
