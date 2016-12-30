package org.domogik.butler;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
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
import android.widget.TextView;
import android.widget.Toast;


public class FullscreenActivity extends AppCompatActivity {
    private String LOG_TAG = "BUTLER > FullscreenAct";

    private View mContentView;
    // Speak Button
    ImageButton speakButton;
    // Google voice for STT
    private ButlerGoogleVoice Gv;

    // Receivers
    UserRequestReceiverForGUI userRequestReceiverForGUI;
    ResponseReceiverForGUI responseReceiverForGUI;

    private static FullscreenActivity ins;  // needed by the receivers to call the update functions of the user interface

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ins = this;

        // We first start the Butler Service
        Intent butlerService = new Intent(FullscreenActivity.this, ButlerService.class);
        startService(butlerService);

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

        // Init the receivers
        userRequestReceiverForGUI = new UserRequestReceiverForGUI();
        registerReceiver(userRequestReceiverForGUI, new IntentFilter("org.domogik.butler.UserRequest"));
        responseReceiverForGUI = new ResponseReceiverForGUI();
        registerReceiver(responseReceiverForGUI, new IntentFilter("org.domogik.butler.Response"));

    }

    // Speak Button pressed (called from activity)
    public void onSpeakButton(View view) {
        Log.d("BUTLER", "Function onSpeakButton");
        Toast.makeText(getBaseContext(), "Please speak", Toast.LENGTH_SHORT).show();       // TODO : DEL
        //TODO : do this only if the current status allows it !
        //Gv = new ButlerGoogleVoice();
        //Gv.startVoiceRecognition(getApplicationContext());

        Intent i = new Intent("org.domogik.butler.StartListeningUserRequest");
        sendBroadcast(i);
        Log.i(LOG_TAG, "ET LA TRA LA LA");
    }


    // Update User interface from Intents
    public static FullscreenActivity  getInstace(){
        return ins;
    }

    public void updateTheRequest(final String t) {
        FullscreenActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                TextView request = (TextView) findViewById(R.id.request);
                request.setText(t);
            }
        });
    }

    public void updateTheResponse(final String t) {
        FullscreenActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                TextView request = (TextView) findViewById(R.id.response);
                request.setText(t);
            }
        });
    }

}



/*** Receivers *************************************************************************/


class UserRequestReceiverForGUI extends BroadcastReceiver {
    /* When a spoken user request is received and recognized
       This Receiver may be found also on some activities to be displayed
     */
    private String LOG_TAG = "GUI > UserRequestRcv";

    @Override
    public void onReceive(Context context, Intent arg) {
        // TODO Auto-generated method stub
        Log.i(LOG_TAG, "UserRequestReceiverForGUI");
        String text = arg.getStringExtra("text");
        //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
        // TODO : add try..catch ?
        FullscreenActivity.getInstace().updateTheRequest(text);
    }
}

class ResponseReceiverForGUI extends BroadcastReceiver {
    /* When a spoken user request is received and recognized
       This Receiver may be found also on some activities to be displayed
     */
    private String LOG_TAG = "GUI > ResponseRcv";

    @Override
    public void onReceive(Context context, Intent arg) {
        // TODO Auto-generated method stub
        Log.i(LOG_TAG, "ResponseReceiverForGUI");
        String text = arg.getStringExtra("text");
        //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
        // TODO : add try..catch ?
        FullscreenActivity.getInstace().updateTheResponse(text);
    }
}