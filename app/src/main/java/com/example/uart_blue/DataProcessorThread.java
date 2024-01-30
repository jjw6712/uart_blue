package com.example.uart_blue;

import java.util.concurrent.BlockingQueue;

class DataProcessorThread implements Runnable {
    private final BlockingQueue<String> queue;
    private StringBuilder logEntries = new StringBuilder();
    private int localCounter = 0;
    private final int MAX_ENTRIES = 1000;

    public DataProcessorThread(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String logEntry = queue.take();
                logEntries.append(logEntry).append("\n");
                localCounter++;

                if (localCounter >= MAX_ENTRIES) {
                    saveData(logEntries.toString());
                    logEntries.setLength(0);
                    localCounter = 0;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void saveData(String data) {
        // 데이터 저장 로직 (예: 파일에 쓰기)
    }
}
