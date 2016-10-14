package com.croconaut;

import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkMessage;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

public class DownloadMessagesAndAttachmentsDeliveryThread extends CptSyncThread {
    private TreeSet<NetworkHeader> receivedHeaders;

    protected DownloadMessagesAndAttachmentsDeliveryThread(Socket socket) throws SQLException {
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

            sendMessages();

            receiveAcks();

            sendDeliveredIdentifiers();

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

    private void sendMessages() throws IOException {
        log("sendMessages");

        TreeSet<NetworkHeader> ourHeaders = mySqlAccess.getHeadersForDownload(crocoId, fullSync);
        log("we have " + ourHeaders.size() + " headers for this croco id");

        List<NetworkHeader> missingHeaders = mySqlAccess.getOpaqueHeaders(ourHeaders, receivedHeaders);
        List<NetworkMessage> missingMessages = mySqlAccess.getMessages(missingHeaders);
        StreamUtil.writeStreamablesTo(dos, missingMessages);
        dos.flush();

        log("sent " + missingMessages.size() + " messages");
    }

    private void receiveAcks() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        log("receiveAcks");

        //noinspection unchecked
        List<NetworkMessage> ackMessages = (List<NetworkMessage>) StreamUtil.readStreamablesFrom(dis);
        processMessages(ackMessages);

        log("received " + ackMessages.size() + " ACK messages");
    }

    private void sendDeliveredIdentifiers() throws IOException {
        log("sendDeliveredIdentifiers");

        List<MessageAttachmentIdentifier> deliveredAttachments = mySqlAccess.getDeliveredAttachments(crocoId);
        StreamUtil.writeStreamablesTo(dos, deliveredAttachments);
        dos.flush();

        log("sent " + deliveredAttachments.size() + " delivered attachment confirmations");
    }
}
