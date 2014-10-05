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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.internal.WrapsDriver;
import org.openqa.selenium.remote.SessionId;
import shaded.org.openqa.selenium.remote.server.rest.RestishHandler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public abstract class WebDriverHandler<T> implements RestishHandler<T>, Callable<T> {

  private final shaded.org.openqa.selenium.remote.server.Session session;

  protected WebDriverHandler(shaded.org.openqa.selenium.remote.server.Session session) {
    this.session = session;
  }

  @Override
  public final T handle() throws Exception {
    FutureTask<T> future = new FutureTask<T>(this);
    try {
      return getSession().execute(future);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception)
        throw (Exception) cause;
      throw e;
    }
  }

  public SessionId getSessionId() {
    return session.getSessionId();
  }

  public String getScreenshot() {
    shaded.org.openqa.selenium.remote.server.Session session = getSession();
    return session != null ? session.getAndClearScreenshot() : null;
  }

  protected WebDriver getDriver() {
    shaded.org.openqa.selenium.remote.server.Session session = getSession();
    return session.getDriver();
  }

  protected shaded.org.openqa.selenium.remote.server.Session getSession() {
    return session;
  }

  protected shaded.org.openqa.selenium.remote.server.KnownElements getKnownElements() {
    return getSession().getKnownElements();
  }

  protected SessionId getRealSessionId() {
    return session == null ? new SessionId("unknown") : session.getSessionId();
  }

  protected BySelector newBySelector() {
    return new BySelector();
  }

  protected WebDriver getUnwrappedDriver() {
    WebDriver toReturn = getDriver();
    while (toReturn instanceof WrapsDriver) {
      toReturn = ((WrapsDriver) toReturn).getWrappedDriver();
    }
    return toReturn;
  }
}
