package com.example.uart_blue;

public class GpioControl {
    static {
        System.loadLibrary("native-lib"); // 'native-lib'는 C 코드를 컴파일한 라이브러리 이름입니다.
    }

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
