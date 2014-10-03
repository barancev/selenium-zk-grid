package ru.stqa.selenium.common;

import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.JsonToBeanConverter;

public class CommandSerializer implements QueueSerializer<Command> {

  @Override
  public byte[] serialize(Command cmd) {
    return new BeanToJsonConverter().convert(cmd).getBytes();
  }

  @Override
  public Command deserialize(byte[] bytes) {
    return new JsonToBeanConverter().convert(Command.class, new String(bytes));
  }
}
