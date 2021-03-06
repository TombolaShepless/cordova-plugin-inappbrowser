/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.inappbrowser;

import org.apache.cordova.CordovaWebView;
import android.util.Log;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions.Callback;

public class InAppChromeClient extends WebChromeClient {

    private final String GAP_PROTOCOL = "gap-iab://";
    private final String GAP_NATIVE_PROTOCOL = "gap-iab-native://";

    private NativeScriptResultHandler nativeScriptResultHandler;
    private CordovaWebView webView;
    private String LOG_TAG = "InAppChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;

    public InAppChromeClient(NativeScriptResultHandler nativeScriptResultHandler, CordovaWebView webView) {
        super();
        this.nativeScriptResultHandler =nativeScriptResultHandler;
        this.webView = webView;
    }
    /**
     * Handle database quota exceeded notification.
     *
     * @param url
     * @param databaseIdentifier
     * @param currentQuota
     * @param estimatedSize
     * @param totalUsedQuota
     * @param quotaUpdater
     */
    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)
    {
        Log.d(LOG_TAG, String.format("onExceededDatabaseQuota estimatedSize: %1$d  currentQuota: %2$d  totalUsedQuota: %3$d", estimatedSize, currentQuota, totalUsedQuota));
        quotaUpdater.updateQuota(MAX_QUOTA);
    }

    /**
     * Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
     *
     * @param origin
     * @param callback
     */
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
    }

    /**
     * Tell the client to display a prompt dialog to the user.
     * If the client returns true, WebView will assume that the client will
     * handle the prompt dialog and call the appropriate JsPromptResult method.
     *
     * The prompt bridge provided for the InAppBrowser is capable of executing any
     * oustanding callback belonging to the InAppBrowser plugin. Care has been
     * taken that other callbacks cannot be triggered, and that no other code
     * execution is possible.
     *
     * To trigger the bridge, the prompt default value should be of the form:
     *
     * gap-iab://<callbackId>
     *
     * where <callbackId> is the string id of the callback to trigger (something
     * like "InAppBrowser0123456789")
     *
     * If present, the prompt message is expected to be a JSON-encoded value to
     * pass to the callback. A JSON_EXCEPTION is returned if the JSON is invalid.
     *
     * @param view
     * @param url
     * @param message
     * @param defaultValue
     * @param result
     */
    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        // See if the prompt string uses the 'gap-iab' protocol. If so, the remainder should be the id of a callback to execute.


        if (defaultValue == null || !defaultValue.startsWith("gap")) {
            return false;
        }

        if (defaultValue.startsWith(GAP_PROTOCOL)) {
            return handleJavascriptExecute(message, defaultValue, result);
        }

        if (defaultValue.startsWith(GAP_NATIVE_PROTOCOL)){
            return handleNativeJavascriptResponse(message, defaultValue, result);
        }

        // Anything else with a gap: prefix should get this message
        Log.w(LOG_TAG, "InAppBrowser does not support Cordova API calls: " + url + " " + defaultValue);
        result.cancel();
        return true;

    }

    private  boolean handleNativeJavascriptResponse(String message, String defaultValue, JsPromptResult result){
        if(message == null || message.length() == 0) {
            result.confirm("");
            return true;
        }

        String actionType = defaultValue.substring(GAP_NATIVE_PROTOCOL.length());

        if(!actionType.equals("poll")) {
            result.confirm("");
            Log.w(LOG_TAG, "InAppBrowser calls from native code with action type other than 'poll'" );
            result.confirm("");
            return true;
        }

        if(!nativeScriptResultHandler.handle(message)){
            Log.w(LOG_TAG, "The action in the return of the passed poll function could not be parsed or did not have a known action");
        }

        result.confirm("");
        return true;
    }

    private boolean handleJavascriptExecute(String message, String defaultValue, JsPromptResult result) {
        PluginResult scriptResult;
        String scriptCallbackId = defaultValue.substring(GAP_PROTOCOL.length());
        if (!scriptCallbackId.startsWith("InAppBrowser")) {
            return false;
        }
        if(message == null || message.length() == 0) {
            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
        } else {
            try {
                scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray(message));
            } catch(JSONException e) {
                scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
            }
        }
        this.webView.sendPluginResult(scriptResult, scriptCallbackId);
        result.confirm("");
        return true;

    }

}
