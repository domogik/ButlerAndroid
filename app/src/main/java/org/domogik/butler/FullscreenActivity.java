package org.domogik.butler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import static android.app.PendingIntent.getActivity;


public class FullscreenActivity extends AppCompatActivity {
    private String LOG_TAG = "BUTLER > FullscreenAct";

    private View mContentView;
    // Buttons
    ImageButton speakButton;
    ImageButton muteButton;
    // Google voice for STT
    private ButlerGoogleVoice Gv;

    // Receivers
    StatusReceiverForGUI statusReceiverForGUI;
    UserRequestReceiverForGUI userRequestReceiverForGUI;
    ResponseReceiverForGUI responseReceiverForGUI;
    MuteStatusReceiverForGUI muteStatusReceiverForGUI;

    // Just mandatory to allow requesting RECORD_AUDIO permission...
    int MY_PERMISSIONS_REQUEST_RECORD_AUDIO;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We first start the Butler Service
        Intent butlerService = new Intent(FullscreenActivity.this, ButlerService.class);
        startService(butlerService);

        setContentView(R.layout.activity_fullscreen);

        // First, check if the user have the permission to use the microphone
        // This is only used for Android >= 6
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);

        }

        // Switch Fullscreen mode

        mContentView = findViewById(R.id.fullscreen_content);
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Buttons
        speakButton = (ImageButton)findViewById(R.id.speakbutton);
        muteButton = (ImageButton)findViewById(R.id.muteButton);

        // Init the receivers
        statusReceiverForGUI = new StatusReceiverForGUI(this);
        registerReceiver(statusReceiverForGUI, new IntentFilter("org.domogik.butler.Status"));
        userRequestReceiverForGUI = new UserRequestReceiverForGUI(this);
        registerReceiver(userRequestReceiverForGUI, new IntentFilter("org.domogik.butler.UserRequest"));
        responseReceiverForGUI = new ResponseReceiverForGUI(this);
        registerReceiver(responseReceiverForGUI, new IntentFilter("org.domogik.butler.Response"));
        muteStatusReceiverForGUI = new MuteStatusReceiverForGUI(this);
        registerReceiver(muteStatusReceiverForGUI, new IntentFilter("org.domogik.butler.MuteStatus"));

    }

    // Speak Button pressed (called from activity)
    public void onSpeakButton(View view) {
        Log.d("BUTLER", "Function onSpeakButton");
        Intent i = new Intent("org.domogik.butler.StartListeningUserRequest");
        sendBroadcast(i);
    }

    // Mute/unmute Button pressed (called from activity)
    public void onMuteButton(View view) {
        Log.d("BUTLER", "Function onMuteButton");
        Intent i = new Intent("org.domogik.butler.MuteAction");
        sendBroadcast(i);
        // TODO : handle a MuteStatus sent from the service and change the button background with the appropriate value
    }



    public void updateTheRequest(final String t) {
        FullscreenActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                TextView request = (TextView) findViewById(R.id.request);
                request.setText(capitalize(t));
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

    public static String capitalize(String s) {
        // Set the first letter to be a UPPER one
        if(s == null) return null;
        if(s.length() == 1) {
            return s.toUpperCase();
        }
        if(s.length() > 1){
            return s.substring(0,1).toUpperCase() + s.substring(1);
        } return "";
    }



    /*** Receivers *************************************************************************/

    class StatusReceiverForGUI extends BroadcastReceiver {
        /* Used to catch a request to speak to the Butler
           Can be called from an activity or a keyspotting feature in background
         */
        private Context context;
        public StatusReceiverForGUI(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "GUI > StatusReceiver";
        private ButlerGoogleVoice gv;

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            String status = arg.getStringExtra("status");

            Log.i(LOG_TAG, "StatusReceiver : status='" + status + "'");

            if (status.equals("LISTENING")) {
                // Listening action in progress with Google Voice or whatever...
                // We also get a voice level information
                int level = arg.getIntExtra("voicelevel", 0);  // 0 = default value
                int buttonImg = getResources().getIdentifier("btn_icon_mic_" + level, "drawable", getPackageName());
                speakButton.setBackgroundResource(buttonImg);
            }
            else if (status.equals("LISTENING_DONE")) {
                // Listening action done, we put back the original button icon
                // If any process should start after listening (requesting the butler for example), a new icon will be applied immediatly after)
                speakButton.setBackgroundResource(R.drawable.btn_icon);
            }
            else if (status.equals("LISTENING_ERROR")) {
                speakButton.setBackgroundResource(R.drawable.btn_icon);
            }
            else if (status.equals("REQUESTING_THE_BUTLER")) {
                speakButton.setBackgroundResource(R.drawable.btn_icon_processing);
            }
            else if (status.equals("REQUESTING_THE_BUTLER_DONE")) {
                // Calling the Butler over REST action done, we put back the original button icon
                // If any process should start after requesting the butler (text to speech for example), a new icon will be applied immediatly after)
                speakButton.setBackgroundResource(R.drawable.btn_icon);
            }
            else if (status.equals("SPEAKING")) {
                speakButton.setBackgroundResource(R.drawable.btn_icon_speaking);
            }
            else if (status.equals("SPEAKING_DONE")) {
                // Speaking action done, we put back the original button icon
                // If any process should start after speaking (continuous speach or whatever), a new icon will be applied immediatly after
                speakButton.setBackgroundResource(R.drawable.btn_icon);
            }

        }
    }

    class UserRequestReceiverForGUI extends BroadcastReceiver {
        /* When a spoken user request is received and recognized
           This Receiver may be found also on some activities to be displayed
         */
        private Context context;
        public UserRequestReceiverForGUI(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "GUI > UserRequestRcv";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "UserRequestReceiverForGUI");
            String text = arg.getStringExtra("text");
            //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
            // TODO : add try..catch ?

            updateTheRequest(text);
            updateTheResponse("...");
        }
    }

    class ResponseReceiverForGUI extends BroadcastReceiver {
        /* When a butler response is received
         */
        private Context context;
        public ResponseReceiverForGUI(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "GUI > ResponseRcv";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "ResponseReceiverForGUI");
            String text = arg.getStringExtra("text");
            //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
            // TODO : add try..catch ?

            updateTheResponse(text);
        }
    }

    class MuteStatusReceiverForGUI extends BroadcastReceiver {
        /* When a spoken user request is received and recognized
           This Receiver may be found also on some activities to be displayed
         */
        private Context context;
        public MuteStatusReceiverForGUI(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "GUI > MuteStatusRcv";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "MuteStatusReceiverForGUI");
            Boolean isMute = arg.getBooleanExtra("mute", false);

            if (isMute) {
                muteButton.setBackgroundResource(R.drawable.mute);
            }
            else {
                muteButton.setBackgroundResource(R.drawable.unmute);
            }
        }
    }
}



