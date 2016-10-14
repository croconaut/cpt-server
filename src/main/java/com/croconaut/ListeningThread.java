package com.croconaut;

import com.croconaut.cpt.network.NetworkUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ListeningThread extends LoggableThread {
    private ServerSocket serverSocket;

    public ListeningThread() {
        log("ListeningThread");

        try {
            // unbound server socket
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(NetworkUtil.APP_SERVER_PORT));
        } catch (IOException e) {
            log(e);
        }
    }

    @Override
    public void run() {
        log("run");

        while (serverSocket.isBound() && !isInterrupted()) {
            try {
                Socket socket = serverSocket.accept();
                new CommandThread(socket).start();
            } catch (IOException e) {
                log(e);
            }
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            log(e);
        }
    }
}
