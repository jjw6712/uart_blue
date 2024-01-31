package com.example.uart_blue;

import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
import java.util.TimeZone;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ReadThread extends Thread {
    private static final String TAG = "UART_Logging";
    private int localCounter = 0;
    private StringBuilder logEntries = new StringBuilder();
    private SimpleDateFormat dateFormat;
    private InputStream mInputStream;
    private IDataReceiver dataReceiver;
    private static final int PACKET_SIZE = 14; // 14바이트 패킷 (새로운 필드 포함)


    // 콜백 인터페이스 정의
    public interface IDataReceiver {
        void onReceiveData(String data);
        void onError(Exception e);
    }

    public ReadThread(InputStream inputStream, IDataReceiver receiver) {
        this.mInputStream = inputStream;
        this.dataReceiver = receiver;
        this.dateFormat = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                if (mInputStream == null) return;
                byte[] buffer = new byte[PACKET_SIZE];
                int size = mInputStream.read(buffer);
                if (size == PACKET_SIZE ) {
                    Log.d(TAG, "Received Data: " + bytesToHex(buffer));
                    // 체크섬 검증
                    byte checksumcheck = calculateChecksum(buffer);
                    if (buffer[PACKET_SIZE - 1] != checksumcheck) {
                        System.out.println("체크섬 오류!");
                        continue; // 체크섬 불일치, 패킷 무시
                    }

                    String timestamp = dateFormat.format(new Date());

                    // 패킷에서 데이터 추출 (바이너리 데이터)
                    byte stx = buffer[0];
                    byte cmd = buffer[1];
                    int pressure = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                    int waterLevel = ((buffer[4] & 0xFF) << 8) | (buffer[5] & 0xFF);
                    int humidity = buffer[6] & 0xFF;
                    int battery = buffer[7] & 0xFF;
                    int drive = buffer[8] & 0xFF;
                    int stop = buffer[9] & 0xFF;
                    int wh = buffer[10] & 0xFF;
                    int blackout = buffer[11] & 0xFF;
                    byte etx = buffer[12];
                    byte checksum = buffer[13];

                    String logEntry = String.format(
                            "(%s, %d, %d, %d, %d%%, %d) (%d, %d, %d, %d)",
                            timestamp, // 현재 시간 (년, 월, 일, 시, 분, 초)
                            ++localCounter, // 카운터
                            pressure, // 수압
                            waterLevel, // 수위
                            humidity, // 습도
                            battery, // 배터리 잔량
                            drive, // 드라이브 상태
                            stop, // 정지 상태
                            wh, // WH 상태
                            blackout // 블랙아웃 상태
                    );

                    logEntries.append(logEntry + "\n");

                    if (localCounter >= 1000) {
                        saveData(logEntries.toString());
                        logEntries.setLength(0);
                        localCounter = 0;
                    }
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
        // 스레드가 중단될 때 남은 데이터를 저장
        if (logEntries.length() > 0) {
            saveData(logEntries.toString());
        }
    }
    // 바이트 배열을 HEX 형식의 문자열로 변환하는 메소드
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("0x%02X", b));
        }
        return sb.toString().trim();
    }
    private void saveData(String data) {
        if (dataReceiver != null) {
            dataReceiver.onReceiveData(data);
        }
    }

    private void handleError(Exception e) {
        if (dataReceiver != null) {
            dataReceiver.onError(e);
        }
    }

    private byte calculateChecksum(byte[] data) {
        byte checksum = 0x00; // 체크섬 초기값을 0x00으로 설정
        // STX부터 ETX까지 모든 바이트의 XOR 연산
        for (int i = 0; i < data.length-1; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }

}
