package com.example.covidappactivities.dashboard.fragments;

import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.covidappactivities.R;
import com.example.covidappactivities.cough.RecognizeCommands;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.tensorflow.lite.Interpreter;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CoughFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CoughFragment extends Fragment {

  // Constants that control the behavior of the recognition code and model
  // settings. See the audio recognition tutorial for a detailed explanation of
  // all these, but you should customize them to match your training settings if
  // you are running your own model.
  private static final int SAMPLE_RATE = 16000;
  private static final int SAMPLE_DURATION_MS = 1000;
  private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
  private static final long AVERAGE_WINDOW_DURATION_MS = 2000;
  private static final float DETECTION_THRESHOLD = 0.50f;
  private static final int SUPPRESSION_MS = 1500;
  private static final int MINIMUM_COUNT = 3;
  private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
  private static final String LABEL_FILENAME = "file:///android_asset/labels.txt";
  private static final String MODEL_FILENAME = "file:///android_asset/cough_detection_model.tflite";

  private static final int REQUEST_RECORD_AUDIO = 13;
  private static final String LOG_TAG = CoughFragment.class.getSimpleName();

  // Working variables.
  short[] recordingBuffer = new short[RECORDING_LENGTH];
  int recordingOffset = 0;
  boolean shouldContinue = true;
  private Thread recordingThread;
  boolean shouldContinueRecognition = true;
  private Thread recognitionThread;
  private final ReentrantLock recordingBufferLock = new ReentrantLock();

  private List<String> labels = new ArrayList<String>();
  private List<String> displayedLabels = new ArrayList<>();
  private RecognizeCommands recognizeCommands = null;

  // Tensorflow Interpreter
  private Interpreter tfLite;

  private Handler handler = new Handler();
  private boolean coughDetected = false;

  public CoughFragment() {
    // Required empty public constructor
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ///////////////////////////////TENSORFLOW///////////////////////
    // Load the labels for the model, but only display those that don't start
    // with an underscore.
    String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
    Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(this.getActivity().getAssets().open(actualLabelFilename)));
      String line;
      while ((line = br.readLine()) != null) {
        labels.add(line);
        if (line.charAt(0) != '_') {
          displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
        }
      }
      br.close();
    } catch (IOException e) {
      throw new RuntimeException("Problem reading label file!", e);
    }

    // Set up an object to smooth recognition results to increase accuracy.
    recognizeCommands =
            new RecognizeCommands(
                    labels,
                    AVERAGE_WINDOW_DURATION_MS,
                    DETECTION_THRESHOLD,
                    SUPPRESSION_MS,
                    MINIMUM_COUNT,
                    MINIMUM_TIME_BETWEEN_SAMPLES_MS);

    String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
    try {

      AssetFileDescriptor fileDescriptor = this.getActivity().getAssets().openFd(actualModelFilename);
      FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
      FileChannel fileChannel = inputStream.getChannel();
      long startOffset = fileDescriptor.getStartOffset();
      long declaredLength = fileDescriptor.getDeclaredLength();

      tfLite = new Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength));
      fileDescriptor.close();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
