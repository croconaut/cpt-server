package com.croconaut;

import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkMessage;

import java.io.IOException;
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

            receiveDeviceInfo();

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

        if (oldToken == null || !oldToken.equals(newToken)) {
            log("Token for " + crocoId + " is different, creating welcome messages etc");

            ArrayList<NetworkMessage> networkMessages = new ArrayList<>();

            networkMessages.add(createNetworkMessage(CptSyncThread.AUTHORS_ID, crocoId, "Miro Kropáček",
                    "Hi, Miro here.\n"
                            + "\n"
                            + "Me and my friends have created WiFON, just to see whether it's really possible.\n"
                            + "\n"
                            + "What you ask? Sending messages to each other without Internet connectivity! :-O\n"
                            + "\n"
                            + "This message is sent from our server, of course. But try to 'forget' your current Wi-Fi network and/or "
                            + "disable mobile data and try to write to a friend who has our app installed and is standing "
                            + "next to you. Miraculously he will receive it! (hopefully ;))"
                    )
            );

            networkMessages.add(createNetworkMessage(CptSyncThread.AUTHORS_ID, crocoId, "Miro Kropáček",
                    "Of course, it's totally OK to exchange messages (and images, videos, files, ...) via our server, too.\n"
                            + "\n"
                            + "If you want to know more, feel free to take a look at https://wifon.sk or just text me here, on WiFON!"
                    )
            );

            networkMessages.add(createNetworkMessage(CptSyncThread.AUTHORS_ID, crocoId, "Miro Kropáček",
                    "Oh, and WiFON is open source. Take a look at https://github.com/croconaut.\n"
                            + "\n"
                            + "If you are interested in technical details, go to https://github.com/croconaut/cpt/wiki"
                    )
            );

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

            processMessages(networkMessages, false);

            TreeSet<NetworkHeader> headersForDownload = mySqlAccess.getHeadersForDownload(crocoId, true);
            for (NetworkHeader header : headersForDownload) {
                boolean direct = header.getTo().equals(crocoId);
                notify(crocoId, direct);
                if (direct) {
                    log("Message directly for this croco id found");
                    break;
                }   // else continue, what if there's a header with to == crocoId
            }
        }

        mySqlAccess.setNameAndToken(crocoId, name, newToken);

        log("Token for " + crocoId + " is " + newToken);
        includeMyself = true;
        notifyClients();
    }
}
