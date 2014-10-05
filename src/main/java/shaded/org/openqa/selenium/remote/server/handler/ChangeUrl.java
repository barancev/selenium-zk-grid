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

import java.util.Map;

public class ChangeUrl extends WebDriverHandler<Void> implements shaded.org.openqa.selenium.remote.server.JsonParametersAware {

  private volatile String url;

  public ChangeUrl(shaded.org.openqa.selenium.remote.server.Session session) {
    super(session);
  }

  @Override
  public void setJsonParameters(Map<String, Object> allParameters) throws Exception {
    url = (String) allParameters.get("url");
  }

  @Override
  public Void call() throws Exception {
    getDriver().get(url);

    return null;
  }

  @Override
  public String toString() {
    return "[get: " + url + "]";
  }
}
