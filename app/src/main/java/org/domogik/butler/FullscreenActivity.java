package org.domogik.butler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

    Intent butlerService;

    // Buttons
    ImageButton speakButton;
    ImageButton muteButton;
    boolean isSmallScreen = false; // the mute button is displayed only on small screens

    // Google voice for STT
    private ButlerGoogleVoice Gv;

    // screen size
    int screenSize;

    // Menu
    private Menu mOptionsMenu = null;

    // settings
    SharedPreferences settings;
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    // Screen wake up
    // TODO DEL
    //Boolean doWakeupScreen = true;   // true to allow screen wakeup on the first voice wake up

    // Receivers
    StatusReceiverForGUI statusReceiverForGUI;
    UserRequestReceiverForGUI userRequestReceiverForGUI;
    UserPartialRequestReceiverForGUI userPartialRequestReceiverForGUI;
    ResponseReceiverForGUI responseReceiverForGUI;
    MuteStatusReceiverForGUI muteStatusReceiverForGUI;

    // Just mandatory to allow requesting RECORD_AUDIO permission...
    int MY_PERMISSIONS_REQUEST_RECORD_AUDIO;
    int MY_PERMISSION_ACCESS_COURSE_LOCATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We first start the Butler Service
        butlerService = new Intent(FullscreenActivity.this, ButlerService.class);
        startService(butlerService);


        // First, check if the user have the permission to use the microphone
        // This is only used for Android >= 6
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);

        }

        // Then, check also if the user have the permission to use Location services
        // Needed for some Android releases
        // more informations : http://stackoverflow.com/questions/32491960/android-check-permission-for-locationmanager
        if ( ContextCompat.checkSelfPermission( this,
                Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {

            ActivityCompat.requestPermissions( this,
                    new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION  }, MY_PERMISSION_ACCESS_COURSE_LOCATION );
        }

        // Settings
        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        registerPreferenceListener();

        // Default layout
        setContentView(R.layout.activity_fullscreen);
        // Switch screen mode
        mContentView = findViewById(R.id.fullscreen_content);
        screenSize = getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;

        String toastMsg;
        switch(screenSize) {
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                toastMsg = "Large screen";
                break;
            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                toastMsg = "Normal screen";
                break;
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
                getSupportActionBar().hide();

                setContentView(R.layout.activity_fullscreen_small);
                isSmallScreen = true;
                toastMsg = "Small screen";
                // change the activity used !
                /*
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                        */

                break;
            default:
                toastMsg = "Screen size is neither large, normal or small";
        }
        //Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();

        // Buttons
        speakButton = (ImageButton)findViewById(R.id.speakbutton);
        if (isSmallScreen) {
            muteButton = (ImageButton) findViewById(R.id.muteButton);
        }

        // Init the receivers
        statusReceiverForGUI = new StatusReceiverForGUI(this);
        registerReceiver(statusReceiverForGUI, new IntentFilter("org.domogik.butler.Status"));
        userRequestReceiverForGUI = new UserRequestReceiverForGUI(this);
        registerReceiver(userRequestReceiverForGUI, new IntentFilter("org.domogik.butler.UserRequest"));
        userPartialRequestReceiverForGUI = new UserPartialRequestReceiverForGUI(this);
        registerReceiver(userPartialRequestReceiverForGUI, new IntentFilter("org.domogik.butler.UserPartialRequest"));
        responseReceiverForGUI = new ResponseReceiverForGUI(this);
        registerReceiver(responseReceiverForGUI, new IntentFilter("org.domogik.butler.Response"));
        muteStatusReceiverForGUI = new MuteStatusReceiverForGUI(this);
        registerReceiver(muteStatusReceiverForGUI, new IntentFilter("org.domogik.butler.MuteStatus"));

        // First, we check if the configuration is done. If not, we open the configuration screen
        String adminUrl = settings.getString("domogik_admin_url", "notconfigured");
        String userAuth = settings.getString("domogik_user", "notconfigured");
        String passwordAuth = settings.getString("domogik_password", "notconfigured");
        if ((adminUrl.equals("notconfigured"))
            || (userAuth.equals("notconfigured"))
            || (passwordAuth.equals("notconfigured"))) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (!isFinishing()){
                        new AlertDialog.Builder(FullscreenActivity.this)
                                .setTitle("Configuration")          // TODO : i18n
                                .setMessage("The configuration is not complete!")
                                .setCancelable(false)
                                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(FullscreenActivity.this, SettingsActivity.class);
                                        startActivity(intent);
                                    }
                                }).show();
                    }
                }
            });

        }

        // Init the text view with the keyphrase if set/activated
        TextView request = (TextView) findViewById(R.id.request);
        TextView displayedKeyphrase = (TextView) findViewById(R.id.keyphrase);
        String keyphrase = settings.getString("keyspotting_keyphrase", "notconfigured");
        Boolean doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
        if (doVoiceWakeup) {
            //Commented because when you goes from the activity with no previous dialog and voice wake up activated to
            //voice wake up deactivated with the top button, the text is still telling that we can vocie wake up
            // TODO : how to improve this ?
            //request.setText(getResources().getString(R.string.request_default_with_wakeup) + " \"" + capitalize(keyphrase) + "\"");
            request.setText(getResources().getString(R.string.request_default));
            if (!isSmallScreen) {
                displayedKeyphrase.setText(capitalize(keyphrase));
            }
            displayedKeyphrase.setText(capitalize(keyphrase));
            // icon of the keyspotting button in actionbar will be set in actionbar creator

        }
        else {
            request.setText(getResources().getString(R.string.request_default));
            // icon of the keyspotting button in actionbar will be set in actionbar creator
        }

        // Init the mute buttons for a small screen
        Boolean isMute = settings.getBoolean("mute", false);
        if (isMute) {
            // mute button or mute in action bar...
            if (isSmallScreen) {
                muteButton.setBackgroundResource(R.drawable.mute);
            }
        }
        else {
            // mute button or mute in action bar...
            if (isSmallScreen) {
                muteButton.setBackgroundResource(R.drawable.unmute);
            }
        }

        // The config "service_stop" must always be off (for a better user experience)
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("service_stop", false);
        editor.commit();

        // To allow unlock screen on voice wake up and screen wake up
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // If set in configuration, start listening when opening the activity
        Boolean startListening = settings.getBoolean("listen_on_display_activated", false);
        if (startListening) {
            Intent i = new Intent("org.domogik.butler.StartListeningUserRequest");
            sendBroadcast(i);
        }

    }


    private void registerPreferenceListener()
    {
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

                if (key.equals("keyspotting_activated")) {
                    Boolean doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
                    String keyphrase = settings.getString("keyspotting_keyphrase", "");
                    TextView displayedKeyphrase = (TextView) findViewById(R.id.keyphrase);
                    Log.i(LOG_TAG, "Preferences : keyspotting_activated changed ! New value = " + doVoiceWakeup);
                    if (doVoiceWakeup) {

                        if (!isSmallScreen) {
                            if (mOptionsMenu != null) {
                                mOptionsMenu.findItem(R.id.action_keyspotting).setIcon(R.drawable.keyspotting_on);
                            }

                        }
                        displayedKeyphrase.setText(capitalize(keyphrase));
                    }
                    else {
                        if (!isSmallScreen) {
                            if (mOptionsMenu != null) {
                                mOptionsMenu.findItem(R.id.action_keyspotting).setIcon(R.drawable.keyspotting_off);
                            }

                        }
                        displayedKeyphrase.setText("");
                    }

                }
                else if ((key.equals("service_stop"))) {
                    Boolean serviceStop = settings.getBoolean("service_stop", false);
                    if (serviceStop) {
                        Log.i(LOG_TAG, "Stop application");
                        stopService(butlerService);
                        finishAffinity();
                        //getActivity( ).finish();
                        //System.exit(0);
                    }
                }
                // TODO : something to do/reload if the language changes ? i18n
            }
        };

        settings.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // set up the action bar
        mOptionsMenu = menu;
        switch(screenSize) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                // no menu :)
                break;
            default:
                getMenuInflater().inflate(R.menu.main, menu);
                Boolean doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
                if (doVoiceWakeup) {
                    mOptionsMenu.findItem(R.id.action_keyspotting).setIcon(R.drawable.keyspotting_on);
                }
                else {
                    mOptionsMenu.findItem(R.id.action_keyspotting).setIcon(R.drawable.keyspotting_off);
                }

                // Init the mute buttons
                Boolean isMute = settings.getBoolean("mute", false);
                if (isMute) {
                    // mute button or mute in action bar...
                    if (mOptionsMenu != null) {
                        mOptionsMenu.findItem(R.id.action_mute).setIcon(R.drawable.mute);
                    }
                }
                else {
                    // mute button or mute in action bar...
                    if (mOptionsMenu != null) {
                        mOptionsMenu.findItem(R.id.action_mute).setIcon(R.drawable.unmute);
                    }
                }

        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_mute:
                Intent i = new Intent("org.domogik.butler.MuteAction");
                sendBroadcast(i);
                // TODO : handle a MuteStatus sent from the service and change the button background with the appropriate value
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(FullscreenActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_keyspotting:
                Boolean doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("keyspotting_activated", !doVoiceWakeup);
                editor.commit();
                // The icon update will be done in the configuration receiver, as the config can also be set from the settings
                return true;

        }

        return super.onOptionsItemSelected(item);
    }


    // Exit related functions
