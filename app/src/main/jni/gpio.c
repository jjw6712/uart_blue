//
// Created by jinwoo on 2024-02-05.
//
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include "com_example_uart_blue_GpioControl.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <sys/stat.h>

JNIEXPORT void JNICALL
Java_com_example_uart_1blue_GpioControl_initializeGpioPins(JNIEnv *env, jobject thiz) {
    const char *pins[] = {"138", "139", "28"};
    const int num_pins = sizeof(pins) / sizeof(pins[0]);
    int fd;


    for (int i = 0; i < num_pins; ++i) {
        // GPIO 핀을 export합니다.
        fd = open("/sys/class/gpio/export", O_WRONLY);
        if (fd < 0) {
            // 에러 처리
            continue;
        }
        write(fd, pins[i], strlen(pins[i]));
        close(fd);

        // GPIO 방향을 설정합니다. (여기서는 "in"으로 입력 모드 설정)
        char dir_path[64];
        snprintf(dir_path, sizeof(dir_path), "/sys/class/gpio/gpio%s/direction", pins[i]);
        fd = open(dir_path, O_WRONLY);
        if (fd < 0) {
            // 에러 처리
            continue;
        }
        write(fd, "in", 2);
        close(fd);

        // GPIO 값을 설정합니다. (여기서는 "1"로 높음 상태 설정)
        char value_path[64];
        snprintf(value_path, sizeof(value_path), "/sys/class/gpio/gpio%s/value", pins[i]);
        fd = open(value_path, O_WRONLY);
        if (fd < 0) {
            // 에러 처리
            continue;
        }
        write(fd, "1", 1);
        close(fd);
    }
}

JNIEXPORT int JNICALL
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



