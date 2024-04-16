package com.example.uart_blue;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.util.Log;

import com.example.uart_blue.FileManager.FileManager;
import com.example.uart_blue.FileManager.FileProcessingTask;
import com.example.uart_blue.Network.TcpClient;

import android_serialport_api.SerialPort;

public class ReadThread extends Thread {
    private static final String TAG = "UART_Logging";
    private Context context; // Context 변수 추가
    private static final int PACKET_SIZE = 8; // 14바이트 패킷 (새로운 필드 포함)
    private static final byte STX = 0x021;  // 시작 바이트
    private static final byte ETX = 0x03;  // 종료 바이트

    private FileSaveThread fileSaveThread;
    private int localCounter = -1;
    private StringBuilder logEntries = new StringBuilder();
    private SimpleDateFormat dateFormat;
    private InputStream mInputStream;
    private IDataReceiver dataReceiver;
    private SerialPort mSerialPort;
    protected OutputStream mOutputStream;

    private long lastSaveTime = System.currentTimeMillis(); // 마지막 저장 시간 초기화
    private int whcount = 0;
    BlockingQueue<byte[]> packetQueue = new LinkedBlockingQueue<>(2000);
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    OptionActivity optionActivity;
    private boolean isWhConditionProcessing = false; // wh 판단 로직 실행 중 플래그
    boolean isSleep = false;
    int wh = 0;
    private Handler zipFileSavedHandler = new Handler(Looper.getMainLooper());
    private Runnable zipFileSavedRunnable;
    private AtomicInteger packetsReceivedPerSecond = new AtomicInteger(0); // 1초마다 수신된 패킷 수 추적
    private ScheduledFuture<?> packetProcessorFuture;
    private ScheduledFuture<?> packetCountLoggerFuture;
    public TcpClient tcpClient;
    private boolean isSerialPortOpen = false; // 시리얼 포트 열림 상태 추적
    private boolean isstop = false;

    // 콜백 인터페이스 정의
    public interface IDataReceiver {
        void onReceiveData(String data);
        void onError(Exception e);
    }

