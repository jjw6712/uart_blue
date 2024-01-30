package com.example.uart_blue;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.io.InputStream;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReadThread extends Thread {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final SimpleDateFormat dateFormat;
    private final InputStream mInputStream;
    private final IDataReceiver dataReceiver;
    private final byte STX = 0x02;
    private final byte ETX = 0x03;
    private final int BUFFER_SIZE = 14;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private int localCounter = 0;

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
                int size = mInputStream.read(buffer);
                if (size == BUFFER_SIZE && buffer[0] == STX && buffer[BUFFER_SIZE - 2] == ETX) {
                    // Checksum validation
                    byte checksum = calculateChecksum(Arrays.copyOfRange(buffer, 0, BUFFER_SIZE - 1));
                    if (checksum == buffer[BUFFER_SIZE - 1]) {
                        processPacket();
                    } else {
                        handleError(new Exception("Checksum Mismatch"));
                    }
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private byte calculateChecksum(byte[] data) {
        byte checksum = 0x00;
        for (byte b : data) {
            checksum ^= b;
        }
        return checksum;
    }

    private void processPacket() {
        String timestamp = dateFormat.format(new Date());
        int pressure = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
        int waterLevel = ((buffer[4] & 0xFF) << 8) | (buffer[5] & 0xFF);
        int humidity = buffer[6];
        int battery = buffer[7];
        int drive = buffer[8];
        int stop = buffer[9];
        int wh = buffer[10];
        int blackout = buffer[11];

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

        dataReceiver.onReceiveData(logEntry);
    }

    private void handleError(Exception e) {
        dataReceiver.onError(e);
    }
}
