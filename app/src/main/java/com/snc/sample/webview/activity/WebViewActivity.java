package com.snc.sample.webview.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.PermissionRequest; // ADDED
import android.webkit.WebView;

import com.snc.sample.webview.BuildConfig;
import com.snc.sample.webview.R;
import com.snc.sample.webview.bridge.AndroidBridge;
import com.snc.sample.webview.bridge.plugin.PluginCamera;
import com.snc.sample.webview.webview.WebViewHelper;
import com.snc.zero.activity.BaseActivity;
import com.snc.zero.dialog.DialogBuilder;
import com.snc.zero.keyevent.BackKeyShutdown;
import com.snc.zero.requetcode.RequestCode;
import com.snc.zero.util.AssetUtil;
import com.snc.zero.util.EnvUtil;
import com.snc.zero.util.PackageUtil;
import com.snc.zero.util.StringUtil;
import com.snc.zero.webview.CSDownloadListener;
import com.snc.zero.webview.CSFileChooserListener;
import com.snc.zero.webview.CSWebChromeClient;
import com.snc.zero.webview.CSWebViewClient;

import java.io.File;

import timber.log.Timber;

/**
 * WebView Activity
 *
 * @author mcharima5@gmail.com
 * @since 2018
 */
public class WebViewActivity extends BaseActivity {
    private WebView webview;
    private CSWebChromeClient webChromeClient;
    private CSFileChooserListener webviewFileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (BuildConfig.DEBUG) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        if (!new File(EnvUtil.getInternalFilesDir(getContext(), "public"), "202205091737430060.jpg").exists()) {
            AssetUtil.copyAssetToFile(getContext(),
                    "www/common/img/202205091737430060.jpg",
                    EnvUtil.getInternalFilesDir(getContext(), "public"));
        }

        init();
    }

    @SuppressLint("AddJavascriptInterface")
    private void init() {
        ViewGroup contentView = findViewById(R.id.contentView);
        if (null == contentView) {
            DialogBuilder.with(getActivity())
                    .setMessage("The contentView does not exist.")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .show();
            return;
        }

        // add webview
        this.webview = WebViewHelper.addWebView(getContext(), contentView);

        this.webview.getSettings().setMediaPlaybackRequiresUserGesture(false); // ADDED

        // options
        //this.webview.getSettings().setSupportMultipleWindows(true);

        // set user-agent
        try {
            String ua = this.webview.getSettings().getUserAgentString();
            if (!ua.endsWith(" ")) {
                ua += " ";
            }
            ua += PackageUtil.getApplicationName(this);
            ua += "/" + PackageUtil.getPackageVersionName(this);
            ua += "." + PackageUtil.getPackageVersionCode(this);
            this.webview.getSettings().setUserAgentString(ua);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e);
        }

        // set webViewClient
        CSWebViewClient webviewClient = new CSWebViewClient(getContext());
        this.webview.setWebViewClient(webviewClient);

        // set webChromeClient
        this.webChromeClient = new CSWebChromeClient(getContext()) {
            @Override // ADDED
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        }; // ADDED END

        this.webview.setWebChromeClient(this.webChromeClient);

        // set fileChooser
        this.webviewFileChooser = new CSFileChooserListener(getContext());
        this.webChromeClient.setFileChooserListener(this.webviewFileChooser);

        // add interface
        this.webview.addJavascriptInterface(new AndroidBridge(webview), "AndroidBridge");

        // add download listener
        this.webview.setDownloadListener(new CSDownloadListener(getActivity()));

        // load url
        WebViewHelper.loadUrl(this.webview, "https://noixsvoice.netlify.app/");
    }

    @Override
    protected void onDestroy() {
        WebViewHelper.removeWebView(this.webview);
        this.webview = null;
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            Timber.i("[ACTIVITY] onKeyDown(): WebView isVideoPlayingInFullscreen = %s", this.webChromeClient.isVideoPlayingInFullscreen());
            if (this.webChromeClient.isVideoPlayingInFullscreen()) {
                return false;
            }

            // multiple windows go back
            if (null != this.webChromeClient.getNewWebView()) {
                Timber.i("[ACTIVITY] onKeyDown(): NewWebView canGoBack = %s", this.webChromeClient.getNewWebView().canGoBack());
                if (this.webChromeClient.getNewWebView().canGoBack()) {
                    this.webChromeClient.getNewWebView().goBack();
                    return true;
                } else {
                    this.webChromeClient.closeNewWebView();
                }
                return true;
            }

            Timber.i("[ACTIVITY] onKeyDown(): WebView canGoBack = %s", this.webview.canGoBack());
            // go back
            if (this.webview.canGoBack()) {
                this.webview.goBack();
                return true;
            }

            if (BackKeyShutdown.isFirstBackKeyPress(keyCode, event)) {
                DialogBuilder.with(getContext())
                        .setMessage(getString(R.string.one_more_press_back_button))
                        .toast();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (null == data) {
            Timber.i("[ACTIVITY] onActivityResult(): requestCode[" + requestCode + "],  resultCode[" + resultCode + "],  data[null]");
        } else {
            Timber.i("[ACTIVITY] onActivityResult(): requestCode[" + requestCode + "],  resultCode[" + resultCode + "],  data[" +
                    "\n  action = " + data.getAction() +
                    "\n  scheme = " + data.getScheme() +
                    "\n  data = " + data.getData() +
                    "\n  type = " + data.getType() +
                    "\n  extras = " + StringUtil.toString(data.getExtras()) +
                    "\n]");
        }

        //++ [[START] File Chooser]
        if (RequestCode.REQUEST_FILE_CHOOSER_NORMAL == requestCode) {
            Timber.i("[ACTIVITY] onActivityResult(): REQUEST_FILE_CHOOSER_NORMAL");
            this.webviewFileChooser.onActivityResultFileChooserNormal(requestCode, resultCode, data);
        }
        else if (RequestCode.REQUEST_FILE_CHOOSER_LOLLIPOP == requestCode) {
            Timber.i("[ACTIVITY] onActivityResult(): REQUEST_FILE_CHOOSER_LOLLIPOP");
            this.webviewFileChooser.onActivityResultFileChooserLollipop(requestCode, resultCode, data);
        }
        //-- [[E N D] File Chooser]

        //++ [[START] Take a picture]
        if (RequestCode.REQUEST_TAKE_A_PICTURE == requestCode) {
            Timber.i("[ACTIVITY] onActivityResult(): REQUEST_TAKE_A_PICTURE");
            PluginCamera.onActivityResultTakePicture(this.webview, requestCode, resultCode, data);
        }
        //++ [[E N D] Take a picture]

        super.onActivityResult(requestCode, resultCode, data);
    }

}
