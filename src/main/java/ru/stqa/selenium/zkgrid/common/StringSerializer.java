package ru.stqa.selenium.zkgrid.common;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

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
