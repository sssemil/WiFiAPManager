package sssemil.com.wifiapmanager;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class LicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        WebView localWebView = (WebView) findViewById(R.id.webView);
        localWebView.loadUrl("file:///android_res/raw/license.html");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        Tracker tracker = AnalyticsApplication.getDefaultTracker(this);
        Log.i(this.getLocalClassName(), "Setting screen name: " + this.getLocalClassName());
        tracker.setScreenName(this.getLocalClassName());
        tracker.send(new HitBuilders.ScreenViewBuilder().build());
    }
}
