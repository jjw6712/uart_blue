package com.example.uart_blue;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.util.Log;

public class SensorDataManager {
    private static SensorDataManager instance;

    private double maxPressure;
    private double expectedPressurePercentage;
    private int selectedTime; // 초 단위
    private double lastPressurePercentage = -1;
    private long lastPressureTime = -1;
    private long lastPressureCountTime = 1000;

    private double lastPressurecount = -1;


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

    public synchronized void updatePressurecount(double currentPressurePercentage) {
        lastPressurecount = currentPressurePercentage;
    }
    public synchronized double getLastPressurecount() {
        return lastPressurecount;
    }


    public synchronized double getLastPressurePercentage() {
        return lastPressurePercentage;
    }

    public synchronized long getLastPressureTime() {
        return lastPressureTime;
    }

    public synchronized boolean checkForWaterHammer(double currentPressurePercentage) {
        long currentTime = System.currentTimeMillis();
        // 설정된 시간이 지났는지 확인
        if ((lastPressureTime == -1) || (currentTime - lastPressureTime >= selectedTime * 1000)) {
            double pressureChangePercentage = (currentPressurePercentage - lastPressurePercentage) / lastPressurePercentage * 100;
            Log.e(TAG, "pressureChangePercentage: "+pressureChangePercentage+"=  "+"("+currentPressurePercentage+"-"+lastPressurePercentage+")"+"/"+lastPressurePercentage+"* 100"+" 계산된 압력: "+expectedPressurePercentage);

            // 마지막 압력 백분율과 시간 업데이트는 조건 판단 전에 수행
            lastPressurePercentage = currentPressurePercentage;
            lastPressureTime = currentTime;

            // 설정된 압력 변화량을 초과했는지 확인
            if (pressureChangePercentage >= expectedPressurePercentage) {
                Log.e(TAG, "checkForWaterHammer: "+pressureChangePercentage+" >= "+expectedPressurePercentage);
                // 수격 조건 충족
                return true;
            }
        }
        return false;
    }

}

