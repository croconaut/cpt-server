package com.croconaut;

import com.croconaut.cpt.network.NetworkUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.sql.SQLException;

public class CommandThread extends LoggableThread {
    private final Socket socket;

    public CommandThread(Socket socket) {
        log("CommandThread");

        this.socket = socket;
    }

    @Override
    public void run() {
        log("run");

        try {
            socket.setSoTimeout(NetworkUtil.SOCKET_TIMEOUT);
            socket.setTcpNoDelay(true);

            InputStream is = socket.getInputStream();
            int cmd = is.read();
            log("Received command: " + cmd);
            switch (cmd) {
                case NetworkUtil.DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY:
                    new DownloadMessagesAndAttachmentsDeliveryThread(socket).start();
                    break;
                case NetworkUtil.UPLOAD_TOKEN:
                    new UploadTokenThread(socket).start();
                    break;
                case NetworkUtil.UPLOAD_NON_LOCAL_MESSAGES:
                    new UploadNonLocalMessagesThread(socket).start();
                    break;
                case NetworkUtil.UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS:
                    new UploadLocalMessagesWithAttachmentsThread(socket).start();
                    break;
                case NetworkUtil.DOWNLOAD_ATTACHMENTS:
                    new DownloadAttachmentsThread(socket).start();
                    break;
                case NetworkUtil.UPLOAD_FRIENDS:
                    new UploadFriendsThread(socket).start();
                    break;
                default:
                    System.err.println("Unknown command: " + cmd);
                    break;
            }
        } catch (IOException | SQLException e) {
            log(e);
        }
    }
}
