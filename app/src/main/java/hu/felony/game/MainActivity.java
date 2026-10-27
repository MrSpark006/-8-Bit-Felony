package hu.felony.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {
    GameView v;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        v = new GameView(this);
        setContentView(v);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onBackPressed() {
        if (v.state != 0) v.state = 0; else super.onBackPressed();
    }
}
