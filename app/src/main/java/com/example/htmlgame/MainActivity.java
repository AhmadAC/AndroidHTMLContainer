package com.example.htmlgame;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView myWebView;
    private Uri currentFileUri = null;
    private static final int PICK_FILE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        myWebView = new WebView(this);
        setContentView(myWebView);
        
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); 
        
        // This injects the "AndroidBridge" into your HTML JavaScript
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        
        myWebView.loadUrl("file:///android_asset/index.html");
    }

    // --- JAVASCRIPT BRIDGE ---
    public class WebAppInterface {
        
        @JavascriptInterface
        public void openFilePicker() {
            // Opens the native Android file picker
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            startActivityForResult(intent, PICK_FILE_REQUEST);
        }

        @JavascriptInterface
        public void saveFile(final String base64Content) {
            if (currentFileUri != null) {
                try {
                    // Decode the text sent from HTML and write it to the Android file
                    byte[] decodedBytes = Base64.decode(base64Content, Base64.DEFAULT);
                    OutputStream os = getContentResolver().openOutputStream(currentFileUri);
                    if (os != null) {
                        os.write(decodedBytes);
                        os.close();
                        runOnUiThread(() -> myWebView.evaluateJavascript("javascript:onSaveSuccess()", null));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> myWebView.evaluateJavascript("javascript:onSaveError()", null));
                }
            }
        }
    }

    // --- HANDLE FILE PICKER RESULT ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            currentFileUri = data.getData();
            if (currentFileUri != null) {
                
                // Request permission to write to this file in the background later
                getContentResolver().takePersistableUriPermission(currentFileUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                try {
                    // Read the text file
                    InputStream is = getContentResolver().openInputStream(currentFileUri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    is.close();
                    
                    String fileName = currentFileUri.getLastPathSegment();
                    if(fileName != null && fileName.contains(":")) fileName = fileName.substring(fileName.lastIndexOf(":") + 1);
                    if(fileName != null) fileName = fileName.replace("'", "\\'"); // Escape quotes

                    // Convert to Base64 to safely pass into Javascript without breaking syntax
                    String base64Content = Base64.encodeToString(sb.toString().getBytes("UTF-8"), Base64.NO_WRAP);
                    
                    // Call the Javascript function in your HTML
                    final String jsCall = "javascript:loadFromAndroid('" + base64Content + "', '" + fileName + "')";
                    runOnUiThread(() -> myWebView.evaluateJavascript(jsCall, null));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
