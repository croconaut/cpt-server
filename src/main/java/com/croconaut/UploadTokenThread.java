package com.croconaut;

import com.croconaut.cpt.network.NetworkHeader;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.TreeSet;

public class UploadTokenThread extends CptSyncThread {
    protected UploadTokenThread(Socket socket) throws SQLException {
        super(socket);
    }

    @Override
    public void run() {
        log("run");

        try {
            initializeDataStreams();

            receiveCrocoIdAndUsername();

            receiveToken();

            shutdown();

            // now we're sure both parties have the same content
        } catch (IOException e) {
            log(e);
        } finally {
            close();
        }
    }

    private void receiveToken() throws IOException {
        log("receiveToken");

        String token = dis.readUTF();

        if (mySqlAccess.getToken(crocoId) == null) {
            log("Token for " + crocoId + " was null, notifying the device");

            TreeSet<NetworkHeader> headersForDownload = mySqlAccess.getHeadersForDownload(crocoId, true);
            for (NetworkHeader header : headersForDownload) {
                if (header.getTo().equals(crocoId)) {
                    log("Message directly for this croco id found");
                    clientsToNotify.put(crocoId, true);
                    break;
                } else if (!clientsToNotify.containsKey(crocoId)) {
                    clientsToNotify.put(crocoId, false);
                    // continue, what if there's a header with to == crocoId
                }
            }
        }

        mySqlAccess.setNameAndToken(crocoId, name, token);

        log("Token for " + crocoId + " is " + token);
        includeMyself = true;
        notifyClients();
    }
}
