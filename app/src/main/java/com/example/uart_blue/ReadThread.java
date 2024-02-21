package com.example.uart_blue;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;

import android_serialport_api.SerialPort;

public class ReadThread extends Thread {
    private static final String TAG = "UART_Logging";
    private Context context; // Context 변수 추가
    private static final int PACKET_SIZE = 8; // 14바이트 패킷 (새로운 필드 포함)
    private static final byte STX = 0x021;  // 시작 바이트
    private static final byte ETX = 0x03;  // 종료 바이트

    private FileSaveThread fileSaveThread;
    private int localCounter = 0;
    private StringBuilder logEntries = new StringBuilder();
    private SimpleDateFormat dateFormat;
    private InputStream mInputStream;
    private IDataReceiver dataReceiver;
    private SerialPort mSerialPort;
    protected OutputStream mOutputStream;

    private long lastSaveTime = System.currentTimeMillis(); // 마지막 저장 시간 초기화
    private final long SAVE_INTERVAL = 1000; // 데이터 저장 간격 (1초)
    private ConcurrentLinkedQueue<byte[]> packetQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 콜백 인터페이스 정의
    public interface IDataReceiver {
        void onReceiveData(String data);
        void onError(Exception e);
    }

    public ReadThread(String portPath, int baudRate, IDataReceiver receiver, Context context) {
        this.context = context; // Context 초기화
        this.dataReceiver = receiver;
        this.fileSaveThread = new FileSaveThread(context);
        this.fileSaveThread.start(); // FileSaveThread 시작
        this.dateFormat = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        try {
            mSerialPort = new SerialPort(new File(portPath), baudRate, 0);
            mInputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();
            startPacketProcessing(); // 패킷 처리 시작
        } catch (IOException e) {
            Log.e(TAG, "Error opening serial port: " + e.getMessage());
            handleError(e);
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
                        data = mInputStream.read();
                        if (data == -1) break;
                        packet[count++] = (byte) data;
                    }
                    if (packet[PACKET_SIZE - 2] == ETX) {
                        if(packetQueue !=null) packetQueue.offer(packet.clone()); // 패킷 큐에 추가
                    }
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void startPacketProcessing() {
        final Runnable processor = new Runnable() {
            public void run() {
                processPackets();
            }
        };
        scheduler.scheduleAtFixedRate(processor, 0, 1, TimeUnit.SECONDS);
    }

    private void processPackets() {
        int packetsToProcess = Math.min(1000, packetQueue.size());
        for (int i = 0; i < packetsToProcess; i++) {
            byte[] packet = packetQueue.poll();
            if (packet != null) {
                processPacket(packet);
            }
        }
    }

    private void processPacket(byte[] packet) {
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
            int batteryPercentage = (battery * 123) / 0x7F;

            // drive 상태를 추출합니다. (3번째 비트)
            int drive = (statusByte & 0x08) >> 3; // 0x08 = 0000 1000

            // stop 상태를 추출합니다. (2번째 비트)
            int stop = (statusByte & 0x04) >> 2; // 0x04 = 0000 0100

            // blackout 상태를 추출합니다. (1번째 비트)
            int blackout = (statusByte & 0x01); // 0x01 = 0000 0001
            byte etx = packet[6];

            // 현재 시간과 함께 로그 기록 생성
            String timestamp = dateFormat.format(new Date());
            int wh = 0;
            String logEntry = String.format(
                    "%s, %d, %.2f, %.2f, %d, %d, %d, %d, %d",
                    timestamp, // 현재 시간 (년, 월, 일, 시, 분, 초)
                    ++localCounter,
                    pressurePercentage, // 수압
                    waterLevelPercentage, // 수위
                    batteryPercentage, // 배터리 잔량
                    drive, // 드라이브 상태
                    stop, // 정지 상태
                    wh , // WH 상태
                    blackout // 블랙아웃 상태

            );

            logEntries.append(logEntry + "\n");
            long currentTime = System.currentTimeMillis();
            if (localCounter >= 1000) { // 지정된 시간 간격(1초)이 경과했는지 확인
                if (logEntries.length() > 0) { // 로그 데이터가 있을 경우에만 저장
                    fileSaveThread.addData(logEntries.toString());
                    logEntries.setLength(0); // 로그 기록 초기화
                }
                lastSaveTime = currentTime; // 마지막 저장 시간 업데이트
                localCounter = 0;
            }
            // 패킷 처리가 성공했을 때 Broadcast Intent 생성 및 전송
            Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
            intent.putExtra("wh", wh);
            //intent.putExtra("humidity", 0);
            intent.putExtra("battery", battery);
            intent.putExtra("blackout", blackout);
            context.sendBroadcast(intent);
        } else {
            Log.e(TAG, "체크섬 오류!!!");
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
    // ReadThread와 FileSaveThread를 정지하는 메소드
    public void stopThreads() {
        // ReadThread 중단
        interrupt();
        clearBuffer(); // 버퍼 초기화

        // FileSaveThread 중단
        if (fileSaveThread != null) {
            fileSaveThread.stopSaving();  // FileSaveThread에 중단 신호 보내기
            fileSaveThread.interrupt();   // FileSaveThread 중단
            fileSaveThread = null;        // 참조 해제
        }

        // packetQueue 비우기
        if (packetQueue != null) {
            packetQueue.clear(); // packetQueue의 모든 요소 제거
            packetQueue = null;  // 참조 해제
        }

        // scheduler 종료
        if (scheduler != null) {
            scheduler.shutdownNow(); // 진행 중인 모든 작업을 중단하고 스레드 풀을 종료
            scheduler = null;        // 참조 해제
        }

        Log.d(TAG, "ReadThread, FileSaveThread, and scheduler stopped");
    }
}

