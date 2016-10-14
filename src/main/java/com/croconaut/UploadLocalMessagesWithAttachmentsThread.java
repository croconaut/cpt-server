package com.croconaut;

import com.croconaut.cpt.common.util.FileUtil;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkMessage;
import com.croconaut.cpt.network.NetworkUtil;
import com.croconaut.cpt.network.UriIdentifier;
import com.croconaut.cpt.network.UriIdentifierResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class UploadLocalMessagesWithAttachmentsThread extends CptSyncThread {
    private TreeSet<NetworkHeader> receivedHeaders;
    private Map<String, List<MessageAttachmentIdentifier>> downloadRequests;

    protected UploadLocalMessagesWithAttachmentsThread(Socket socket) throws SQLException {
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

            receiveMessageAttachmentIdentifiers();

            sendRequests();

            receiveAttachments();

            receiveHeaders();

            sendHeaders();

            receiveMessages();

            shutdown();

            // now we're sure both parties have the same content
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log(e);
        } finally {
            close();
        }
    }

    private void receiveMessageAttachmentIdentifiers() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        log("receiveMessageAttachmentIdentifiers");

        //noinspection unchecked
        Set<MessageAttachmentIdentifier> receivedAttachmentIdentifiers = (Set<MessageAttachmentIdentifier>) StreamUtil.readStreamablesFrom(dis);
        downloadRequests = mySqlAccess.getOpaqueMessageAttachmentIdentifiers(receivedAttachmentIdentifiers);

        log("received " + receivedAttachmentIdentifiers.size() + " attachment identifiers");
    }

    private void sendRequests() throws IOException {
        log("sendRequests");

        Set<UriIdentifier> uriIdentifiers = new HashSet<>();
        for (String sourceUri : downloadRequests.keySet()) {
            // UriIdentifier contains zero target storage directories, we expect the client to fill this out in UriIdentifierResponse
            // Also, we must use UriIdentifier because it's Streamable and String isn't :)
            uriIdentifiers.add(new UriIdentifier(sourceUri));
        }
        StreamUtil.writeStreamablesTo(dos, uriIdentifiers);
        dos.flush();

        log("sent request for " + uriIdentifiers.size() + " uri identifiers");
    }

    private void receiveAttachments() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        log("receiveAttachments");

        int uriResponsesCount = dis.readInt();
        for (int i = 0; i < uriResponsesCount; ++i) {
            UriIdentifierResponse uriIdentifierResponse = (UriIdentifierResponse) StreamUtil.readStreamableFrom(dis);
            if (downloadRequests.containsKey(uriIdentifierResponse.getSourceUri())) {
                // TODO: this should be perhaps stored into an app-specific directory
                final String thisCrocoIdPath = ((CptSyncThread) Thread.currentThread()).getPath();
                File file = new File(new File(thisCrocoIdPath, "attachments").getPath(), uriIdentifierResponse.getName());
                final File parent = file.getParentFile();
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
                if (!file.createNewFile()) {
                    // file not created because it exists
                    file = File.createTempFile(
                            FileUtil.getBaseName(uriIdentifierResponse.getName()).concat("_"),
                            FileUtil.getExtension(uriIdentifierResponse.getName()), parent
                    );
                }

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(file);

                    byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
                    int len;
                    long total = 0;
                    long startTime = System.currentTimeMillis();
                    int bytesPerSecond = 0;
                    while (total != uriIdentifierResponse.getLength() && (len = dis.read(buffer, 0, (int) Math.min(buffer.length, uriIdentifierResponse.getLength() - total))) != -1) {
                        total += len;
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - startTime < 1000) {
                            currentTime = startTime + 1000;
                        }
                        bytesPerSecond = (int) ((total * 1000) / (currentTime - startTime));

                        fos.write(buffer, 0, len);
                    }

                    if (total != uriIdentifierResponse.getLength()) {
                        throw new IOException("read() ended prematurely");
                    }

                    log("Received [" + file.getPath() + "] at " + bytesPerSecond / 1024 + " KB/s: " + uriIdentifierResponse.getName());

                    uriIdentifierResponse.setPath(file.getPath());
                    mySqlAccess.addAttachment(uriIdentifierResponse, downloadRequests.get(uriIdentifierResponse.getSourceUri()));
                } catch (IOException e) {
                    log(e);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    throw e;
                } finally {
                    if (fos != null) {
                        fos.close();
                    }
                }
            } else {
                log("Client supplied an illegal uri response: " + uriIdentifierResponse);
            }
        }

        log("received " + uriResponsesCount + " unique attachments");
    }

    private void receiveHeaders() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        log("receiveHeaders");

        //noinspection unchecked
        receivedHeaders = (TreeSet<NetworkHeader>) StreamUtil.readStreamablesFrom(dis);

        log("received " + receivedHeaders.size() + " local headers");
    }

    private void sendHeaders() throws IOException {
        log("sendHeaders");

        // get all our headers (exclude blocked and include only for this croco id, if desired)
        TreeSet<NetworkHeader> headers = mySqlAccess.getHeadersForUpload();
        log("we have " + headers.size() + " local headers from this croco id");

        List<NetworkHeader> sentHeaders = mySqlAccess.getOpaqueHeaders(receivedHeaders, headers);
        StreamUtil.writeStreamablesTo(dos, sentHeaders);
        dos.flush();

        log("sent a request for " + sentHeaders.size() + " headers missing on our side");
    }

    private void receiveMessages() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        log("receiveMessages");

        //noinspection unchecked
        List<NetworkMessage> receivedMessages = (List<NetworkMessage>) StreamUtil.readStreamablesFrom(dis);
        processMessages(receivedMessages);

        log("received " + receivedMessages.size() + " local messages");
    }
}
