package org.domogik.butlerwear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends WearableActivity {

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private BoxInsetLayout mContainerView;
    private TextView mTextView;
    private TextView mClockView;
    private static String LOG_TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mTextView = (TextView) findViewById(R.id.text);
        mClockView = (TextView) findViewById(R.id.clock);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        Log.d(LOG_TAG, "updateDisplay");
        if (isAmbient()) {
            Log.d(LOG_TAG, "isAmbient");
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mTextView.setTextColor(getResources().getColor(android.R.color.white));
            mClockView.setVisibility(View.VISIBLE);

            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        } else {
            Log.d(LOG_TAG, "No more isAmbient");
            mContainerView.setBackground(null);
            mTextView.setTextColor(getResources().getColor(android.R.color.black));
            mClockView.setVisibility(View.GONE);
        }
    }

    public void updateTheRequest(final String t) {
        Log.d(LOG_TAG, "updateTheRequest");
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                TextView request = (TextView) findViewById(R.id.text);
                request.setText(capitalize(t));
                Log.d(LOG_TAG, "updateTheRequest runOnUiThread");
            }
        });
    }

    public static String capitalize(String s) {
        // Set the first letter to be a UPPER one
        Log.d(LOG_TAG, "capitalize");
        if (s == null){
            Log.d(LOG_TAG, "s == null");
            return null;
        }
        if (s.length() == 1) {
            Log.d(LOG_TAG, "s.length() == 1");
            return s.toUpperCase();
        }
        if (s.length() > 1) {
            Log.d(LOG_TAG, "s.length() > 1");
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        return "";
    }

    /****
     * Receivers
     */


    class UserRequestReceiverForGUI extends BroadcastReceiver {
        /* When a spoken user request is received and recognized
           This Receiver may be found also on some activities to be displayed
         */
        private Context context;

        public UserRequestReceiverForGUI(Context context) {
            Log.i(LOG_TAG, "UserRequestReceiverForGUI");
            this.context = context;
        }

        private String LOG_TAG = "GUI > UserRequestRcv";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "UserRequestReceiverForGUI onReceive");
            String text = arg.getStringExtra("text");
            //Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO DEL
            // TODO : add try..catch ?

            updateTheRequest(text);
            // TODO updateTheResponse("...");
        }
    }
}
