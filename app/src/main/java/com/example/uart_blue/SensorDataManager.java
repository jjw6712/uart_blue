package com.example.uart_blue;

public class SensorDataManager {
    private static SensorDataManager instance;

    private double maxPressure;
    private double expectedPressurePercentage;
    private int selectedTime; // 초 단위
    private double lastPressurePercentage = -1;
    private long lastPressureTime = -1;


    private SensorDataManager() {
        // 초기화
    }

    public static SensorDataManager getInstance() {
        if (instance == null) {
            instance = new SensorDataManager();
        }
        return instance;
    }

    public synchronized void setMaxPressure(double maxPressure) {
        this.maxPressure = maxPressure;
    }

    public synchronized double getMaxPressure() {
        return maxPressure;
    }

    public synchronized void setExpectedPressurePercentage(double percentage) {
        this.expectedPressurePercentage = percentage;
    }

    public synchronized double getExpectedPressurePercentage() {
        return expectedPressurePercentage;
    }

    public synchronized void setSelectedTime(int seconds) {
        this.selectedTime = seconds;
    }

    public synchronized int getSelectedTime() {
        return selectedTime;
    }
    public synchronized void updatePressureData(double currentPressurePercentage) {
        long currentTime = System.currentTimeMillis();
        // 첫 번째 유효값으로 초기화 조건 수정
        if (lastPressurePercentage == -1) {
            lastPressurePercentage = currentPressurePercentage;
            lastPressureTime = currentTime;
        } else {
            // 설정된 시간이 지났거나 압력이 0까지 떨어졌다 다시 상승한 경우 업데이트
            if (currentTime - lastPressureTime >= selectedTime * 1000 || currentPressurePercentage < lastPressurePercentage) {
                lastPressurePercentage = currentPressurePercentage;
                lastPressureTime = currentTime;
            }
        }
    }



    public synchronized double getLastPressurePercentage() {
        return lastPressurePercentage;
    }

    public synchronized long getLastPressureTime() {
        return lastPressureTime;
    }
}

