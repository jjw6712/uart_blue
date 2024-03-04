package com.example.uart_blue.FileManager;

import java.util.Date;

public class LineEntry {
    Date date;
    String text;

    public LineEntry(Date date, String text) {
        this.date = date;
        this.text = text;
    }

    public Date getDate() {
        return date;
    }

    public String getText() {
        return text;
    }
}
