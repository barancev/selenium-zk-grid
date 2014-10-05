/*
Copyright 2007-2009 Selenium committers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package shaded.org.openqa.selenium.remote.server.handler;

import org.openqa.selenium.WebElement;

public class GetCssProperty extends WebElementHandler<String> {
  private volatile String propertyName;

  public GetCssProperty(shaded.org.openqa.selenium.remote.server.Session session) {
    super(session);
  }

  public void setPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }

  @Override
  public String call() throws Exception {
    WebElement element = getElement();
    return element.getCssValue(propertyName);
  }

  @Override
  public String toString() {
    return String.format("[get value of css property: %s, %s]", getElementAsString(), propertyName);
  }
}
