package com.croconaut;

import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.network.NetworkUtil;
import com.croconaut.cpt.network.UriIdentifierResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DownloadAttachmentsThread extends CptSyncThread {
    private Set<MessageAttachmentIdentifier> uploadRequests;
    private Map<UriIdentifierResponse, List<MessageAttachmentIdentifier>> requestedAttachments;

    protected DownloadAttachmentsThread(Socket socket) throws SQLException {
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

            receiveRequests();

            sendAttachments();

            shutdown();

            // now we're sure both parties have the same content

            markAsDelivered();
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log(e);
        } finally {
            close();
        }
    }

    private void receiveRequests() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        log("receiveRequests");

        //noinspection unchecked
        uploadRequests = (Set<MessageAttachmentIdentifier>) StreamUtil.readStreamablesFrom(dis);

        log("received " + uploadRequests.size() + " upload requests");
    }

    private void sendAttachments() throws IOException {
        log("sendAttachments");

        requestedAttachments = mySqlAccess.getAttachments(uploadRequests);
        Set<UriIdentifierResponse> uriIdentifierResponses = requestedAttachments.keySet();

        nonBufferedDos.writeInt(uriIdentifierResponses.size());
        for (UriIdentifierResponse uriIdentifierResponse : uriIdentifierResponses) {
            // we don't transmit the path but we do differentiate between the same sourceUri and different paths
            StreamUtil.writeStreamableTo(nonBufferedDos, uriIdentifierResponse);

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(new File(uriIdentifierResponse.getPath()));

                byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
                int len;
                long total = 0;
                long startTime = System.currentTimeMillis();
                int bytesPerSecond = 0;
                while (total != uriIdentifierResponse.getLength() && (len = fis.read(buffer, 0, (int) Math.min(buffer.length, uriIdentifierResponse.getLength() - total))) != -1) {
                    nonBufferedDos.write(buffer, 0, len);
                    total += len;
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - startTime < 1000) {
                        currentTime = startTime + 1000;
                    }
                    bytesPerSecond = (int) ((total * 1000) / (currentTime - startTime));
                }

                if (total != uriIdentifierResponse.getLength()) {
                    throw new IOException("read() ended prematurely");
                }

                log("Sent [" + uriIdentifierResponse.getPath() + "] at " + bytesPerSecond / 1024 + " KB/s: " + uriIdentifierResponse.getName());
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }
        nonBufferedDos.flush();

        log("sent " + uriIdentifierResponses.size() + " unique attachments");
    }

    private void markAsDelivered() {
        log("markAsDelivered");

        for (List<MessageAttachmentIdentifier> messageAttachmentIdentifiers : requestedAttachments.values()) {
            // notify attachment recipients
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                mySqlAccess.markAttachmentAsDelivered(messageAttachmentIdentifier);
                if (clientsToNotify.get(messageAttachmentIdentifier.getFrom()) == null) {
                    // don't overwrite true (and false is useless...)
                    clientsToNotify.put(messageAttachmentIdentifier.getFrom(), false);  // 'delivered' => low priority
                }
            }
        }

        notifyClients();
    }
}
