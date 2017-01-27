package com.croconaut;

import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.network.NetworkAttachmentPreview;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.croconaut.ratemebuddy.data.pojo.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
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

        String oldToken = mySqlAccess.getToken(crocoId);
        String newToken = dis.readUTF();

        if (oldToken == null) {
            log("Token for " + crocoId + " was null, creating welcome messages");

            ArrayList<NetworkMessage> networkMessages = new ArrayList<>();
            ArrayList<NetworkAttachmentPreview> networkAttachmentPreviews = new ArrayList<>();

            Message textMessage = new Message(
                    "Hi, Miro here.\n"
                    + "\n"
                    + "Me and my friends have created WiFON, just to see whether it's really possible.\n"
                    + "\n"
                    + "What you ask? Sending messages to each other without Internet connectivity! :-O\n"
                    + "\n"
                    + "This message is sent from our server, of course. But try to 'forget' your current Wi-Fi network and/or "
                        + "disable mobile data and try to write to a friend who has our app installed and is standing "
                        + "next to you. Miraculously he will receive it! (hopefully ;))",
                    null, "Miro Kropáček");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(textMessage);
            oos.close();
            byte[] appPayload = baos.toByteArray();

            NetworkHeader networkHeader = new NetworkHeader(
                    -1,
                    new MessageIdentifier(
                            "com.croconaut.ratemebuddy",
                            "66:bc:0c:51:4a:0a",
                            crocoId,
                            System.currentTimeMillis()
                    ),
                    NetworkHeader.Type.NORMAL,
                    -1
            );
            NetworkMessage networkMessage = new NetworkMessage(
                    networkHeader,
                    604800000,
                    appPayload,
                    new ArrayList<NetworkHop>(),  // server's hop will be added in processMessages()
                    false, false, false, false, true,
                    false   // is local
            );
            networkMessage.setAttachments(networkAttachmentPreviews);
            networkMessages.add(networkMessage);

            // just to be sure we have different creation time
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            textMessage = new Message(
                    "Of course, it's totally OK to exchange messages (and images, videos, files, ...) via our server, too.\n"
                    + "\n"
                    + "If you want to know more, feel free to take a look at https://wifon.sk or just text me here, on WiFON!",
                    null, "Miro Kropáček");
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(textMessage);
            oos.close();
            appPayload = baos.toByteArray();

            networkHeader = new NetworkHeader(
                    -1,
                    new MessageIdentifier(
                            "com.croconaut.ratemebuddy",
                            "66:bc:0c:51:4a:0a",
                            crocoId,
                            System.currentTimeMillis()
                    ),
                    NetworkHeader.Type.NORMAL,
                    -1
            );
            networkMessage = new NetworkMessage(
                    networkHeader,
                    604800000,
                    appPayload,
                    new ArrayList<NetworkHop>(),  // server's hop will be added in processMessages()
                    false, false, false, false, true,
                    false   // is local
            );
            networkMessage.setAttachments(networkAttachmentPreviews);
            networkMessages.add(networkMessage);

            // just to be sure we have different creation time
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            textMessage = new Message(
                    "Oh, and WiFON is open source. Take a look at https://github.com/croconaut.\n"
                            + "\n"
                            + "If you are interested in technical details, go to https://github.com/croconaut/cpt/wiki",
                    null, "Miro Kropáček");
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(textMessage);
            oos.close();
            appPayload = baos.toByteArray();

            networkHeader = new NetworkHeader(
                    -1,
                    new MessageIdentifier(
                            "com.croconaut.ratemebuddy",
                            "66:bc:0c:51:4a:0a",
                            crocoId,
                            System.currentTimeMillis()
                    ),
                    NetworkHeader.Type.NORMAL,
                    -1
            );
            networkMessage = new NetworkMessage(
                    networkHeader,
                    604800000,
                    appPayload,
                    new ArrayList<NetworkHop>(),  // server's hop will be added in processMessages()
                    false, false, false, false, true,
                    false   // is local
            );
            networkMessage.setAttachments(networkAttachmentPreviews);
            networkMessages.add(networkMessage);

            /*
             * Disable the attachment stuff for now, the client would receive just a preview anyway
            // just to be sure we have different creation time
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Attachment attachment = new Attachment();
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(attachment);
            oos.close();
            appPayload = baos.toByteArray();
            */

            processMessages(networkMessages);
        }


        if (oldToken == null || !oldToken.equals(newToken)) {
            log("Token for " + crocoId + " is different, notifying the device");

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

        mySqlAccess.setNameAndToken(crocoId, name, newToken);

        log("Token for " + crocoId + " is " + newToken);
        includeMyself = true;
        notifyClients();
    }
}
