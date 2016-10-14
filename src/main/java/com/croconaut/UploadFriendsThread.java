package com.croconaut;

import com.croconaut.cpt.network.NetworkHeader;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class UploadFriendsThread extends CptSyncThread {
    protected UploadFriendsThread(Socket socket) throws SQLException {
        super(socket);
    }

    @Override
    public void run() {
        log("run");

        try {
            initializeDataStreams();

            receiveCrocoIdAndUsername();

            receiveBlockedCrocoIds();

            receiveFriends();

            shutdown();

            // now we're sure both parties have the same content
        } catch (IOException | ClassNotFoundException e) {
            log(e);
        } finally {
            close();
        }
    }

    private void receiveFriends() throws IOException, ClassNotFoundException {
        log("receiveFriends");

        ObjectInputStream ois = new ObjectInputStream(dis);
        //noinspection unchecked
        Set<String> newFriends = (Set<String>) ois.readObject();
        Set<String> oldFriends = mySqlAccess.getFriends(crocoId);

        if (!newFriends.equals(oldFriends)) {
            mySqlAccess.setFriends(crocoId, new HashSet<>(newFriends)); // deep copy... TODO remove when changed to mysql

            newFriends.removeAll(oldFriends);
            if (!newFriends.isEmpty()) {
                log("Added " + newFriends.size() + " new friends of " + crocoId);

                TreeSet<NetworkHeader> broadcastHeaders = mySqlAccess.getBroadcastHeaders(crocoId);
                for (NetworkHeader header : broadcastHeaders) {
                    if (newFriends.contains(header.getFrom())) {
                        // if there's a broadcast from one of the new friends, notify the client about it
                        log("There's at least one broadcast from this croco id's new friend(s)");
                        clientsToNotify.put(crocoId, false);    // it's a broadcast => low priority
                        break;
                    }
                }
                includeMyself = true;
                notifyClients();
            }
        }
    }
}
