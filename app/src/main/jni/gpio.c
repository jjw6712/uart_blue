//
// Created by jinwoo on 2024-02-05.
//


#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include "com_example_uart_blue_GpioControl.h"

JNIEXPORT jint JNICALL
Java_com_example_uart_1blue_GpioControl_readGpioValue(JNIEnv *env, jobject thiz, jint pin) {
    char path[64];
    char value_str[3];
    int fd;

    // GPIO 값 파일 경로를 설정합니다.
    snprintf(path, sizeof(path), "/sys/class/gpio/gpio%d/value", pin);

    // GPIO 값 파일을 엽니다.
    fd = open(path, O_RDONLY);
    if (fd < 0) {
        // 파일을 열 수 없는 경우
        return -1;
    }

    // GPIO 값 파일에서 값을 읽습니다.
    if (read(fd, value_str, 3) < 0) {
        // 읽기 오류
        close(fd);
        return -1;
    }

    // 파일을 닫습니다.
    close(fd);

    // 문자열로 읽은 값을 정수로 변환합니다.
    return (value_str[0] == '0') ? 0 : 1;
}


