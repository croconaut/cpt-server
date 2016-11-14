package com.croconaut;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.SQLException;

public class CptServer {
    public static String SERVER_API_KEY;
    public static String JDBC_DB_NAME;
    public static String JDBC_USERNAME;
    public static String JDBC_PASSWORD;

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

            FileReader fileReader = new FileReader("jdbc.txt");
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            JDBC_DB_NAME = bufferedReader.readLine();
            JDBC_USERNAME = bufferedReader.readLine();
            JDBC_PASSWORD = bufferedReader.readLine();
            if (JDBC_DB_NAME == null || JDBC_USERNAME == null || JDBC_PASSWORD == null) {
                throw new IllegalArgumentException("jdbc credentials file is corrupted");
            }
            // test the connection
            new MySqlAccess();

            fileReader = new FileReader("api_key.txt");
            bufferedReader = new BufferedReader(fileReader);
            SERVER_API_KEY = bufferedReader.readLine();
            if (SERVER_API_KEY == null) {
                throw new IllegalArgumentException("api key file is corrupted");
            }

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
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | InterruptedException | IOException | SQLException e) {
            e.printStackTrace();
        }
  }
}
