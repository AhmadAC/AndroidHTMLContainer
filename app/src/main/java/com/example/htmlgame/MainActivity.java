import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Uri currentFileUri = null;
    private String pendingSaveAsContent = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Required for HTML local storage

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // Bind the JavaScript interface
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        // Load the HTML file
        webView.loadUrl("file:///android_asset/index.html");
    }

    // --- FILE OPENER LAUNCHER ---
    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    currentFileUri = result.getData().getData();
                    if (currentFileUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(currentFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (Exception e) { e.printStackTrace(); }
                        readAndSendToWeb(currentFileUri);
                    }
                }
            }
    );

    // --- SAVE AS LAUNCHER ---
    private final ActivityResultLauncher<Intent> saveAsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    currentFileUri = result.getData().getData();
                    if (currentFileUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(currentFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (Exception e) { e.printStackTrace(); }
                        
                        // Write the data to the newly created file location
                        writeToFile(currentFileUri, pendingSaveAsContent);
                        readAndSendToWeb(currentFileUri); // Refresh the app UI with the new filename
                    }
                }
            }
    );

    private void readAndSendToWeb(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) { sb.append(line).append("\n"); }
            reader.close();
            
            String fileName = getFileName(uri);
            String base64Content = Base64.encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            
            webView.post(() -> webView.evaluateJavascript("javascript:loadFromAndroid('" + base64Content + "', '" + fileName + "')", null));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void writeToFile(Uri uri, String content) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
                os.close();
                webView.post(() -> webView.evaluateJavascript("javascript:onSaveSuccess()", null));
            }
        } catch (Exception e) {
            e.printStackTrace();
            webView.post(() -> webView.evaluateJavascript("javascript:onSaveError()", null));
        }
    }

    private String getFileName(Uri uri) {
        String result = "Loaded File.txt";
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) { result = cursor.getString(index); }
                }
            }
        }
        return result;
    }

    // --- THE BRIDGE BETWEEN JAVA AND HTML ---
    private class WebAppInterface {

        @JavascriptInterface
        public void openFilePicker() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            openFileLauncher.launch(intent);
        }

        @JavascriptInterface
        public void saveFile(String base64Content) {
            try {
                String content = new String(Base64.decode(base64Content, Base64.DEFAULT), StandardCharsets.UTF_8);
                if (currentFileUri != null) {
                    writeToFile(currentFileUri, content);
                } else {
                    // If the user tries to auto-save but hasn't picked a file yet, force "Save As"
                    saveAsFile(base64Content, "New_Tracker_List.txt");
                }
            } catch (Exception e) {
                webView.post(() -> webView.evaluateJavascript("javascript:onSaveError()", null));
            }
        }

        @JavascriptInterface
        public void saveAsFile(String base64Content, String suggestedName) {
            try {
                pendingSaveAsContent = new String(Base64.decode(base64Content, Base64.DEFAULT), StandardCharsets.UTF_8);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
                saveAsLauncher.launch(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
