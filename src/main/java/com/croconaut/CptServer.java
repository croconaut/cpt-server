package com.croconaut;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public class CptServer {
    public static void main(String[] args) {
        /*
        Logger logger = Logger.getLogger(Sender.class.getName());
        logger.setLevel(Level.ALL);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        logger.addHandler(consoleHandler);
        */

        try {
            // This will load the MySQL driver, each DB has its own driver
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            System.out.println("mysql ok");

            boolean skipLoading = false;
            for (String arg : args) {
                if (arg.equals("-clean")) {
                    skipLoading = true;
                }
            }
            if (!skipLoading) {
                MySqlAccess.loadNonPersistent();
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());

                        FileWriter fileWriter = new FileWriter("exit_port.txt");
                        fileWriter.write(String.valueOf(serverSocket.getLocalPort()));
                        fileWriter.close();
                        System.out.println("Exit port listening at: " + serverSocket.getLocalPort());

                        serverSocket.accept();

                        MySqlAccess.saveNonPersistent();

                        System.exit(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            ListeningThread listeningThread = new ListeningThread();
            listeningThread.start();
            listeningThread.join();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
  }
}
