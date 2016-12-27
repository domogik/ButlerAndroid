package org.domogik.butler;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by fritz on 27/12/16.
 */

public class GoogleVoice implements
        RecognitionListener {


    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "BUTLER > GoogleVoice";
    private Context context;
    //private domogik.domodroid.DaemonService Parent;

    //@Override
    //protected void onCreate(Bundle savedInstanceState) {
    protected void startVoiceRecognition(Context context) { //, domogik.domodroid.DaemonService Parent) {
        this.context = context;
        // TODO : REPLACE/DEL    this.Parent = Parent;
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
    }

    @Override
    public void onError(int errorCode) {
        //TODO : DELLog.e(LOG_TAG, "ZUT2");
        //TODO : DEL Toast.makeText(this.context, "ZUT", Toast.LENGTH_SHORT).show();
        String errorMessage = getErrorText(errorCode);

        if (errorCode != SpeechRecognizer.ERROR_CLIENT) {
            Log.e(LOG_TAG, "FAILED " + errorMessage);
            Log.e(LOG_TAG, "Request stop listening (onError function). ErrorCode=" + errorCode + ". ErrorMessage=" + errorMessage);
            speech.stopListening();
            // TODO : REPLACE Parent.gvOnStopListening(null);
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
            text += result + "\n";
            break;
        }
        Log.i(LOG_TAG, "Result : " + text);
        speech.stopListening();
        // TODO : REPLACE Parent.gvOnStopListening(matches);
        // TODO : renvoyer le texte !!!!
        // TODO : renvoyer le texte !!!!
        // TODO : renvoyer le texte !!!!
        // TODO : renvoyer le texte !!!!
        // TODO : renvoyer le texte !!!!
        // TODO : renvoyer le texte !!!!
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        //Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
        //progressBar.setProgress((int) rmsdB);
        int percent = (int)(10*Math.pow(10, ((double)rmsdB/(double)10)));
        int level = percent/5;   // to get 20 levels
        level = level * 5;  // to get 0, 5, 10.... 100
        // TODO : REPLACE Parent.gvOnVoiceLevel(level);

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
