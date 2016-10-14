package com.croconaut;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public abstract class LoggableThread extends Thread {
    protected void log(String str) {
        Date now = Calendar.getInstance().getTime();
        String strNow = new SimpleDateFormat("dd-MM HH:mm:ss.SSS").format(now);

        System.out.println(String.format("%s %05d %s: %s", strNow, Thread.currentThread().getId(), getClass().getSimpleName(), str));
    }

    protected void log(Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        printWriter.flush();

        log(stringWriter.toString());
    }
}
