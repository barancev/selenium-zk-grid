package ru.stqa.selenium.zkgrid.common;

import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.JsonToBeanConverter;

public class CapabilitiesSerializer implements QueueSerializer<Capabilities> {

  @Override
  public byte[] serialize(Capabilities capabilities) {
    return new BeanToJsonConverter().convert(capabilities).getBytes();
  }

  @Override
  public Capabilities deserialize(byte[] bytes) {
    return new JsonToBeanConverter().convert(Capabilities.class, new String(bytes));
  }
}