/* disabled for now
   Used by the notification

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case ButlerService.CLOSE_ACTION:
                Log.i(LOG_TAG, "REQUEST TO EXIT FROM NOTIFICATION!");
                NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                nMgr.cancel(ButlerService.NOTIFICATION);
                exit();
                break;
        }
    }

    private void exit() {
        stopService(new Intent(this, ButlerService.class));
        finish();
    }
    */

    public void onDestroy() {
        unregisterReceiver(statusReceiverForGUI);
        unregisterReceiver(userRequestReceiverForGUI);
        unregisterReceiver(userPartialRequestReceiverForGUI);
        unregisterReceiver(responseReceiverForGUI);
        unregisterReceiver(muteStatusReceiverForGUI);

        super.onDestroy();
    }


    // Menu Button pressed (called from activity)
    public void onMenuButton(View view) {
        Log.i("BUTLER", "Function onMenuButton");
        Intent intent = new Intent(FullscreenActivity.this, SettingsActivity.class);
        startActivity(intent);
    }

    // Speak Button pressed (called from activity)
    public void onSpeakButton(View view) {
        Log.i("BUTLER", "Function onSpeakButton");
        Intent i = new Intent("org.domogik.butler.StartListeningUserRequest");
        sendBroadcast(i);
    }

    // Mute/unmute Button pressed (called from activity)
    public void onMuteButton(View view) {
        Log.i("BUTLER", "Function onMuteButton");
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

            if (!status.equals("LISTENING")) {
                // We don't log for listening to avoid too much spam as each time the voice level change this function is raised
                Log.i(LOG_TAG, "StatusReceiver : status='" + status + "'");
            }
            if ((status.equals("LISTENING")) || (status.equals("WANT_LISTENING_AGAIN"))) {
                /* TODO DEL

                // Wake up the screen (usefull in case of voice wakeup)
                if (doWakeupScreen) {
                    Log.i(LOG_TAG, "Do wake up screen !");
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                         WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                         WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                                         WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    doWakeupScreen = false;
                }

                 */


                // Listening action in progress with Google Voice or whatever... or a listening action will be soon initiated (continuous speaking)
                // We also get a voice level information
                int level = arg.getIntExtra("voicelevel", 0);  // 0 = default value
                int buttonImg = getResources().getIdentifier("btn_icon_mic_" + level, "drawable", getPackageName());
                speakButton.setBackgroundResource(buttonImg);
            }
            else if (status.equals("LISTENING_DONE")) {
                // Listening action done, we put back the original button icon
                // If any process should start after listening (requesting the butler for example), a new icon will be applied immediatly after)
                speakButton.setBackgroundResource(R.drawable.btn_icon);

                // For next time, allow again screen wake up
                // TODO : DEL
                //doWakeupScreen = true;
            }
            else if (status.equals("LISTENING_WAITING_FOR_SERVER_RESPONSE")) {
                speakButton.setBackgroundResource(R.drawable.btn_icon_mic_waiting);
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


    class UserPartialRequestReceiverForGUI extends BroadcastReceiver {
        /* When a spoken user request is received and recognized
           This Receiver may be found also on some activities to be displayed
         */
        private Context context;
        public UserPartialRequestReceiverForGUI(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "GUI > UserPartialReqRcv";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "UserPartialRequestReceiverForGUI");
            String text = arg.getStringExtra("text");
            //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
            // TODO : add try..catch ?

            updateTheRequest(text);
            updateTheResponse("");
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
                // mute button or mute in action bar...
                if (isSmallScreen) {
                    muteButton.setBackgroundResource(R.drawable.mute);
                }
                else {
                    mOptionsMenu.findItem(R.id.action_mute).setIcon(R.drawable.mute);
                }
            }
            else {
                // mute button or mute in action bar...
                if (isSmallScreen) {
                    muteButton.setBackgroundResource(R.drawable.unmute);
                }
                else {
                    mOptionsMenu.findItem(R.id.action_mute).setIcon(R.drawable.unmute);
                }
            }
        }
    }
}



