package com.example.appthaylam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.Size;
import android.view.ScaleGestureDetector; // Added for camera zoom
import android.view.MotionEvent;          // Added for camera zoom
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;           // Added for camera zoom
import androidx.camera.core.CameraControl;    // Added for camera zoom
import androidx.camera.core.CameraInfo;       // Added for camera zoom
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;

public class MainActivity extends AppCompatActivity {

    // ================================================================
    // REGION: BLUETOOTH & GHI ÂM (Speech Recognition)
    // ================================================================
    private static final String TAG = "MainActivity";

    // Chỉ giữ lại nút ghi âm và nút chọn thiết bị
    private Button btnRecord, btnSelectDevice;
    private TextView tvResult;

    private SpeechRecognizer speechRecognizer;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 100;

    // ================================================================

    private BluetoothDevice bluetoothDeviceBLE;
    // ================================================================
    // REGION: CAMERA, ML KIT OCR & API tra cứu phạt nguội
    // ================================================================
    // Giữ lại PreviewView, EditText và TextView để hiển thị biển số và kết quả API
    private PreviewView previewView;
    private TextView txtKetQuaPhatNguoi;
    private EditText edtBienSoXe;

    // CameraX
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;  // Added for camera zoom

    // ScaleGestureDetector cho zoom bằng tay
    private ScaleGestureDetector scaleGestureDetector; // Added for camera zoom

    // API
    private static final String BASE_URL = "https://api.checkphatnguoi.vn/";

    // ================================================================

    private static final int REQUEST_BLE_PERMISSIONS = 2;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private final Handler handler = new Handler();
    private boolean scanning = false;
    private final ArrayList<BluetoothDevice> foundDevices = new ArrayList<>();

    // Characteristics cho giao tiếp dữ liệu
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    // ActivityResultLauncher để yêu cầu bật Bluetooth
    private ActivityResultLauncher<Intent> enableBtLauncher;
    //================================================================
    // Animation
    // ===============================================================
    private final Handler loadingHandler = new Handler(Looper.getMainLooper());
    private Runnable loadingRunnable;
    private int dotCount = 0;

    // --- Global variables ---
    private TextRecognizer textRecognizer;

    // Biến để kiểm soát việc đang xử lý biển số (dừng dò khi API đang xử lý)
    private boolean isProcessingPlate = false;

    private TextToSpeech tts;
    private boolean speakerEnabled = false;

    private boolean isListening = false;

    private ImageButton iconAutoCheckPhatNguoi;
    private final String textSpeakWhenReceivePhatNguoi = "Có lỗi phạt nguội, bạn có muốn gửi thông tin phạt nguội không";

    private int numberOfPenalty = 0;
    private String licensePlatePenalty = "";

    private boolean isWaitingRespose = false;

    private boolean askingToSendPenalty = false;
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ánh xạ view cho BLUETOOTH & GHI ÂM
        btnRecord = findViewById(R.id.btnRecord);
        btnSelectDevice = findViewById(R.id.btnSelectDevice);
        tvResult = findViewById(R.id.tvResult);

        // Ánh xạ view cho CAMERA & TRA CỨU PHẠT NGUỘI
        previewView = findViewById(R.id.previewView);
        edtBienSoXe = findViewById(R.id.edtBienSoXe);
        txtKetQuaPhatNguoi = findViewById(R.id.txtKetQuaPhatNguoi);
        iconAutoCheckPhatNguoi = findViewById(R.id.iconAutoCheckPhatNguoi);
        // Khởi tạo GHI Âm
        requestAudioPermission();

