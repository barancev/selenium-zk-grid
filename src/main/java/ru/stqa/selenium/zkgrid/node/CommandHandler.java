package ru.stqa.selenium.zkgrid.node;

import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.ErrorCodes;
import org.openqa.selenium.remote.Response;
import shaded.org.openqa.selenium.remote.server.DriverSessions;
import shaded.org.openqa.selenium.remote.server.rest.RestishHandler;
import shaded.org.openqa.selenium.remote.server.rest.ResultConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.openqa.selenium.remote.DriverCommand.*;

public class CommandHandler {

  private static Logger log = LoggerFactory.getLogger(Node.class);

  private final DriverSessions sessions;
  private final Map<String, ResultConfig> configs = new HashMap<String, ResultConfig>();
  private final ErrorCodes errorCodes = new ErrorCodes();

  public CommandHandler(DriverSessions sessions) {
    this.sessions = sessions;
    setUpMappings();
  }

  public Response handleCommand(Command command) {
    log.info("Handling command " + command);

    Response response;
    try {
      ResultConfig config = configs.get(command.getName());
      if (config == null) {
        throw new UnsupportedCommandException();
      }
      response = config.handle(command);
      log.info("Finished " + command);
    } catch (Exception e) {
      log.info(String.format("Error on " + command));
      e.printStackTrace();
      response = new Response();
      response.setStatus(errorCodes.toStatusCode(e));
      response.setState(errorCodes.toState(response.getStatus()));
      response.setValue(e);

      if (command != null && command.getSessionId() != null) {
        response.setSessionId(command.getSessionId().toString());
      }
    }
    return response;
  }

  public void addNewMapping(String commandName, Class<? extends RestishHandler<?>> implementationClass) {
    ResultConfig config = new ResultConfig(commandName, implementationClass, sessions, java.util.logging.Logger.getAnonymousLogger());
    configs.put(commandName, config);
  }