//    tfLite.resizeInput(1, new int[] {1});

    // Start the recording and recognition threads.
    startRecording();
    startRecognition();


  }


  public synchronized void startRecording() {
    if (recordingThread != null) {
      return;
    }
    shouldContinue = true;
    recordingThread =
            new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        record();
                      }
                    });
    recordingThread.start();
  }

  public synchronized void stopRecording() {
    if (recordingThread == null) {
      return;
    }
    shouldContinue = false;
    recordingThread = null;
  }

  private void record() {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

    // Estimate the buffer size we'll need for this device.
    int bufferSize =
            AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      bufferSize = SAMPLE_RATE * 2;
    }
    short[] audioBuffer = new short[bufferSize / 2];

    AudioRecord record =
            new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(LOG_TAG, "Audio Record can't initialize!");
      return;
    }

    record.startRecording();

    Log.v(LOG_TAG, "Start recording");

    // Loop, gathering audio data and copying it to a round-robin buffer.
    while (shouldContinue) {
      int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
      int maxLength = recordingBuffer.length;
      int newRecordingOffset = recordingOffset + numberRead;
      int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
      int firstCopyLength = numberRead - secondCopyLength;
      // We store off all the data for the recognition thread to access. The ML
      // thread will copy out of this buffer into its own, while holding the
      // lock, so this should be thread safe.
      recordingBufferLock.lock();
      try {
        System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
        System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
        recordingOffset = newRecordingOffset % maxLength;
      } finally {
        recordingBufferLock.unlock();
      }
    }

    record.stop();
    record.release();
  }

  public synchronized void startRecognition() {
    if (recognitionThread != null) {
      return;
    }
    shouldContinueRecognition = true;
    recognitionThread =
            new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        recognize();
                      }
                    });
    recognitionThread.start();
  }

  public synchronized void stopRecognition() {
    if (recognitionThread == null) {
      return;
    }
    shouldContinueRecognition = false;
    recognitionThread = null;
  }

  private void recognize() {

    Log.v(LOG_TAG, "Start recognition");

    short[] inputBuffer = new short[RECORDING_LENGTH];
    float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
    float[][] outputScores = new float[1][labels.size()];
    int[] sampleRateList = new int[] {SAMPLE_RATE};

    // Loop, grabbing recorded data and running the recognition model on it.
    while (shouldContinueRecognition) {
      long startTime = new Date().getTime();
      // The recording thread places data in this round-robin buffer, so lock to
      // make sure there's no writing happening and then copy it to our own
      // local version.
      recordingBufferLock.lock();
      try {
        int maxLength = recordingBuffer.length;
        int firstCopyLength = maxLength - recordingOffset;
        int secondCopyLength = recordingOffset;
        System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
        System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
      } finally {
        recordingBufferLock.unlock();
      }

      // We need to feed in float values between -1.0f and 1.0f, so divide the
      // signed 16-bit inputs.
      for (int i = 0; i < RECORDING_LENGTH; ++i) {
        floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
      }

      Object[] inputArray = {floatInputBuffer};//, sampleRateList};
      Map<Integer, Object> outputMap = new HashMap<>();
      outputMap.put(0, outputScores);

      // Run the model.
      tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

      // Use the smoother to figure out if we've had a real recognition event.
      long currentTime = System.currentTimeMillis();
      final RecognizeCommands.RecognitionResult result =
              recognizeCommands.processLatestResults(outputScores[0], currentTime);
      this.getActivity().runOnUiThread(
              new Runnable() {
                @Override
                public void run() {


                  // If we do have a new command, highlight the right list entry.
                  if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                    int labelIndex = -1;
                    for (int i = 0; i < labels.size(); ++i) {
                      if (labels.get(i).equals(result.foundCommand)) {
                        labelIndex = i;
                      }
                    }

                    switch (labelIndex - 2) {
                      case 0:
                        coughDetected = true;
                        Log.v(LOG_TAG, "COUGH DETECTED");
                        break;
                      default:
                        Log.v(LOG_TAG, "NOTHING DETECTED");
                        coughDetected = false;
                        break;

                    }

                    if (coughDetected) {
                      final String message = getMessage(result.score * 100);
                      final String score = Math.round(result.score * 100) + "%";
                      TextView view = (TextView) getActivity().findViewById(R.id.speechBubble);
                      view.setText(message);
                      ImageView coughIcon = (ImageView) CoughFragment.this.getActivity().findViewById(R.id.coughBell);
                      coughIcon.setImageResource(R.drawable.cough);
                      handler.postDelayed(
                              new Runnable() {
                                @Override
                                public void run() {
                                  coughIcon.setImageResource(R.drawable.happy);
                                }
                              },
                              1500);
                    }
                  }
                }
              });
      try {
        // We don't need to run too frequently, so snooze for a bit.
        Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
      } catch (InterruptedException e) {
        // Ignore
      }
    }

    Log.v(LOG_TAG, "End recognition");
  }

  /**
   * Returns a message to display on the cough recognition fragment
   * @param score 0-100 probability of recognition
   * @return message to display
   */
  private String getMessage(float score){
    double modifiedScore = Math.floor(score);
    String[] lowConfidence = {"Was that a cough, I just heard?", "That kind of sounded like a cough", "I'm like " + String.valueOf(modifiedScore) + "% sure that was a cough"};
    String[] highConfidence = {"That didn't sound great!", "I'm like " + String.valueOf(modifiedScore) + "% sure that was a cough", "Hope you're wearing a mask!", "Stay safe, my friend!"};
    if (modifiedScore < 60){
      int max = lowConfidence.length;
      int a = (int) (Math.random() * max);
      return lowConfidence[a];
    }else{
      int max = highConfidence.length;
      int a = (int) (Math.random() * max);
      return highConfidence[a];
    }
  }

  /** Memory-map the model file in Assets. */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
          throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   *
   * @return A new instance of fragment SmsFragment.
   */
  public static CoughFragment newInstance() {
    CoughFragment fragment = new CoughFragment();
    return fragment;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_cough, container, false);
  }


  @Override
  public void onPause() {
    Log.v(LOG_TAG, "Pausing Fragment");
    super.onPause();
    stopRecording();
    stopRecognition();
  }

}
