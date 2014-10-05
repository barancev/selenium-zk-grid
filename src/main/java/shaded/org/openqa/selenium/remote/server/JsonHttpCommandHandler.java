/*
Copyright 2012 Selenium committers
Copyright 2012 Software Freedom Conservancy

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

package shaded.org.openqa.selenium.remote.server;

import static org.openqa.selenium.remote.DriverCommand.*;
import static org.openqa.selenium.remote.http.HttpMethod.POST;

import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.ErrorCodes;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.JsonHttpCommandCodec;
import org.openqa.selenium.remote.http.JsonHttpResponseCodec;
import shaded.org.openqa.selenium.remote.server.handler.AcceptAlert;
import shaded.org.openqa.selenium.remote.server.handler.AddCookie;
import shaded.org.openqa.selenium.remote.server.handler.ChangeUrl;
import shaded.org.openqa.selenium.remote.server.handler.FindActiveElement;
import shaded.org.openqa.selenium.remote.server.handler.FindChildElement;
import shaded.org.openqa.selenium.remote.server.handler.FindChildElements;
import shaded.org.openqa.selenium.remote.server.handler.FindElement;
import shaded.org.openqa.selenium.remote.server.handler.FindElements;
import shaded.org.openqa.selenium.remote.server.handler.GetAllSessions;
import shaded.org.openqa.selenium.remote.server.handler.GetAllWindowHandles;
import shaded.org.openqa.selenium.remote.server.handler.GetCssProperty;
import shaded.org.openqa.selenium.remote.server.handler.GetElementSelected;
import shaded.org.openqa.selenium.remote.server.handler.GetSessionCapabilities;
import shaded.org.openqa.selenium.remote.server.handler.GetTagName;
import shaded.org.openqa.selenium.remote.server.handler.GoBack;
import shaded.org.openqa.selenium.remote.server.handler.ImeGetActiveEngine;
import shaded.org.openqa.selenium.remote.server.handler.ImplicitlyWait;
import shaded.org.openqa.selenium.remote.server.handler.RefreshPage;
import shaded.org.openqa.selenium.remote.server.handler.Rotate;
import shaded.org.openqa.selenium.remote.server.handler.SendKeys;
import shaded.org.openqa.selenium.remote.server.handler.SetAlertText;
import shaded.org.openqa.selenium.remote.server.handler.SwitchToParentFrame;
import shaded.org.openqa.selenium.remote.server.handler.UploadFile;
import shaded.org.openqa.selenium.remote.server.handler.html5.ClearSessionStorage;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetAppCacheStatus;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageKeys;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageSize;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetLocationContext;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageItem;
import shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageKeys;
import shaded.org.openqa.selenium.remote.server.handler.html5.RemoveSessionStorageItem;
import shaded.org.openqa.selenium.remote.server.handler.html5.SetLocalStorageItem;
import shaded.org.openqa.selenium.remote.server.handler.html5.SetSessionStorageItem;
import shaded.org.openqa.selenium.remote.server.handler.interactions.ClickInSession;
import shaded.org.openqa.selenium.remote.server.handler.interactions.DoubleClickInSession;
import shaded.org.openqa.selenium.remote.server.handler.interactions.MouseDown;
import shaded.org.openqa.selenium.remote.server.handler.interactions.MouseUp;
import shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Flick;
import shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Up;
import shaded.org.openqa.selenium.remote.server.rest.RestishHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class JsonHttpCommandHandler {

  private static final String ADD_CONFIG_COMMAND_NAME = "-selenium-add-config";

  private final DriverSessions sessions;
  private final Logger log;
  private final JsonHttpCommandCodec commandCodec;
  private final JsonHttpResponseCodec responseCodec;
  private final Map<String, shaded.org.openqa.selenium.remote.server.rest.ResultConfig> configs = new LinkedHashMap<String, shaded.org.openqa.selenium.remote.server.rest.ResultConfig>();
  private final ErrorCodes errorCodes = new ErrorCodes();

  public JsonHttpCommandHandler(DriverSessions sessions, Logger log) {
    this.sessions = sessions;
    this.log = log;
    this.commandCodec = new JsonHttpCommandCodec();
    this.responseCodec = new JsonHttpResponseCodec();
    setUpMappings();
  }

  public void addNewMapping(
      String commandName, Class<? extends RestishHandler<?>> implementationClass) {
    shaded.org.openqa.selenium.remote.server.rest.ResultConfig config = new shaded.org.openqa.selenium.remote.server.rest.ResultConfig(commandName, implementationClass, sessions, log);
    configs.put(commandName, config);
  }

  public HttpResponse handleRequest(HttpRequest request) {
    log.fine(String.format("Handling: %s %s", request.getMethod(), request.getUri()));

    Command command = null;
    Response response;
    try {
      command = commandCodec.decode(request);
      shaded.org.openqa.selenium.remote.server.rest.ResultConfig config = configs.get(command.getName());
      if (config == null) {
        throw new UnsupportedCommandException();
      }
      response = config.handle(command);
      log.fine(String.format("Finished: %s %s", request.getMethod(), request.getUri()));
    } catch (Exception e) {
      log.fine(String.format("Error on: %s %s", request.getMethod(), request.getUri()));
      response = new Response();
      response.setStatus(errorCodes.toStatusCode(e));
      response.setState(errorCodes.toState(response.getStatus()));
      response.setValue(e);

      if (command != null && command.getSessionId() != null) {
        response.setSessionId(command.getSessionId().toString());
      }
    }
    return responseCodec.encode(response);
  }

  private void setUpMappings() {
    commandCodec.defineCommand(ADD_CONFIG_COMMAND_NAME, POST, "/config/drivers");
    addNewMapping(ADD_CONFIG_COMMAND_NAME, shaded.org.openqa.selenium.remote.server.handler.AddConfig.class);

    addNewMapping(STATUS, shaded.org.openqa.selenium.remote.server.handler.Status.class);
    addNewMapping(GET_ALL_SESSIONS, GetAllSessions.class);
    addNewMapping(NEW_SESSION, shaded.org.openqa.selenium.remote.server.handler.NewSession.class);
    addNewMapping(GET_CAPABILITIES, GetSessionCapabilities.class);
    addNewMapping(QUIT, shaded.org.openqa.selenium.remote.server.handler.DeleteSession.class);

    addNewMapping(GET_CURRENT_WINDOW_HANDLE, shaded.org.openqa.selenium.remote.server.handler.GetCurrentWindowHandle.class);
    addNewMapping(GET_WINDOW_HANDLES, GetAllWindowHandles.class);

    addNewMapping(DISMISS_ALERT, shaded.org.openqa.selenium.remote.server.handler.DismissAlert.class);
    addNewMapping(ACCEPT_ALERT, AcceptAlert.class);
    addNewMapping(GET_ALERT_TEXT, shaded.org.openqa.selenium.remote.server.handler.GetAlertText.class);
    addNewMapping(SET_ALERT_VALUE, SetAlertText.class);

    addNewMapping(GET, ChangeUrl.class);
    addNewMapping(GET_CURRENT_URL, shaded.org.openqa.selenium.remote.server.handler.GetCurrentUrl.class);
    addNewMapping(GO_FORWARD, shaded.org.openqa.selenium.remote.server.handler.GoForward.class);
    addNewMapping(GO_BACK, GoBack.class);
    addNewMapping(REFRESH, RefreshPage.class);

    addNewMapping(EXECUTE_SCRIPT, shaded.org.openqa.selenium.remote.server.handler.ExecuteScript.class);
    addNewMapping(EXECUTE_ASYNC_SCRIPT, shaded.org.openqa.selenium.remote.server.handler.ExecuteAsyncScript.class);

    addNewMapping(GET_PAGE_SOURCE, shaded.org.openqa.selenium.remote.server.handler.GetPageSource.class);

    addNewMapping(SCREENSHOT, shaded.org.openqa.selenium.remote.server.handler.CaptureScreenshot.class);

    addNewMapping(GET_TITLE, shaded.org.openqa.selenium.remote.server.handler.GetTitle.class);

    addNewMapping(FIND_ELEMENT, FindElement.class);
    addNewMapping(FIND_ELEMENTS, FindElements.class);
    addNewMapping(GET_ACTIVE_ELEMENT, FindActiveElement.class);

    addNewMapping(FIND_CHILD_ELEMENT, FindChildElement.class);
    addNewMapping(FIND_CHILD_ELEMENTS, FindChildElements.class);

    addNewMapping(CLICK_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.ClickElement.class);
    addNewMapping(GET_ELEMENT_TEXT, shaded.org.openqa.selenium.remote.server.handler.GetElementText.class);
    addNewMapping(SUBMIT_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.SubmitElement.class);

    addNewMapping(UPLOAD_FILE, UploadFile.class);
    addNewMapping(SEND_KEYS_TO_ELEMENT, SendKeys.class);
    addNewMapping(GET_ELEMENT_TAG_NAME, GetTagName.class);

    addNewMapping(CLEAR_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.ClearElement.class);
    addNewMapping(IS_ELEMENT_SELECTED, GetElementSelected.class);
    addNewMapping(IS_ELEMENT_ENABLED, shaded.org.openqa.selenium.remote.server.handler.GetElementEnabled.class);
    addNewMapping(IS_ELEMENT_DISPLAYED, shaded.org.openqa.selenium.remote.server.handler.GetElementDisplayed.class);
    addNewMapping(GET_ELEMENT_LOCATION, shaded.org.openqa.selenium.remote.server.handler.GetElementLocation.class);
    addNewMapping(GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW, shaded.org.openqa.selenium.remote.server.handler.GetElementLocationInView.class);
    addNewMapping(GET_ELEMENT_SIZE, shaded.org.openqa.selenium.remote.server.handler.GetElementSize.class);
    addNewMapping(GET_ELEMENT_VALUE_OF_CSS_PROPERTY, GetCssProperty.class);

    addNewMapping(GET_ELEMENT_ATTRIBUTE, shaded.org.openqa.selenium.remote.server.handler.GetElementAttribute.class);
    addNewMapping(ELEMENT_EQUALS, shaded.org.openqa.selenium.remote.server.handler.ElementEquality.class);

    addNewMapping(GET_ALL_COOKIES, shaded.org.openqa.selenium.remote.server.handler.GetAllCookies.class);
    addNewMapping(ADD_COOKIE, AddCookie.class);
    addNewMapping(DELETE_ALL_COOKIES, shaded.org.openqa.selenium.remote.server.handler.DeleteCookie.class);
    addNewMapping(DELETE_COOKIE, shaded.org.openqa.selenium.remote.server.handler.DeleteNamedCookie.class);

    addNewMapping(SWITCH_TO_FRAME, shaded.org.openqa.selenium.remote.server.handler.SwitchToFrame.class);
    addNewMapping(SWITCH_TO_PARENT_FRAME, SwitchToParentFrame.class);
    addNewMapping(SWITCH_TO_WINDOW, shaded.org.openqa.selenium.remote.server.handler.SwitchToWindow.class);
    addNewMapping(CLOSE, shaded.org.openqa.selenium.remote.server.handler.CloseWindow.class);

    addNewMapping(GET_WINDOW_SIZE, shaded.org.openqa.selenium.remote.server.handler.GetWindowSize.class);
    addNewMapping(SET_WINDOW_SIZE, shaded.org.openqa.selenium.remote.server.handler.SetWindowSize.class);
    addNewMapping(GET_WINDOW_POSITION, shaded.org.openqa.selenium.remote.server.handler.GetWindowPosition.class);
    addNewMapping(SET_WINDOW_POSITION, shaded.org.openqa.selenium.remote.server.handler.SetWindowPosition.class);
    addNewMapping(MAXIMIZE_WINDOW, shaded.org.openqa.selenium.remote.server.handler.MaximizeWindow.class);

    addNewMapping(SET_TIMEOUT, shaded.org.openqa.selenium.remote.server.handler.ConfigureTimeout.class);
    addNewMapping(IMPLICITLY_WAIT, ImplicitlyWait.class);
    addNewMapping(SET_SCRIPT_TIMEOUT, shaded.org.openqa.selenium.remote.server.handler.SetScriptTimeout.class);

    addNewMapping(EXECUTE_SQL, shaded.org.openqa.selenium.remote.server.handler.html5.ExecuteSQL.class);

    addNewMapping(GET_LOCATION, GetLocationContext.class);
    addNewMapping(SET_LOCATION,  shaded.org.openqa.selenium.remote.server.handler.html5.SetLocationContext.class);

    addNewMapping(GET_APP_CACHE_STATUS, GetAppCacheStatus.class);

    addNewMapping(GET_LOCAL_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageItem.class);
    addNewMapping(REMOVE_LOCAL_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.RemoveLocalStorageItem.class);
    addNewMapping(GET_LOCAL_STORAGE_KEYS, GetLocalStorageKeys.class);
    addNewMapping(SET_LOCAL_STORAGE_ITEM, SetLocalStorageItem.class);
    addNewMapping(CLEAR_LOCAL_STORAGE, shaded.org.openqa.selenium.remote.server.handler.html5.ClearLocalStorage.class);
    addNewMapping(GET_LOCAL_STORAGE_SIZE, GetLocalStorageSize.class);

    addNewMapping(GET_SESSION_STORAGE_ITEM, GetSessionStorageItem.class);
    addNewMapping(REMOVE_SESSION_STORAGE_ITEM, RemoveSessionStorageItem.class);
    addNewMapping(GET_SESSION_STORAGE_KEYS, GetSessionStorageKeys.class);
    addNewMapping(SET_SESSION_STORAGE_ITEM, SetSessionStorageItem.class);
    addNewMapping(CLEAR_SESSION_STORAGE, ClearSessionStorage.class);
    addNewMapping(GET_SESSION_STORAGE_SIZE, shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageSize.class);

    addNewMapping(GET_SCREEN_ORIENTATION, shaded.org.openqa.selenium.remote.server.handler.GetScreenOrientation.class);
    addNewMapping(SET_SCREEN_ORIENTATION, Rotate.class);

    addNewMapping(MOVE_TO, shaded.org.openqa.selenium.remote.server.handler.interactions.MouseMoveToLocation.class);
    addNewMapping(CLICK, ClickInSession.class);
    addNewMapping(DOUBLE_CLICK, DoubleClickInSession.class);
    addNewMapping(MOUSE_DOWN, MouseDown.class);
    addNewMapping(MOUSE_UP, MouseUp.class);
    addNewMapping(SEND_KEYS_TO_ACTIVE_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.interactions.SendKeyToActiveElement.class);

    addNewMapping(IME_GET_AVAILABLE_ENGINES, shaded.org.openqa.selenium.remote.server.handler.ImeGetAvailableEngines.class);
    addNewMapping(IME_GET_ACTIVE_ENGINE, ImeGetActiveEngine.class);
    addNewMapping(IME_IS_ACTIVATED, shaded.org.openqa.selenium.remote.server.handler.ImeIsActivated.class);
    addNewMapping(IME_DEACTIVATE, shaded.org.openqa.selenium.remote.server.handler.ImeDeactivate.class);
    addNewMapping(IME_ACTIVATE_ENGINE, shaded.org.openqa.selenium.remote.server.handler.ImeActivateEngine.class);

    // Advanced Touch API
    addNewMapping(TOUCH_SINGLE_TAP, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.SingleTapOnElement.class);
    addNewMapping(TOUCH_DOWN, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Down.class);
    addNewMapping(TOUCH_UP, Up.class);
    addNewMapping(TOUCH_MOVE, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Move.class);
    addNewMapping(TOUCH_SCROLL, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Scroll.class);
    addNewMapping(TOUCH_DOUBLE_TAP, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.DoubleTapOnElement.class);
    addNewMapping(TOUCH_LONG_PRESS, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.LongPressOnElement.class);
    addNewMapping(TOUCH_FLICK, Flick.class);

    addNewMapping(GET_AVAILABLE_LOG_TYPES, shaded.org.openqa.selenium.remote.server.handler.GetAvailableLogTypesHandler.class);
    addNewMapping(GET_LOG, shaded.org.openqa.selenium.remote.server.handler.GetLogHandler.class);
    addNewMapping(GET_SESSION_LOGS, shaded.org.openqa.selenium.remote.server.handler.GetSessionLogsHandler.class);
  }
}
