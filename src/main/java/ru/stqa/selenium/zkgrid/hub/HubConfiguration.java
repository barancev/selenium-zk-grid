package ru.stqa.selenium.zkgrid.hub;

public class HubConfiguration {

  long heartBeatPeriod = 2;
  long nodeLostTimeout = 10;
  long nodeDeadTimeout = 20;
  String capabilityMatcher = DefaultCapabilityMatcher.class.getName();

}
