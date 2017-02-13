package org.domogik.butler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by fritz on 27/12/16.
 */

public class ButlerGoogleVoice extends Activity implements
        RecognitionListener {

    // TODO : option to replace bip for input started, input ended by vibrations (for smartwatches)


    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "BUTLER > GoogleVoice";
    private Context context;
    //private domogik.domodroid.DaemonService Parent;

    //@Override
    //protected void onCreate(Bundle savedInstanceState) {
    protected void startVoiceRecognition(Context context) {
        this.context = context;
        // TODO : REPLACE/DEL    this.Parent = Parent;
        Log.i(LOG_TAG, "Init listening....");
        speech = SpeechRecognizer.createSpeechRecognizer(context);
        speech.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        // Check if a recognition service is available
        if (speech.isRecognitionAvailable(context) == true) {
            Log.i(LOG_TAG, "Start listening....");
            speech.startListening(recognizerIntent);
            Intent i = new Intent("org.domogik.butler.Status");
            i.putExtra("status", "LISTENING");
            i.putExtra("voicelevel", 0);    // the voice level will be updated in the onRmsChanged() function
            context.sendBroadcast(i);

        }
        else {
            // TODO : why the fuck this error is not catched ?
            // E/SpeechRecognizer: no selected voice recognition service

            Log.e(LOG_TAG, "Speech recognition not available.");
            Toast.makeText(this.context, "Speech recognition not available." , Toast.LENGTH_SHORT).show();

        }

    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
        //Toast.makeText(this.context, "GV > onBufferReceived", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
        Intent i = new Intent("org.domogik.butler.Status");
        i.putExtra("status", "LISTENING_WAITING_FOR_SERVER_RESPONSE");
        context.sendBroadcast(i);
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);

        if (errorCode != SpeechRecognizer.ERROR_CLIENT) {
            Log.e(LOG_TAG, "FAILED " + errorMessage);
            Log.e(LOG_TAG, "Request stop listening (onError function). ErrorCode=" + errorCode + ". ErrorMessage=" + errorMessage);
            speech.stopListening();
            stopRecognition();


            Intent i = new Intent("org.domogik.butler.Status");
            i.putExtra("status", "LISTENING_ERROR");
            context.sendBroadcast(i);

        }
        else {
            // We don't raise any errors here because, it is mainly a not understood query from the user
            // Toast.makeText(this.context, "Input not understood", Toast.LENGTH_SHORT).show();  // TODO : i18n
            // we skip this part as it is called each time a valid onResult is raised.... WTF !!!!  // TODO : understood why
        }
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";
        for (String result : matches) {
            // we get only the first result...
            text += result; // + "\n";
            break;
        }
        Log.i(LOG_TAG, "Result : " + text);
        //Toast.makeText(this.context, "Result : " + text , Toast.LENGTH_SHORT).show();  // TODO : DEL
        speech.stopListening();
        stopRecognition();

        // The requests are catched by the service for processing and also the fullscreen activity for display
        Intent i = new Intent("org.domogik.butler.Status");
        i.putExtra("status", "LISTENING_DONE");
        context.sendBroadcast(i);

        Intent i2 = new Intent("org.domogik.butler.UserRequest");
        i2.putExtra("text", text);
        context.sendBroadcast(i2);

    }

    @Override
    public void onRmsChanged(float rmsdB) {
        /* NOTICE : this function is not called on some devices... WTF ?
          For exemple, on KW88 Android 5.1 smartwatch, this function is nevel called even if voice recognition is ok...
         */
        //Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);

        int percent = (int)(10*Math.pow(10, ((double)rmsdB/(double)10)));
        int level = percent/5;   // to get 20 levels
        level = level * 5;  // to get 0, 5, 10.... 100

        // The request is catched by the GUI for changing the buttin icon depending on the voice level
        Intent i = new Intent("org.domogik.butler.Status");
        i.putExtra("status", "LISTENING");
        i.putExtra("voicelevel", level);
        context.sendBroadcast(i);
    }

    public void stopRecognition(){
        if (speech != null) {
            //speech.stopListening();
            speech.destroy();
            speech = null;
        }

    }

    public  String getErrorText(int errorCode) {
        String message = "";
        // TODO : DEL Toast.makeText(this.context, "STT ERROR = '" + errorCode + "'", Toast.LENGTH_SHORT).show();

        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        Log.e(LOG_TAG,  "STT ERROR CODE = '" + errorCode + "'");
        Log.e(LOG_TAG,  "STT ERROR MSG = '" + message + "'");
        return message;
    }

}
