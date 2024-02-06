package com.example.uart_blue;


import java.io.DataOutputStream;
import java.io.IOException;

public class GpioControl {

    static {
        System.loadLibrary("native-lib"); // 'native-lib'는 C 코드를 컴파일한 라이브러리 이름입니다.
    }
    public void initializeGpioPinsjava() {
        try {
            // Execute superuser command to set SELinux to permissive mode
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            // Change permissions of the export file to read and write
            outputStream.writeBytes("chmod 666 /sys/class/gpio/export\n");

            // Flush and close the output stream
            outputStream.flush();
            outputStream.close();

            // Wait for the command to complete
            su.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new SecurityException("Failed to obtain superuser permissions or change file permissions", e);
        }

        // Call the native method to continue initializing the GPIO pins
        initializeGpioPins();
    }
    // GPIO 핀을 초기화하는 네이티브 메소드
    private native void initializeGpioPins();

    // 네이티브 메소드 선언
    public native int readGpioValue(int pin);

    // GPIO 상태에 따라 텍스트를 업데이트하는 메소드
    public String getPinStatus(int pin) {
        int value = readGpioValue(pin);
        switch (pin) {
            case 138:
                return value == 0 ? "운전" : "대기";
            case 139:
                return value == 0 ? "정지" : "대기";
            case 28:
                return value == 0 ? "수격" : "대기";
            default:
                return "알 수 없음";
        }
    }
}

