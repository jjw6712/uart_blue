package com.example.uart_blue;

import android.content.Context;
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

import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import android.content.Context;
import android.util.Log;

public class ReadThread extends Thread {
    private static final String TAG = "UART_Logging";
    private static final int PACKET_SIZE = 14; // 14바이트 패킷 (새로운 필드 포함)
    private static final byte STX = 0x02;  // 시작 바이트
    private static final byte ETX = 0x03;  // 종료 바이트

    private FileSaveThread fileSaveThread;
    private int localCounter = 0;
    private StringBuilder logEntries = new StringBuilder();
    private SimpleDateFormat dateFormat;
    private InputStream mInputStream;
    private IDataReceiver dataReceiver;

    // 콜백 인터페이스 정의
    public interface IDataReceiver {
        void onReceiveData(String data);
        void onError(Exception e);
    }

    public ReadThread(InputStream inputStream, IDataReceiver receiver, Context context) {
        this.mInputStream = inputStream;
        this.dataReceiver = receiver;
        this.fileSaveThread = new FileSaveThread(context);
        this.fileSaveThread.start(); // FileSaveThread 시작
        this.dateFormat = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Override
    public void run() {
        clearBuffer(); // 버퍼 초기화
        byte[] buffer = new byte[PACKET_SIZE];

        while (!isInterrupted()) {
            try {
                if (mInputStream == null) return;

                int data = mInputStream.read();  // 1바이트 읽기
                if (data == -1) continue;  // 데이터가 없으면 계속

                byte readByte = (byte) data;
                if (readByte == STX) {  // 패킷 시작 바이트(STX)를 만나면
                    int bytesRead = mInputStream.read(buffer, 1, PACKET_SIZE - 1);  // 나머지 패킷 읽기
                    if (bytesRead == PACKET_SIZE - 1 && buffer[PACKET_SIZE - 2] == ETX) {
                        buffer[0] = STX;  // 버퍼 첫 바이트에 STX 추가
                        processPacket(buffer);  // 패킷 처리
                    }
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void processPacket(byte[] packet) {
        // STX, ETX 검증이 이미 완료된 상태
        byte checksum = calculateChecksum(packet, PACKET_SIZE - 1);
        if (packet[PACKET_SIZE - 1] == checksum) {  // 체크섬 검증
            // 패킷에서 데이터 추출
            byte stx = packet[0];
            byte cmd = packet[1];
            int pressure = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
            int waterLevel = ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF);
            int humidity = packet[6];
            int battery = packet[7];
            int drive = packet[8];
            int stop = packet[9];
            int wh = packet[10];
            int blackout = packet[11];
            byte etx = packet[12];

            // 현재 시간과 함께 로그 기록 생성
            String timestamp = dateFormat.format(new Date());
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

            // 로컬 카운터가 1000에 도달하면 로그 데이터를 FileSaveThread에 전달하고 로그 기록 초기화
            if (localCounter >= 1000) {
                fileSaveThread.addData(logEntries.toString());
                logEntries.setLength(0);
                localCounter = 0;
            }
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
}

