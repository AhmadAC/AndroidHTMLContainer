package com.example.htmlgame;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WebView myWebView = new WebView(this);
        setContentView(myWebView);
        
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Allows HTML5 games to save data
        webSettings.setMediaPlaybackRequiresUserGesture(false); // Allows auto-playing sounds
        
        // This points to the HTML file in your assets folder
        myWebView.loadUrl("file:///android_asset/index.html");
    }
}
