package com.croconaut.cpt.data;

import java.util.Date;

public interface MessageAttachment {
    String getName();

    long getLength();

    Date getLastModified();

    String getType();

    String getStorageDirectory();

    String getPath();   // "emulated" uri

    String getSourceUri();
}
