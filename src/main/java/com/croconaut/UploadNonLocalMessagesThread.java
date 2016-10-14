package com.croconaut;

import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkMessage;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

public class UploadNonLocalMessagesThread extends CptSyncThread {
    private TreeSet<NetworkHeader> receivedHeaders;

    protected UploadNonLocalMessagesThread(Socket socket) throws SQLException {
        super(socket);
    }

    @Override
    public void run() {
        log("run");

        try {
            initializeDataStreams();

            receiveCrocoIdAndUsername();

            receiveSyncPreference();

            receiveBlockedCrocoIds();

            receiveHeaders();

            sendMissingHeaders();

            receiveMissingMessages();

            shutdown();

            // now we're sure both parties have the same content
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log(e);
        } finally {
            close();
        }
    }

    private void receiveHeaders() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        log("receiveHeaders");

        //noinspection unchecked
        receivedHeaders = (TreeSet<NetworkHeader>) StreamUtil.readStreamablesFrom(dis);

        log("received " + receivedHeaders.size() + " headers");
    }

    private void sendMissingHeaders() throws IOException {
        log("sendMissingHeaders");

        // get all our headers (exclude blocked and include only for this croco id, if desired)
        TreeSet<NetworkHeader> ourHeaders = mySqlAccess.getHeadersForUpload();
        log("we have " + ourHeaders.size() + " headers from this croco id");

        List<NetworkHeader> missingHeaders = mySqlAccess.getOpaqueHeaders(receivedHeaders, ourHeaders);
        StreamUtil.writeStreamablesTo(dos, missingHeaders);
        dos.flush();

        log("sent a request for " + missingHeaders.size() + " headers missing on our side");
    }

    private void receiveMissingMessages() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        log("receiveMissingMessages");

        //noinspection unchecked
        List<NetworkMessage> missingMessages = (List<NetworkMessage>) StreamUtil.readStreamablesFrom(dis);
        processMessages(missingMessages);

        log("received " + missingMessages.size() + " messages");
    }
}
