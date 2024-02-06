package com.example.uart_blue;


import java.io.DataOutputStream;
import java.io.IOException;

public class GpioControl {

    static {
        System.loadLibrary("gpio_port"); // 'native-lib'는 C 코드를 컴파일한 라이브러리 이름입니다.
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

    public boolean isGpioActive(int pin) {
        int value = readGpioValue(pin);
        return value == 0; // 활성 상태를 낮은 신호로 가정
    }
}

