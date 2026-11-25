package com.panoi.pos;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  static final String URL = "https://burinbrkm-deep.github.io/pos-panoi/";
  WebView web; SunmiPrinterService printer; TextToSpeech tts; boolean thai = false;
  final ExecutorService pq = Executors.newSingleThreadExecutor();

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false); s.setCacheMode(WebSettings.LOAD_NO_CACHE);
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new Bridge(), "PNApp");
    setContentView(web);
    bindPrinter();
    tts = new TextToSpeech(this, st -> {
      if (st == TextToSpeech.SUCCESS) {
        int r = tts.setLanguage(new Locale("th", "TH"));
        thai = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
      }
    });
    if (b != null) web.restoreState(b); else web.loadUrl(URL);
  }

  void bindPrinter() {
      try {
      InnerPrinterManager.getInstance().bindService(this, new InnerPrinterCallback() {
        @Override protected void onConnected(SunmiPrinterService service) { printer = service; }
        @Override protected void onDisconnected() { printer = null; }
      });
    } catch (Exception e) { }
  }

  class Bridge {
    @JavascriptInterface public String print(String b64) {
      if (printer == null) { bindPrinter(); return "ไม่พบเครื่องพิมพ์ในตัว ลองใหม่อีกครั้ง"; }
      final byte[] data;
      try { data = Base64.decode(b64, Base64.DEFAULT); } catch (Exception e) { return "ข้อมูลเสีย"; }
      pq.execute(() -> {
        try { SunmiPrinterService p = printer; if (p != null) p.sendRAWData(data, null); }
        catch (Exception e) { }
      });
      return "ok";
    }
    @JavascriptInterface public void speak(String t, double rate) {
      if (tts == null) return;
      tts.setSpeechRate((float) rate);
      tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pn");
    }
    @JavascriptInterface public boolean hasThai() { return thai; }
    @JavascriptInterface public boolean hasPrinter() { return printer != null; }
  }

  @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); }
  @Override protected void onDestroy() {
    try { InnerPrinterManager.getInstance().unBindService(this, null); } catch (Exception e) { }
    if (tts != null) tts.shutdown();
    super.onDestroy();
  }
}