    public ReadThread(String portPath, int baudRate, IDataReceiver receiver, Context context) {
        this.context = context.getApplicationContext(); // 애플리케이션 컨텍스트 사용
        this.dataReceiver = receiver;
        this.fileSaveThread = new FileSaveThread(context);
        this.fileSaveThread.start(); // FileSaveThread 시작
        this.dateFormat = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        if (!isSerialPortOpen) { // 시리얼 포트가 열려 있지 않은 경우에만 실행
            openSerialPort(portPath, baudRate);
        }
    }
    public synchronized void openSerialPort(String portPath, int baudRate) {
        if (mSerialPort == null) {
            try {
                mSerialPort = new SerialPort(new File(portPath), baudRate, 0);
                mInputStream = mSerialPort.getInputStream();
                mOutputStream = mSerialPort.getOutputStream();
                isSerialPortOpen = true; // 시리얼 포트 열림
                startPacketProcessing(); // 패킷 처리 시작
            } catch (IOException e) {
                Log.e(TAG, "Error opening serial port: " + e.getMessage());
                handleError(e);
            }
        }
    }
    public void sendDataToSerialPort(byte[] data) {
        try {
            if (mOutputStream != null) {
                mOutputStream.write(data);
                Log.d(TAG, "Data sent to serial port.");
            } else {
                Log.e(TAG, "OutputStream is null, cannot send data.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while sending data to serial port: " + e.getMessage());
        }
    }
    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                if (mInputStream == null) return;

                int data = mInputStream.read();
                if (data == -1) continue;

                byte readByte = (byte) data;
                byte[] packet = new byte[PACKET_SIZE]; // 패킷 초기화
                //Log.d(TAG, "run: "+bytesToHex(packet));
                if (readByte == STX) {
                    packet[0] = readByte;
                    int count = 1;
                    while (count < PACKET_SIZE) { // 패킷 채우기
                        if (mInputStream == null) return;
                        data = mInputStream.read();
                        if (data == -1) break;
                        packet[count++] = (byte) data;
                    }
                    if (packet[PACKET_SIZE - 2] == ETX) {
                        if(packetQueue !=null) packetQueue.offer(packet.clone()); // 패킷 큐에 추가
                        packetsReceivedPerSecond.incrementAndGet(); // 패킷 수 증가
                        //Log.d(TAG, "run: "+bytesToHex(packet));
                    }
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void startPacketProcessing() {
        final Runnable processor = () -> processPackets();
        packetProcessorFuture = scheduler.scheduleAtFixedRate(processor, 1, 1, TimeUnit.SECONDS);

        // 1초마다 패킷 수 출력 및 초기화
        final Runnable packetCountLogger = () -> {
            int packetsThisSecond = packetsReceivedPerSecond.getAndSet(0);
            Log.d(TAG, "1초 동안 받은 패킷 수: " + packetsThisSecond);
        };
        packetCountLoggerFuture = scheduler.scheduleAtFixedRate(packetCountLogger, 1, 1, TimeUnit.SECONDS);
    }

    private void processPackets() {
        long processingStartTime = System.currentTimeMillis(); // 패킷 처리 시작 시간 기록
        int packetsToProcess = Math.min(1000, packetQueue.size());
        for (int i = 0; i < packetsToProcess; i++) {
            byte[] packet = packetQueue.poll();
            if (packet != null) {
                processPacket(packet, processingStartTime);
            }
        }
    }

    private void processPacket(byte[] packet, long processingStartTime) {
        // STX, ETX 검증이 이미 완료된 상태
        byte checksum = calculateChecksum(packet, PACKET_SIZE - 1);
        if (packet[PACKET_SIZE - 1] == checksum) {  // 체크섬 검증
            // 패킷에서 데이터 추출
            byte stx = packet[0];
            int pressure = ((packet[1] & 0xFF) << 8) | (packet[2] & 0xFF);
            int waterLevel = ((packet[3] & 0xFF) << 8) | (packet[4] & 0xFF);

            // 수압과 수위 백분율 계산
            double pressurePercentage = calculatePercentageFromADC(pressure);
            double waterLevelPercentage = calculatePercentageFromADC(waterLevel);

            // 패킷에서 5번째 바이트를 가져옵니다.
            byte statusByte = packet[5];

            // battery 값을 추출합니다. (하위 7비트 사용)
            int battery = statusByte & 0x7F; // 0x7F = 0111 1111
            // battery 백분율 계산
            int batteryPercentage = (battery * 106) / 0x7F;

            // drive 상태를 추출합니다. (3번째 비트)
            int drive = (statusByte & 0x08) >> 3; // 0x08 = 0000 1000

            // stop 상태를 추출합니다. (2번째 비트)
            int stop = (statusByte & 0x04) >> 2; // 0x04 = 0000 0100

            double currentPressurePercentage = pressurePercentage;
            // blackout 상태를 추출합니다. (1번째 비트)
            int blackout = (statusByte & 0x01); // 0x01 = 0000 0001
            byte etx = packet[6];
            SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
            String selectedSensorType = sharedPreferences.getString("SelectedSensorType", "");
            String sensor = "";
            if (selectedSensorType.equals("5kg")){
                sensor = "1";
            } else if (selectedSensorType.equals("20kg")) {
                sensor = "2";
            }
            else if (selectedSensorType.equals("30kg")) {
                sensor = "3";
            }else {
                sensor = "4";
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("Press", (float) pressurePercentage);
            editor.putFloat("WLevel", (float) waterLevelPercentage);
            editor.apply(); // 변경 사항을 한 번에 적용

            // SensorDataManager에서 마지막 압력 백분율과 시간을 가져옵니다.
            SensorDataManager dataManager = SensorDataManager.getInstance();
            double lastPressurePercentage = dataManager.getLastPressurePercentage();
            double lastPressurecount = dataManager.getLastPressurecount();
            long lastPressureTime = dataManager.getLastPressureTime();
            // 현재 시간을 기준으로 지난 시간을 계산합니다.
            long currentTime = System.currentTimeMillis();
            long timeDifference = currentTime - lastPressureTime;
            BigDecimal currentPressureRounded = BigDecimal.valueOf(currentPressurePercentage).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lastPressurecountRounded = BigDecimal.valueOf(lastPressurecount).setScale(2, RoundingMode.HALF_UP);

            //Log.d(TAG, "수압"+currentPressurePercentage+"이전 수압%"+lastPressurePercentage+"현재 시간"+timeDifference+"설정 시간"+presspur);
            // 만약 설정된 시간이 지났다면 현재 압력 백분율을 비교하여 wh 변수를 업데이트합니다.
            // 현재 압력 백분율과 시간을 업데이트하는 메서드 내부에서
            // 수압 백분율 계산

            if (!isWhConditionProcessing && dataManager.checkForWaterHammer(pressurePercentage)) {
                isWhConditionProcessing = true; // 수격 판단 로직 실행 중
                // 필요한 액션 수행, 예: SharedPreferences 업데이트, 로깅, 파일 저장 등
                sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
                int whcount = sharedPreferences.getInt("whcountui", 0) + 1;
                sharedPreferences.edit().putInt("whcountui", whcount).apply();
                Log.d(TAG, "수격 발생: 현재 수압 백분율 = " + pressurePercentage);
                    Log.d(TAG, "현재수압" + currentPressurePercentage + "이전수압" + lastPressurePercentage);
                // 여기서 현재 압력 백분율과 시간을 항상 업데이트합니다.
                dataManager.updatePressureData(currentPressurePercentage);
                ZipFileSaved(); // 조건 충족 시 수행할 작업
            }else if (isWhConditionProcessing && dataManager.checkForWaterHammer(pressurePercentage)) {
                dataManager.updatePressurecount(currentPressurePercentage); // lastPressurecount 업데이트
                if (currentPressureRounded.compareTo(lastPressurecountRounded) > 0) {
                    //Log.e(TAG, "이전 수압: " + lastPressurecountRound ed + "현재 수압: " + currentPressureRounded);
                    wh = 1; // 현재 수압이이전 수압보다 높을 때 wh를 1로 설정
                    dataManager.updatePressurecount(currentPressurePercentage); // lastPressurecount 업데이트
                } else {
                    wh = 0;
                    dataManager.updatePressurecount(currentPressurePercentage); // lastPressurecount 업데이트
                }
            }
            dataManager.updatePressurecount(currentPressurePercentage); // lastPressurecount 업데이트
            //if(currentPressurePercentage == 99 || currentPressurePercentage == 0){
                //Log.d(TAG, "압력: "+currentPressurePercentage);
            //}

            // 패킷 처리 시작 시간을 기반으로 타임스탬프 생성
            String timestamp = dateFormat.format(new Date(processingStartTime));


            String logEntry = String.format(
                    "%s, %d, %.2f, %.2f, %d, %d, %d, %d, %d, %s",
                    timestamp, // 현재 시간 (년, 월, 일, 시, 분, 초)
                    ++localCounter,
                    pressurePercentage, // 수압
                    waterLevelPercentage, // 수위
                    batteryPercentage, // 배터리 잔량
                    drive, // 드라이브 상태
                    stop, // 정지 상태
                    wh , // WH 상태
                    blackout, // 블랙아웃 상태
                    sensor

            );
            if (batteryPercentage <= 16 && blackout == 1) {
                 //사용자에게 슬립 모드 전환을 권장하는 알림 표시
                recommendSleepMode();
                isSleep = true;
            }
            if (isSleep == true && blackout == 0) {
                // 웨이크업 모드 로직
                wakeUpFromSleepMode();
                isSleep = false;
            }
            logEntries.append(logEntry + "\n");

            // 로컬 카운터가 999에 도달했을 때의 처리
            if (localCounter == 998 && isstop) {
                // 정지 플래그가 설정되었을 때 마지막 로그 엔트리에 drive=0, stop=1 반영
                stop = 1;
                drive = 0;
                // 마지막 로그 엔트리 수정
                logEntry = String.format(
                        "%s, %d, %.2f, %.2f, %d, %d, %d, %d, %d, %s",
                        timestamp,
                        ++localCounter,
                        pressurePercentage,
                        waterLevelPercentage,
                        batteryPercentage,
                        drive,
                        stop,
                        wh,
                        blackout,
                        sensor
                );
                // 마지막 로그 엔트리를 logEntries에 추가
                logEntries.append(logEntry + "\n");
            }
            //long currentTime = System.currentTimeMillis();
            if (localCounter >= 999) { // 1000개의 패킷이 처리되었는지 확인
                if (isstop) {
                    //Log.e(TAG, "멈춤플래그"+isstop);
                    stop = 1;
                    drive = 0;
                }

                if (logEntries.length() > 0) { // 로그 데이터가 있을 경우에만 저장
                    fileSaveThread.addData(logEntries.toString(), timestamp);
                    logEntries.setLength(0); // 로그 기록 초기화

                    byte[] decimalData  = createDecimalData(timestamp, pressurePercentage, waterLevelPercentage, batteryPercentage, drive, stop, wh, blackout, sensor);
                    // Context와 바이너리 데이터를 사용하여 TcpClient 인스턴스 생성
                    tcpClient = new TcpClient(context, decimalData );
                    tcpClient.execute(); // AsyncTask 실행하여 서버로 데이터 전송
                    // 패킷 처리가 성공했을 때 Broadcast Intent 생성 및 전송
                    Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
                    intent.putExtra("wh", whcount);
                    intent.putExtra("battery", batteryPercentage);
                    intent.putExtra("blackout", blackout);
                    context.sendBroadcast(intent);
                }
                lastSaveTime = currentTime; // 마지막 저장 시간 업데이트
                localCounter = -1;
                whcount = 0;
            }

        } else {
            //Log.e(TAG, "체크섬 오류!!!");
        }
    }


    private byte calculateChecksum(byte[] data, int length) {
        byte checksum = 0; // 체크섬 초기값을 0x00으로 설정
        for (int i = 0; i < length; i++) {
            checksum ^= data[i]; // XOR 연산
        }
        return checksum;
    }

    private void clearBuffer() {
        if (mInputStream != null) {
            try {
                while (mInputStream.available() > 0) {
                    mInputStream.read(); // 버퍼 비우기
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to clear buffer: " + e.getMessage());
            }
        }
    }

    private void handleError(Exception e) {
        if (dataReceiver != null) {
            dataReceiver.onError(e);
        }
    }
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("0x%02X ", b));
        }
        return sb.toString().trim();
    }
    public byte[] createDecimalData(String timestamp, double pressurePercentage, double waterLevelPercentage, int batteryPercentage, int drive, int stop, int wh, int blackout, String sensor) {
        // 데이터를 문자열 형태로 결합
        String data = String.format("%s, %.2f, %.2f, %d, %d, %d, %d, %d, %s",
                timestamp, pressurePercentage, waterLevelPercentage,
                batteryPercentage, drive, stop, wh, blackout, sensor);

        // 문자열 데이터를 바이트 배열로 변환 (UTF-8 인코딩 사용)
        return data.getBytes(StandardCharsets.UTF_8);
    }
    private double calculatePercentageFromADC(int adcValue) {
        // ADC 값의 최소 및 최대 범위 설정
        int minADCValue = 8191;    // 0.4V에 해당
        double maxADCValue = 40400;   // 2V에 해당

        // 최소값을 기준으로 한 값의 조정
        double adjustedValue = adcValue - minADCValue;
        double range = maxADCValue - minADCValue;

        // 조정된 값으로 백분율 계산
        double percentage = (adjustedValue / range) * 100.0;

        // 결과가 0% 미만이면 0%로, 100% 초과면 100%로 조정
        percentage = Math.max(0, Math.min(percentage, 100.0));

        return percentage;
    }

    public void ZipFileSaved() {
        Log.d(TAG, "수격신호 수신");
        saveWHState(true);
        Date eventTime = new Date(); // 현재 시간을 수격이 발생한 시간으로 가정
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        String directoryUriString = sharedPreferences.getString("directoryUri", "");
        String deviceNumber = sharedPreferences.getString("deviceNumber", "");
        long beforeMillis = sharedPreferences.getLong("beforeMillis", 0);
        long afterMillis = sharedPreferences.getLong("afterMillis", 0);
        Log.e(TAG, "정상적인 수격시 이전, 이후 시간: "+ beforeMillis+" ~ "+afterMillis);
        String selectedSensorType = sharedPreferences.getString("SelectedSensorType", "");
        String sensor = "";
        if (selectedSensorType.equals("5kg")){
            sensor = "1";
        } else if (selectedSensorType.equals("20kg")) {
            sensor = "2";
        }
        else if (selectedSensorType.equals("30kg")) {
            sensor = "3";
        }else {
            sensor = "4";
        }


        // 이벤트 발생 후 대기할 시간 계산
        long delay = afterMillis;

        // Define the task to be run
        String finalSensor = sensor;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Uri directoryUri = Uri.parse(directoryUriString);
            List<Uri> filesInRange = FileManager.findFilesInRange(context, directoryUri, eventTime, beforeMillis, afterMillis);

            if (!filesInRange.isEmpty()) {
                // eventTime을 기반으로 파일 이름 생성
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
                String eventDateTime = sdf.format(eventTime);
                String combinedFileName = deviceNumber+"-"+ finalSensor +"-"+eventDateTime + ".txt";
                Uri combinedFileUri = FileManager.combineTextFilesInRange(context, filesInRange, combinedFileName, eventTime, beforeMillis, afterMillis);

                if (combinedFileUri != null) {
                    List<Uri> fileUris = FileManager.findFilesInRange(context, directoryUri, eventTime, beforeMillis, afterMillis);
                    new FileProcessingTask(context, fileUris, combinedFileName, eventTime, beforeMillis, afterMillis, directoryUri).execute();

                }
            } else {
                Log.d(TAG, "지정된 시간 범위 내에서 일치하는 파일이 없습니다.");
            }
            isWhConditionProcessing = false;
            saveWHState(false);
        }, delay);
    }


    private void recommendSleepMode() { //슬립모드
        //stopThreads(); // 모든 스레드와 리소스 해제
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(context)) {
                try {
                    // 화면 밝기를 가장 낮은 값으로 설정
                    Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> optionActivity.requestWriteSettingsPermission());
                }
            }
        }
    }
    private void wakeUpFromSleepMode() { //웨이크업 모드
        // 화면 밝기를 사용자 설정 또는 기본값으로 복원
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(context)) {
                try {
                    int brightness = 255; // 화면 밝기를 최대로 설정하거나, 사용자 설정에 맞게 조정
                    Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        Intent serviceIntent = new Intent(context, SerialService.class);
        context.startService(serviceIntent); // 서비스 시작
    }
    // ReadThread와 FileSaveThread를 정지하는 메소드
    public void stopThreads() {
        // ReadThread 중단
        interrupt();
        clearBuffer(); // 버퍼 초기화
        try {
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }
            if (mOutputStream != null) {
                mOutputStream.flush();
                mOutputStream.close();
                mOutputStream = null;
            }
            if (mSerialPort != null) {
                mSerialPort.close();
                mSerialPort = null;
                isSerialPortOpen = false; // 시리얼 포트 닫힘
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing resources: " + e.getMessage());
        }
        // FileSaveThread 중단
        if (fileSaveThread != null) {
            fileSaveThread.stopSaving();  // FileSaveThread에 중단 신호 보내기
            // FileSaveThread 중단
            fileSaveThread = null;        // 참조 해제
        }

        // packetQueue 비우기
        if (packetQueue != null) {
            packetQueue.clear(); // packetQueue의 모든 요소 제거
            packetQueue = null;  // 참조 해제
        }
        if (logEntries != null) {
            logEntries = null;
        }


        if (dataReceiver != null) {
            dataReceiver = null;
        }
        if (zipFileSavedHandler != null && zipFileSavedRunnable != null) {
            zipFileSavedHandler.removeCallbacks(zipFileSavedRunnable);
            zipFileSavedHandler = null;
            zipFileSavedRunnable = null;
        }
        // 스케줄된 태스크 취소
        if (packetProcessorFuture != null && !packetProcessorFuture.isCancelled()) {
            packetProcessorFuture.cancel(true); // 태스크 취소
        }

        if (packetCountLoggerFuture != null && !packetCountLoggerFuture.isCancelled()) {
            packetCountLoggerFuture.cancel(true); // 태스크 취소
        }

        // 필요한 경우 스케줄러 자체를 종료
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (tcpClient != null){
            tcpClient = null;
        }
        saveGPIOState(false, true);
        saveWHState(false);
        Log.d(TAG, "ReadThread, FileSaveThread, and scheduler stopped");
    }
    public void checkStop(){
        isstop = true;
        Log.e(TAG, "멈춤플래그"+isstop);
    }
    // GPIO 상태 저장
    private void saveGPIOState(boolean is138Active, boolean is139Active) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("GPIO_138_ACTIVE", is138Active);
        editor.putBoolean("GPIO_139_ACTIVE", is139Active);
        editor.apply();
    }
    public void saveWHState(boolean is28Active) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("GPIO_28_ACTIVE", is28Active);
        editor.apply();
    }

}

