package com.croconaut.cpt.network;

public class NetworkUtil {
    public static final int ATTACHMENT_BUFFER_SIZE = 1024 * 1024;    // 1 MB
    public static final int  SERVER_SOCKET_TIMEOUT = 1 * 1000;
    public static final int         SOCKET_TIMEOUT = 30 * 1000;
    public static final int     CONNECTION_TIMEOUT = 10 * 1000;

    public static final int        APP_SERVER_PORT = 50001;

    public static final int DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY = 0x01;
    public static final int                               UPLOAD_TOKEN = 0x02;
    public static final int                  UPLOAD_NON_LOCAL_MESSAGES = 0x04;
    public static final int     UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS = 0x08;
    public static final int                       DOWNLOAD_ATTACHMENTS = 0x10;
    public static final int                             UPLOAD_FRIENDS = 0x20;
}
