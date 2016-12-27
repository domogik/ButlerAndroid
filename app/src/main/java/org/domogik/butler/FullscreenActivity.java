package org.domogik.butler;

import android.annotation.SuppressLint;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;


public class FullscreenActivity extends AppCompatActivity {

    private View mContentView;
    // Speak Button
    ImageButton speakButton;
    // Google voice for STT
    private GoogleVoice Gv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        // Switch Fullscreen mode

        mContentView = findViewById(R.id.fullscreen_content);
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Speak Button
        speakButton = (ImageButton)findViewById(R.id.speakbutton);
    }

    // Speak Button pressed (called from activity)
    public void onSpeakButton(View view) {
        Log.d("BUTLER", "Function onSpeakButton");
        Toast.makeText(getBaseContext(), "Please speak", Toast.LENGTH_SHORT).show();       // TODO : DEL
        //TODO : do this only if the current status allows it !
        Gv = new GoogleVoice();
        // TODO : doBeepForInput();
        Gv.startVoiceRecognition(getApplicationContext());
    }

}

