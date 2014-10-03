package ru.stqa.selenium.common;

import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.JsonToBeanConverter;

public class StringSerializer implements QueueSerializer<String> {

  @Override
  public byte[] serialize(String s) {
    return s.getBytes();
  }

  @Override
  public String deserialize(byte[] bytes) {
    return new String(bytes);
  }
}