  private void setUpMappings() {
    addNewMapping(STATUS, shaded.org.openqa.selenium.remote.server.handler.Status.class);
    addNewMapping(GET_ALL_SESSIONS, shaded.org.openqa.selenium.remote.server.handler.GetAllSessions.class);
    addNewMapping(NEW_SESSION, shaded.org.openqa.selenium.remote.server.handler.NewSession.class);
    addNewMapping(GET_CAPABILITIES, shaded.org.openqa.selenium.remote.server.handler.GetSessionCapabilities.class);
    addNewMapping(QUIT, shaded.org.openqa.selenium.remote.server.handler.DeleteSession.class);

    addNewMapping(GET_CURRENT_WINDOW_HANDLE, shaded.org.openqa.selenium.remote.server.handler.GetCurrentWindowHandle.class);
    addNewMapping(GET_WINDOW_HANDLES, shaded.org.openqa.selenium.remote.server.handler.GetAllWindowHandles.class);

    addNewMapping(DISMISS_ALERT, shaded.org.openqa.selenium.remote.server.handler.DismissAlert.class);
    addNewMapping(ACCEPT_ALERT, shaded.org.openqa.selenium.remote.server.handler.AcceptAlert.class);
    addNewMapping(GET_ALERT_TEXT, shaded.org.openqa.selenium.remote.server.handler.GetAlertText.class);
    addNewMapping(SET_ALERT_VALUE, shaded.org.openqa.selenium.remote.server.handler.SetAlertText.class);

    addNewMapping(GET, shaded.org.openqa.selenium.remote.server.handler.ChangeUrl.class);
    addNewMapping(GET_CURRENT_URL, shaded.org.openqa.selenium.remote.server.handler.GetCurrentUrl.class);
    addNewMapping(GO_FORWARD, shaded.org.openqa.selenium.remote.server.handler.GoForward.class);
    addNewMapping(GO_BACK, shaded.org.openqa.selenium.remote.server.handler.GoBack.class);
    addNewMapping(REFRESH, shaded.org.openqa.selenium.remote.server.handler.RefreshPage.class);

    addNewMapping(EXECUTE_SCRIPT, shaded.org.openqa.selenium.remote.server.handler.ExecuteScript.class);
    addNewMapping(EXECUTE_ASYNC_SCRIPT, shaded.org.openqa.selenium.remote.server.handler.ExecuteAsyncScript.class);

    addNewMapping(GET_PAGE_SOURCE, shaded.org.openqa.selenium.remote.server.handler.GetPageSource.class);

    addNewMapping(SCREENSHOT, shaded.org.openqa.selenium.remote.server.handler.CaptureScreenshot.class);

    addNewMapping(GET_TITLE, shaded.org.openqa.selenium.remote.server.handler.GetTitle.class);

    addNewMapping(FIND_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.FindElement.class);
    addNewMapping(FIND_ELEMENTS, shaded.org.openqa.selenium.remote.server.handler.FindElements.class);
    addNewMapping(GET_ACTIVE_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.FindActiveElement.class);

    addNewMapping(FIND_CHILD_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.FindChildElement.class);
    addNewMapping(FIND_CHILD_ELEMENTS, shaded.org.openqa.selenium.remote.server.handler.FindChildElements.class);

    addNewMapping(CLICK_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.ClickElement.class);
    addNewMapping(GET_ELEMENT_TEXT, shaded.org.openqa.selenium.remote.server.handler.GetElementText.class);
    addNewMapping(SUBMIT_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.SubmitElement.class);

    addNewMapping(UPLOAD_FILE, shaded.org.openqa.selenium.remote.server.handler.UploadFile.class);
    addNewMapping(SEND_KEYS_TO_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.SendKeys.class);
    addNewMapping(GET_ELEMENT_TAG_NAME, shaded.org.openqa.selenium.remote.server.handler.GetTagName.class);

    addNewMapping(CLEAR_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.ClearElement.class);
    addNewMapping(IS_ELEMENT_SELECTED, shaded.org.openqa.selenium.remote.server.handler.GetElementSelected.class);
    addNewMapping(IS_ELEMENT_ENABLED, shaded.org.openqa.selenium.remote.server.handler.GetElementEnabled.class);
    addNewMapping(IS_ELEMENT_DISPLAYED, shaded.org.openqa.selenium.remote.server.handler.GetElementDisplayed.class);
    addNewMapping(GET_ELEMENT_LOCATION, shaded.org.openqa.selenium.remote.server.handler.GetElementLocation.class);
    addNewMapping(GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW, shaded.org.openqa.selenium.remote.server.handler.GetElementLocationInView.class);
    addNewMapping(GET_ELEMENT_SIZE, shaded.org.openqa.selenium.remote.server.handler.GetElementSize.class);
    addNewMapping(GET_ELEMENT_VALUE_OF_CSS_PROPERTY, shaded.org.openqa.selenium.remote.server.handler.GetCssProperty.class);

    addNewMapping(GET_ELEMENT_ATTRIBUTE, shaded.org.openqa.selenium.remote.server.handler.GetElementAttribute.class);
    addNewMapping(ELEMENT_EQUALS, shaded.org.openqa.selenium.remote.server.handler.ElementEquality.class);

    addNewMapping(GET_ALL_COOKIES, shaded.org.openqa.selenium.remote.server.handler.GetAllCookies.class);
    addNewMapping(ADD_COOKIE, shaded.org.openqa.selenium.remote.server.handler.AddCookie.class);
    addNewMapping(DELETE_ALL_COOKIES, shaded.org.openqa.selenium.remote.server.handler.DeleteCookie.class);
    addNewMapping(DELETE_COOKIE, shaded.org.openqa.selenium.remote.server.handler.DeleteNamedCookie.class);

    addNewMapping(SWITCH_TO_FRAME, shaded.org.openqa.selenium.remote.server.handler.SwitchToFrame.class);
    addNewMapping(SWITCH_TO_PARENT_FRAME, shaded.org.openqa.selenium.remote.server.handler.SwitchToParentFrame.class);
    addNewMapping(SWITCH_TO_WINDOW, shaded.org.openqa.selenium.remote.server.handler.SwitchToWindow.class);
    addNewMapping(CLOSE, shaded.org.openqa.selenium.remote.server.handler.CloseWindow.class);

    addNewMapping(GET_WINDOW_SIZE, shaded.org.openqa.selenium.remote.server.handler.GetWindowSize.class);
    addNewMapping(SET_WINDOW_SIZE, shaded.org.openqa.selenium.remote.server.handler.SetWindowSize.class);
    addNewMapping(GET_WINDOW_POSITION, shaded.org.openqa.selenium.remote.server.handler.GetWindowPosition.class);
    addNewMapping(SET_WINDOW_POSITION, shaded.org.openqa.selenium.remote.server.handler.SetWindowPosition.class);
    addNewMapping(MAXIMIZE_WINDOW, shaded.org.openqa.selenium.remote.server.handler.MaximizeWindow.class);

    addNewMapping(SET_TIMEOUT, shaded.org.openqa.selenium.remote.server.handler.ConfigureTimeout.class);
    addNewMapping(IMPLICITLY_WAIT, shaded.org.openqa.selenium.remote.server.handler.ImplicitlyWait.class);
    addNewMapping(SET_SCRIPT_TIMEOUT, shaded.org.openqa.selenium.remote.server.handler.SetScriptTimeout.class);

    addNewMapping(EXECUTE_SQL, shaded.org.openqa.selenium.remote.server.handler.html5.ExecuteSQL.class);

    addNewMapping(GET_LOCATION, shaded.org.openqa.selenium.remote.server.handler.html5.GetLocationContext.class);
    addNewMapping(SET_LOCATION,  shaded.org.openqa.selenium.remote.server.handler.html5.SetLocationContext.class);

    addNewMapping(GET_APP_CACHE_STATUS, shaded.org.openqa.selenium.remote.server.handler.html5.GetAppCacheStatus.class);

    addNewMapping(GET_LOCAL_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageItem.class);
    addNewMapping(REMOVE_LOCAL_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.RemoveLocalStorageItem.class);
    addNewMapping(GET_LOCAL_STORAGE_KEYS, shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageKeys.class);
    addNewMapping(SET_LOCAL_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.SetLocalStorageItem.class);
    addNewMapping(CLEAR_LOCAL_STORAGE, shaded.org.openqa.selenium.remote.server.handler.html5.ClearLocalStorage.class);
    addNewMapping(GET_LOCAL_STORAGE_SIZE, shaded.org.openqa.selenium.remote.server.handler.html5.GetLocalStorageSize.class);

    addNewMapping(GET_SESSION_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageItem.class);
    addNewMapping(REMOVE_SESSION_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.RemoveSessionStorageItem.class);
    addNewMapping(GET_SESSION_STORAGE_KEYS, shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageKeys.class);
    addNewMapping(SET_SESSION_STORAGE_ITEM, shaded.org.openqa.selenium.remote.server.handler.html5.SetSessionStorageItem.class);
    addNewMapping(CLEAR_SESSION_STORAGE, shaded.org.openqa.selenium.remote.server.handler.html5.ClearSessionStorage.class);
    addNewMapping(GET_SESSION_STORAGE_SIZE, shaded.org.openqa.selenium.remote.server.handler.html5.GetSessionStorageSize.class);

    addNewMapping(GET_SCREEN_ORIENTATION, shaded.org.openqa.selenium.remote.server.handler.GetScreenOrientation.class);
    addNewMapping(SET_SCREEN_ORIENTATION, shaded.org.openqa.selenium.remote.server.handler.Rotate.class);

    addNewMapping(MOVE_TO, shaded.org.openqa.selenium.remote.server.handler.interactions.MouseMoveToLocation.class);
    addNewMapping(CLICK, shaded.org.openqa.selenium.remote.server.handler.interactions.ClickInSession.class);
    addNewMapping(DOUBLE_CLICK, shaded.org.openqa.selenium.remote.server.handler.interactions.DoubleClickInSession.class);
    addNewMapping(MOUSE_DOWN, shaded.org.openqa.selenium.remote.server.handler.interactions.MouseDown.class);
    addNewMapping(MOUSE_UP, shaded.org.openqa.selenium.remote.server.handler.interactions.MouseUp.class);
    addNewMapping(SEND_KEYS_TO_ACTIVE_ELEMENT, shaded.org.openqa.selenium.remote.server.handler.interactions.SendKeyToActiveElement.class);

    addNewMapping(IME_GET_AVAILABLE_ENGINES, shaded.org.openqa.selenium.remote.server.handler.ImeGetAvailableEngines.class);
    addNewMapping(IME_GET_ACTIVE_ENGINE, shaded.org.openqa.selenium.remote.server.handler.ImeGetActiveEngine.class);
    addNewMapping(IME_IS_ACTIVATED, shaded.org.openqa.selenium.remote.server.handler.ImeIsActivated.class);
    addNewMapping(IME_DEACTIVATE, shaded.org.openqa.selenium.remote.server.handler.ImeDeactivate.class);
    addNewMapping(IME_ACTIVATE_ENGINE, shaded.org.openqa.selenium.remote.server.handler.ImeActivateEngine.class);

    // Advanced Touch API
    addNewMapping(TOUCH_SINGLE_TAP, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.SingleTapOnElement.class);
    addNewMapping(TOUCH_DOWN, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Down.class);
    addNewMapping(TOUCH_UP, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Up.class);
    addNewMapping(TOUCH_MOVE, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Move.class);
    addNewMapping(TOUCH_SCROLL, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Scroll.class);
    addNewMapping(TOUCH_DOUBLE_TAP, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.DoubleTapOnElement.class);
    addNewMapping(TOUCH_LONG_PRESS, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.LongPressOnElement.class);
    addNewMapping(TOUCH_FLICK, shaded.org.openqa.selenium.remote.server.handler.interactions.touch.Flick.class);

    addNewMapping(GET_AVAILABLE_LOG_TYPES, shaded.org.openqa.selenium.remote.server.handler.GetAvailableLogTypesHandler.class);
    addNewMapping(GET_LOG, shaded.org.openqa.selenium.remote.server.handler.GetLogHandler.class);
    addNewMapping(GET_SESSION_LOGS, shaded.org.openqa.selenium.remote.server.handler.GetSessionLogsHandler.class);
  }
}
