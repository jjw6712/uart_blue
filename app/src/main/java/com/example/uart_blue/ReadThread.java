package com.example.uart_blue;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
import java.util.TimeZone;
public class ReadThread extends Thread {
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

                byte[] buffer = new byte[14]; // 14바이트 패킷 (새로운 필드 포함)
                int size = mInputStream.read(buffer);
                if (size == 14) { // 새로운 패킷 크기 확인
                    String timestamp = dateFormat.format(new Date());

                    // 패킷에서 데이터 추출 (바이너리 데이터)
                    int pressure = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                    int waterLevel = ((buffer[4] & 0xFF) << 8) | (buffer[5] & 0xFF);
                    int humidity = buffer[6] & 0xFF; // 0 ~ 100
                    int battery = buffer[7] & 0x07; // 0 ~ 7
                    int drive = buffer[8] & 0x01; // 0 or 1
                    int stop = buffer[9] & 0x01; // 0 or 1
                    int wh = buffer[10] & 0x01; // 0 or 1
                    int blackout = buffer[11] & 0x01; // 0 or 1


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
}