        // --- Initialize ScaleGestureDetector for pinch zoom --- // Added for camera zoom
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera != null) {
                    float currentZoomRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                    float delta = detector.getScaleFactor();
                    float newZoomRatio = currentZoomRatio * delta;
                    // Có thể thêm giới hạn zoom nếu cần, ví dụ: newZoomRatio từ 1.0 đến 5.0
                    camera.getCameraControl().setZoomRatio(newZoomRatio);
                }
                return true;
            }
        });

        // Gán touch listener cho previewView để nhận sự kiện zoom
        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
        // ----------------------------------------------------- //

        btnRecord.setOnClickListener(view -> {
            if (isListening) {
                // Nếu đang ghi âm, dừng ghi âm và xử lý âm thanh
                speechRecognizer.stopListening();
                isListening = false;
                //sendRecord();
                btnRecord.setText("Bấm để ghi âm");
                if(isWaitingRespose)
                {
                    isWaitingRespose = false;
                    speechRecognizer.stopListening();
                    isProcessingPlate = false;
                    askingToSendPenalty = false;
                }
                else
                {
                    sendRecord();
                }
            } else {
                startVoiceRecognition();
                btnRecord.setText("Đang ghi âm");
            }
        });

        initializeSpeechRecognizer();

        iconAutoCheckPhatNguoi.setImageResource(R.drawable.tra_phat_nguoi_off_icon);
        // Nút loa: bật/tắt chế độ Text-to-Speech
        iconAutoCheckPhatNguoi.setOnClickListener(view -> {
            speakerEnabled = !speakerEnabled;
            String state = speakerEnabled ? "Loa bật" : "Loa tắt";
            Toast.makeText(MainActivity.this, state, Toast.LENGTH_SHORT).show();
            iconAutoCheckPhatNguoi.setImageResource(speakerEnabled ? R.drawable.tra_phat_nguoi_icon : R.drawable.tra_phat_nguoi_off_icon);
        });

        // Khởi tạo BLUETOOTH
        initBluetooth();

        btnSelectDevice.setOnClickListener(view -> selectDefaultBluetoothDevice());

        // Auto-connect nếu có thiết bị đã lưu
        SharedPreferences prefs = getSharedPreferences("MyBleAppPrefs", MODE_PRIVATE);
        String lastDeviceAddress = prefs.getString("last_device_address", null);
        if (lastDeviceAddress != null) {
            BluetoothDevice lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);
            Toast.makeText(this, "Đang kết nối lại với: " + prefs.getString("last_device_name", ""), Toast.LENGTH_SHORT).show();
            connectToDevice(lastDevice);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Khởi tạo CAMERA (CameraX) và yêu cầu quyền CAMERA
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // Launcher yêu cầu quyền CAMERA
        ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(MainActivity.this, "Quyền sử dụng Camera cần được cấp", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }

        // Khởi tạo TextToSpeech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: Language not supported");
                }
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });
    }

    String textRecord = "";

    // ================================================================
    // REGION: PHƯƠNG THỨC GỬI GHI ÂM
    // ================================================================

    private void sendRecord()
    {

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Khi bắt đầu nói

                Log.d("TTS", "Bắt đầu nói: " + utteranceId);

                isProcessingPlate = true;
            }
            @SuppressLint("SetTextI18n")
            @Override
            public void onDone(String utteranceId) {

                // Khi hoàn thành phát âm
                Log.d("TTS", "Hoàn thành nói: " + utteranceId);
                runOnUiThread(() -> {
                    // Thực hiện hành động sau khi nói xong, ví dụ: cập nhật giao diện
                    startVoiceRecognition();
                    btnRecord.setText("Đang chờ nhận lệnh:");
                    isWaitingRespose = true;

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if(isWaitingRespose)
                        {
                            isWaitingRespose = false;

                            if (speechRecognizer != null) {
                                speechRecognizer.stopListening();
                            }

                            isListening = false;
                            // Cập nhật lại giao diện sau khi dừng ghi âm
                            btnRecord.setText("Bấm để ghi âm");

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                checkAndSendRecord(textRecord);
                            }, 2000);
                        }

                    }, 5000);

                });
            }
            @Override
            public void onError(String utteranceId) {
                Log.e("TTS", "Lỗi khi nói: " + utteranceId);
            }
        });

        String utteranceId = "myTTS_utterance";
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            textRecord = tvResult.getText().toString();
            String textSendRecord = "Bạn có muốn gửi:" + textRecord + "không?";
            tts.speak(textSendRecord, TextToSpeech.QUEUE_FLUSH, null, utteranceId);

        }, 2000);

    }

    private void checkAndSendRecord(String textSendRecord)
    {
        String text = tvResult.getText().toString().toLowerCase();
        // Danh sách từ khóa biểu đạt ý muốn gửi lỗi phạt nguội
        String[] affirmativeKeywords = {
                "có",
                "yes",
                "hãy gửi",
                "đồng ý",
                "ok",
                "vâng",
                "gửi"
        };

        for (String keyword : affirmativeKeywords) {
            if (text.contains(keyword)) {
                sendTextViaBluetooth(textSendRecord);
                Toast.makeText(MainActivity.this, "Xác nhận gửi ghi âm" , Toast.LENGTH_SHORT).show();
                break; // Dừng vòng lặp khi đã nhận diện được
            }
        }
    }

    // ================================================================
    // REGION: PHƯƠNG THỨC GHI ÂM
    // ================================================================

    private void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    runOnUiThread(() -> {
                        //tvResult.setText(RECORDING_PLACEHOLDER);
                        isListening = true;
                    });
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override
                public void onError(int error) {
                    String errorMessage;
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        errorMessage = "Đang xử lý dữ liệu âm thanh...";
                        Log.w(TAG, errorMessage);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                        runOnUiThread(() -> tvResult.setText(""));
                        speechRecognizer.cancel();
                        isListening = false;
                    } else if (error == SpeechRecognizer.ERROR_CLIENT) {
                        errorMessage = "Client side error. Vui lòng thử lại.";
                        Log.e(TAG, errorMessage);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                        runOnUiThread(() -> tvResult.setText(""));
                        speechRecognizer.cancel();
                        isListening = false;
                        initializeSpeechRecognizer();
                    } else if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        errorMessage = "Không nhận được giọng nói";
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                        runOnUiThread(() -> tvResult.setText(""));
                        isListening = false;
                    } else {
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO:
                                errorMessage = "Audio recording error";
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                errorMessage = "Insufficient permissions";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                                errorMessage = "Network error";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                errorMessage = "Network timeout";
                                break;
                            case SpeechRecognizer.ERROR_SERVER:
                                errorMessage = "Server error";
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                errorMessage = "No speech input";
                                break;
                            default:
                                errorMessage = "Unknown error (" + error + ")";
                        }
                        Log.e(TAG, "Voice Recognition Error: " + errorMessage);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Voice Recognition Error: " + errorMessage, Toast.LENGTH_SHORT).show());
                        runOnUiThread(() -> tvResult.setText(""));
                        isListening = false;
                    }
                }
                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        runOnUiThread(() -> {
                            tvResult.setText(recognizedText);

                            tvResult.setText(recognizedText);
                            //sendMessageToAI(recognizedText);

                        });
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Voice recognition is not available on this device.", Toast.LENGTH_SHORT).show());
        }
    }

    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
            speechRecognizer.startListening(intent);
        }
    }

    // ================================================================
    // REGION: PHƯƠNG THỨC BLUETOOTH
    // ================================================================
    @SuppressLint("MissingPermission")
    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        enableBtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth đã bật", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Bluetooth phải được bật để quét thiết bị BLE", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth LE", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void selectDefaultBluetoothDevice() {
        if (!bluetoothAdapter.isEnabled()) {
            // Yêu cầu bật Bluetooth bằng ActivityResultLauncher
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            enableBtLauncher.launch(enableBtIntent);
            return;
        }
        // Kiểm tra permissions cần thiết cho BLE scan
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }
        // Bắt đầu quét
        startBleScan();
    }

    /** Hàm bắt đầu quét BLE */
    @SuppressLint("SetTextI18n")
    private void startBleScan() {
        foundDevices.clear();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Toast.makeText(this, "Không lấy được BLE scanner (Bluetooth có thể bị tắt)", Toast.LENGTH_SHORT).show();
            return;
        }
        // Dừng quét sau một khoảng thời gian định sẵn
        handler.postDelayed(() -> {
            if (scanning) {
                scanning = false;
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bleScanner.stopScan(leScanCallback);
                showDeviceSelectionDialog();
            }
        }, SCAN_PERIOD);
        scanning = true;
        bleScanner.startScan(leScanCallback);
        Toast.makeText(this, "Đang quét thiết bị...", Toast.LENGTH_SHORT).show();
        btnSelectDevice.setText("Đang quét thiết bị...");
    }

    /** Callback khi quét BLE */
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                boolean alreadyFound = false;
                for (BluetoothDevice dev : foundDevices) {
                    if (dev.getAddress().equals(device.getAddress())) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (!alreadyFound) {
                    foundDevices.add(device);
                }

            }

        }
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e("MainActivity", "Quét BLE thất bại, code: " + errorCode);
            btnSelectDevice.setText("Quét thiết bị");
        }
    };

    /** Hiển thị hộp thoại cho người dùng chọn thiết bị */
    @SuppressLint("SetTextI18n")
    private void showDeviceSelectionDialog() {
        btnSelectDevice.setText("Quét thiết bị");
        if (foundDevices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Chọn thiết bị BLE")
                    .setMessage("Không tìm thấy thiết bị nào. Vui lòng thử lại.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            String[] deviceItems = new String[foundDevices.size()];
            for (int i = 0; i < foundDevices.size(); i++) {
                BluetoothDevice dev = foundDevices.get(i);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                String name = dev.getName();
                if (name == null || name.isEmpty()) name = "Unknown Device";
                deviceItems[i] = name + " (" + dev.getAddress() + ")";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Chọn thiết bị BLE")
                    .setItems(deviceItems, (dialog, which) -> {
                        BluetoothDevice selectedDevice = foundDevices.get(which);
                        onDeviceSelected(selectedDevice);
                    })
                    .show();
        }
    }

    /** Xử lý khi người dùng chọn thiết bị từ danh sách */
    private void onDeviceSelected(BluetoothDevice device) {
        SharedPreferences prefs = getSharedPreferences("MyBleAppPrefs", MODE_PRIVATE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        prefs.edit()
                .putString("last_device_address", device.getAddress())
                .putString("last_device_name", device.getName() != null ? device.getName() : "Unknown Device")
                .apply();
        connectToDevice(device);
    }

    /** Kết nối đến thiết bị BLE được chọn */
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothDeviceBLE = device;
        Toast.makeText(this, "Đang kết nối đến " + (device.getName() != null ? device.getName() : "thiết bị") + "...", Toast.LENGTH_SHORT).show();
        // Dùng Application context để tránh các vấn đề về vòng đời của Activity
        bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
    }

    /** Callback GATT xử lý các sự kiện kết nối và dữ liệu */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Kết nối thành công", Toast.LENGTH_SHORT).show());
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                btnSelectDevice.setText("Thiết bị:" + bluetoothDeviceBLE.getName());
                handler.postDelayed(() -> {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    gatt.discoverServices();
                }, 500);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Mất kết nối với thiết bị BLE", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        int props = characteristic.getProperties();
                        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            notifyCharacteristic = characteristic;
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                            gatt.setCharacteristicNotification(notifyCharacteristic, true);
                            BluetoothGattDescriptor cccd = notifyCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            if (cccd != null) {
                                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(cccd);
                            }
                        }
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            writeCharacteristic = characteristic;
                        }
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Dịch vụ BLE đã được khám phá", Toast.LENGTH_SHORT).show());
            } else {
                Log.w("MainActivity", "onServicesDiscovered lỗi: status " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            if (characteristic.equals(notifyCharacteristic)) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    String text = new String(data);
                    runOnUiThread(() -> {
                        TextView receiveText = findViewById(R.id.tvResult);
                        receiveText.append(text + "\n");
                    });
                }
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("MainActivity", "Ghi thành công cho characteristic " + characteristic.getUuid());
            } else {
                Log.w("MainActivity", "Ghi thất bại cho characteristic " + characteristic.getUuid() + " với status " + status);
            }
        }
    };

    /** Kiểm tra các permission cần thiết cho BLE */
    @SuppressLint("ObsoleteSdkInt")
    private boolean hasBlePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    /** Yêu cầu các permission cần thiết cho BLE */
    @SuppressLint("ObsoleteSdkInt")
    private void requestBlePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                    new String[]{ Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT },
                    REQUEST_BLE_PERMISSIONS
            );
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_BLE_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startBleScan();
            } else {
                Toast.makeText(MainActivity.this, "Cần có permission Bluetooth để quét thiết bị", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendTextViaBluetooth(String text) {
        if (writeCharacteristic != null && bluetoothGatt != null) {
            if (!text.isEmpty()) {
                byte[] value = text.getBytes();
                writeCharacteristic.setValue(value);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                if (!success) {
                    Toast.makeText(MainActivity.this, "Gửi dữ liệu thất bại", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Gửi dữ liệu thành công", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "Chưa kết nối với thiết bị BLE", Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================
    // REGION: CAMERA, ML KIT OCR & CALL API
    // ================================================================
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (isProcessingPlate) {
                        imageProxy.close();
                        return;
                    }
                    @SuppressLint("UnsafeOptInUsageError")
                    Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        textRecognizer.process(inputImage)
                                .addOnSuccessListener(visionText -> {
                                    String recognizedText = visionText.getText();
                                    String cleanedText = recognizedText.replaceAll("[\\s\\.-]+", "").trim().toUpperCase();
                                    String plate = extractLicensePlate(cleanedText);
                                    if (plate != null && !plate.equals(edtBienSoXe.getText().toString().trim())) {
                                        runOnUiThread(() -> {
                                            if(!askingToSendPenalty)
                                            {
                                                edtBienSoXe.setText(plate);
                                                isProcessingPlate = true;
                                                callTrafficFineAPI(plate);
                                            }
                                        });
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Auto OCR failed: ", e))
                                .addOnCompleteListener(task -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                // Lưu lại đối tượng Camera để điều khiển zoom
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis); // Modified for camera zoom
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Lỗi khởi tạo camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "startCamera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- OCRCallback interface ---
    private interface OCRCallback {
        void onPlateFound(String licensePlate);
        void onNoPlateFound();
    }

    private void attemptOCRWithRotation(final Bitmap originalBitmap, final int[] angles, final int index, final OCRCallback callback) {
        if (index >= angles.length) {
            callback.onNoPlateFound();
            return;
        }
        final Bitmap rotatedBitmap = rotateBitmap(originalBitmap, angles[index]);
        if (rotatedBitmap == null) {
            attemptOCRWithRotation(originalBitmap, angles, index + 1, callback);
            return;
        }
        InputImage image = InputImage.fromBitmap(rotatedBitmap, 0);
        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String recognizedText = visionText.getText();
                    Log.d("MLKit", "Recognized text (rotation " + angles[index] + "°): " + recognizedText);
                    String cleanedText = recognizedText.replaceAll("[\\s\\.-]+", "").trim().toUpperCase();
                    String plate = extractLicensePlate(cleanedText);
                    if (plate != null) {
                        callback.onPlateFound(plate);
                    } else {
                        if (!rotatedBitmap.isRecycled()) {
                            rotatedBitmap.recycle();
                        }
                        attemptOCRWithRotation(originalBitmap, angles, index + 1, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MLKit", "Text recognition error (rotation " + angles[index] + "°)", e);
                    if (!rotatedBitmap.isRecycled()) {
                        rotatedBitmap.recycle();
                    }
                    attemptOCRWithRotation(originalBitmap, angles, index + 1, callback);
                });
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        try {
            int newWidth = source.getWidth() / 2;
            int newHeight = source.getHeight() / 2;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(angle);
            Bitmap rotated = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
            if (scaledBitmap != source && !scaledBitmap.isRecycled()) {
                scaledBitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    // Sửa hàm extractLicensePlate để bắt biển số có 4-5 số ở cuối
    private String extractLicensePlate(String cleanedText) {
        String regexStandard         = "\\d{2}[A-Z]{1,2}\\d{4,6}";
        String regexDiplomatic       = "\\d{2}CD\\d{4,6}";
        String regexMilitary         = "\\d{2}QT\\d{4,6}";
        String regexTaxi             = "\\d{2}TX\\d{4,6}";
        String regexStandardAlphaNum = "\\d{2}[A-Z0-9]{1,2}\\d{4,6}";
        String[] regexPatterns = new String[] {
                regexStandard,
                regexDiplomatic,
                regexMilitary,
                regexTaxi,
                regexStandardAlphaNum
        };
        for (String regex : regexPatterns) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(cleanedText);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    private void resumeCamera() {
        previewView.setForeground(null);
    }

    private void callTrafficFineAPI(String licensePlate) {
        startLoadingAnimation();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        TrafficFineApi api = retrofit.create(TrafficFineApi.class);
        PlateRequest request = new PlateRequest(licensePlate);
        api.getViolations(request).enqueue(new Callback<ApiResponse>() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                stopLoadingAnimation();
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse apiResponse = response.body();
                    if (apiResponse.getStatus() == 1) {
                        StringBuilder result = new StringBuilder();
                        List<Violation> violations = apiResponse.getData();
                        if (violations != null && !violations.isEmpty()) {
                            for (Violation v : violations) {
                                result.append(v.toString()).append("\n\n");
                            }
                        } else {
                            result.append("Không có vi phạm phạt nguội.");
                        }
                        DataInfo dataInfo = apiResponse.getDataInfo();
                        if (dataInfo != null) {
                            result.append("\nTổng số: ").append(dataInfo.getTotal())
                                    .append("\nChưa xử phạt: ").append(dataInfo.getChuaxuphat())
                                    .append("\nĐã xử phạt: ").append(dataInfo.getDaxuphat())
                                    .append("\nLatest: ").append(dataInfo.getLatest());
                            numberOfPenalty = dataInfo.getChuaxuphat();
                            licensePlatePenalty = Objects.requireNonNull(violations).isEmpty() ? null : violations.get(0).getBienKiemSoat();

                            if (speakerEnabled && numberOfPenalty > 0) {
                                askingToSendPenalty = true;

                                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                    @Override
                                    public void onStart(String utteranceId) {
                                        Log.d("TTS", "Bắt đầu nói: " + utteranceId);
                                        isProcessingPlate = true;
                                    }
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void onDone(String utteranceId) {
                                        Log.d("TTS", "Hoàn thành nói: " + utteranceId);
                                        runOnUiThread(() -> {
                                            startVoiceRecognition();
                                            btnRecord.setText("Đang chờ nhận lệnh:");
                                            isWaitingRespose = true;

                                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                if(isWaitingRespose)
                                                {
                                                    isWaitingRespose = false;
                                                    if (speechRecognizer != null) {
                                                        speechRecognizer.stopListening();
                                                    }
                                                    isListening = false;
                                                    btnRecord.setText("Bấm để ghi âm");

                                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                        checkAndRunSendPhatnguoi();
                                                        isProcessingPlate = false;
                                                        askingToSendPenalty = false;
                                                    }, 2000);
                                                }

                                            }, 5000);

                                        });
                                    }
                                    @Override
                                    public void onError(String utteranceId) {
                                        Log.e("TTS", "Lỗi khi nói: " + utteranceId);
                                    }
                                });

                                String utteranceId = "myTTS_utterance";
                                tts.speak(textSpeakWhenReceivePhatNguoi, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
                            }
                        }
                        isProcessingPlate = false;
                        txtKetQuaPhatNguoi.setText(result.toString());
                    } else {
                        txtKetQuaPhatNguoi.setText("Không có lỗi phạt nguội");
                        Toast.makeText(MainActivity.this, "Lỗi API: " + response.code(), Toast.LENGTH_SHORT).show();
                        isProcessingPlate = false;
                    }

                    runOnUiThread(() -> {
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Lỗi API: " + response.code(), Toast.LENGTH_SHORT).show();
                    isProcessingPlate = false;
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                stopLoadingAnimation();
                Toast.makeText(MainActivity.this, "Lỗi kết nối API: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                runOnUiThread(() -> {
                    isProcessingPlate = false;
                });
            }
        });
    }

    private void checkAndRunSendPhatnguoi() {
        String text = tvResult.getText().toString().toLowerCase();
        String[] affirmativeKeywords = {
                "có",
                "yes",
                "hãy gửi",
                "đồng ý",
                "ok",
                "vâng",
                "gửi đi",
                "gửi lỗi",
                "gửi",
                "gửi phạt"
        };
        for (String keyword : affirmativeKeywords) {
            if (text.contains(keyword)) {
                String textSend = licensePlatePenalty + " CO " + numberOfPenalty + " LOI PHAT NGUOI CHUA XU LY.";
                sendTextViaBluetooth(txtKetQuaPhatNguoi.getText().toString());
                Toast.makeText(MainActivity.this, "xác nhận gửi: " + textSend , Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    // ================================================================
    // REGION: RETROFIT API INTERFACE & MODEL CLASSES
    // ================================================================
    public interface TrafficFineApi {
        @POST("phatnguoi")
        Call<ApiResponse> getViolations(@Body PlateRequest request);
    }

    public static class PlateRequest {
        private String bienso;
        public PlateRequest(String bienso) { this.bienso = bienso; }
        public String getBienso() { return bienso; }
        public void setBienso(String bienso) { this.bienso = bienso; }
    }

    public class ApiResponse {
        private int status;
        private String msg;
        private List<Violation> data;
        @SerializedName("data_info")
        private DataInfo dataInfo;
        public int getStatus() { return status; }
        public String getMsg() { return msg; }
        public List<Violation> getData() { return data; }
        public DataInfo getDataInfo() { return dataInfo; }

        public void setStatus(int status) {
            this.status = status;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public void setData(List<Violation> data) {
            this.data = data;
        }

        public void setDataInfo(DataInfo dataInfo) {
            this.dataInfo = dataInfo;
        }
    }

    public static class DataInfo {
        private int total;
        private int chuaxuphat;
        private int daxuphat;
        private String latest;
        public int getTotal() { return total; }
        public int getChuaxuphat() { return chuaxuphat; }
        public int getDaxuphat() { return daxuphat; }
        public String getLatest() { return latest; }
    }

    public static class Violation {
        @SerializedName("Biển kiểm soát")
        private String bienKiemSoat;
        @SerializedName("Màu biển")
        private String mauBien;
        @SerializedName("Loại phương tiện")
        private String loaiPhuongTien;
        @SerializedName("Thời gian vi phạm")
        private String thoiGianViPham;
        @SerializedName("Địa điểm vi phạm")
        private String diaDiemViPham;
        @SerializedName("Hành vi vi phạm")
        private String hanhViViPham;
        @SerializedName("Trạng thái")
        private String trangThai;
        @SerializedName("Đơn vị phát hiện vi phạm")
        private String donViPhatHien;
        @SerializedName("Nơi giải quyết vụ việc")
        private List<String> noiGiaiQuyet;
        public String getBienKiemSoat() { return bienKiemSoat; }
        public String getMauBien() { return mauBien; }
        public String getLoaiPhuongTien() { return loaiPhuongTien; }
        public String getThoiGianViPham() { return thoiGianViPham; }
        public String getDiaDiemViPham() { return diaDiemViPham; }
        public String getHanhViViPham() { return hanhViViPham; }
        public String getTrangThai() { return trangThai; }
        public String getDonViPhatHien() { return donViPhatHien; }
        public List<String> getNoiGiaiQuyet() { return noiGiaiQuyet; }
        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Biển kiểm soát: ").append(bienKiemSoat).append("\n")
                    .append("Màu biển: ").append(mauBien).append("\n")
                    .append("Loại phương tiện: ").append(loaiPhuongTien).append("\n")
                    .append("Thời gian vi phạm: ").append(thoiGianViPham).append("\n")
                    .append("Địa điểm vi phạm: ").append(diaDiemViPham).append("\n")
                    .append("Hành vi vi phạm: ").append(hanhViViPham).append("\n")
                    .append("Trạng thái: ").append(trangThai).append("\n")
                    .append("Đơn vị phát hiện vi phạm: ").append(donViPhatHien).append("\n")
                    .append("Nơi giải quyết vụ việc: ");
            if (noiGiaiQuyet != null && !noiGiaiQuyet.isEmpty()) {
                for (String s : noiGiaiQuyet) {
                    sb.append("\n   - ").append(s);
                }
            }
            return sb.toString();
        }
    }
    // ================================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        if (textRecognizer != null) { textRecognizer.close(); }
    }

    // ================================================================
    // REGION: ANIMATION
    // ================================================================
    private void startLoadingAnimation() {
        dotCount = 0;
        loadingRunnable = new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) { dots.append("."); }
                txtKetQuaPhatNguoi.setText("Đang tra phạt nguội" + dots);
                dotCount++;
                if (dotCount > 10) { dotCount = 0; }
                loadingHandler.postDelayed(this, 500);
            }
        };
        loadingHandler.post(loadingRunnable);
    }

    private void stopLoadingAnimation() {
        if (loadingRunnable != null) { loadingHandler.removeCallbacks(loadingRunnable); }
    }
}
