package com.croconaut;

import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CptSyncThread extends LoggableThread {
    private static final String     API_KEY = "TODO: read the API key from a file";
    public static final String BROADCAST_ID = "ff:ff:ff:ff:ff:ff";

    private final Socket socket;
    protected final MySqlAccess mySqlAccess;
    // croco id, high priority
    protected final HashMap<String, Boolean> clientsToNotify = new HashMap<>();

    protected DataOutputStream dos;
    protected DataInputStream  dis;
    protected DataOutputStream nonBufferedDos;
    protected DataInputStream  nonBufferedDis;

    protected String crocoId;
    protected String name;
    protected boolean fullSync;
    protected boolean includeMyself;

    private final Map<String, Set<String>> blockedCrocoIds = new HashMap<>();
    private Set<String> syncedFollowers = new HashSet<>();  // have we sent a notification request to the people who befriended 'item' ?
    private Set<String> syncedAcquaintances = new HashSet<>();  // have we sent a notification request to the acquaintances of 'item' ?


    protected CptSyncThread(Socket socket) throws SQLException {
        this.socket = socket;
        // should be one per thread
        this.mySqlAccess = new MySqlAccess();
    }

    @Override
    public abstract void run();

    public String getPath() {
        return crocoId;
    }

    protected void initializeDataStreams() throws IOException {
        // NOTE: use data streams where possible (instead of object streams which write/read a few bytes by their creation!)
        dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), socket.getSendBufferSize()));
        dis = new DataInputStream( new BufferedInputStream( socket.getInputStream(),  socket.getReceiveBufferSize()));

        nonBufferedDos = new DataOutputStream(socket.getOutputStream());
        nonBufferedDis = new DataInputStream( socket.getInputStream());
    }

    protected void shutdown() throws IOException {
        // semi-synchronization
        socket.shutdownOutput();

        int read = dis.read();
        if (read != -1) {
            throw new StreamCorruptedException("Expected to read -1");
        }
    }

    protected void close() {
        try {
            socket.close();
        } catch (IOException e) {
            log(e);
        }
    }

    protected void receiveCrocoIdAndUsername() throws IOException {
        crocoId = dis.readUTF();
        if (dis.readBoolean()) {
            name = dis.readUTF();
            mySqlAccess.setName(crocoId, name);
        }
        log("receiveCrocoIdAndUsername: " + crocoId + " (" + name + ")");
    }

    protected void receiveSyncPreference() throws IOException {
        fullSync = dis.readBoolean();
    }

    protected void receiveBlockedCrocoIds() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(dis);
        //noinspection unchecked
        Set<String> blockedCrocoIds = (Set<String>) ois.readObject();
        mySqlAccess.setBlockedCrocoIds(crocoId, blockedCrocoIds);
    }

    /*
     * This function has following callers:
     * - markAsDelivered() [false]
     * - processMessages() [true/false]
     * - receiveFriends() [true]
     * - receiveToken() [true]
     *
     * Notification means that the client will send "download messages and attachments delivery").
     */
    protected void notifyClients() {
        log("notifyClients: " + clientsToNotify.size() + ", myself: " + includeMyself);
        if (!clientsToNotify.isEmpty()) {
            log(clientsToNotify.toString());
        }

        for (String clientCrocoId : clientsToNotify.keySet()) {
            if (clientCrocoId.equals(crocoId) && !includeMyself) {
                // don't notify currently connected client
                continue;
            }

            String token = mySqlAccess.getToken(clientCrocoId);
            if (token != null) {
                boolean isHighPriority = clientsToNotify.get(clientCrocoId);

                Message message = new Message.Builder()
                        .collapseKey("sync")
                        .timeToLive(7 * 24 * 60 * 60)  // TODO: read from the DB a relativne ku creationTime
                        .priority(isHighPriority ? Message.Priority.HIGH : Message.Priority.NORMAL)
                        .delayWhileIdle(!isHighPriority)
                        .build();
                //log(message.toString());
                try {
                    Sender sender = new Sender(API_KEY);
                    Result result = sender.send(message, token, 5);
                    log("sender.send() result: " + result.toString());

                    if (result.getMessageId() == null) {
                        // TODO: reschedule
                        log("send.send() error: " + result.getErrorCodeName());
                    }

                    if (result.getCanonicalRegistrationId() != null) {
                        log("New token for " + clientCrocoId + ": " + result.getCanonicalRegistrationId());
                        mySqlAccess.setToken(clientCrocoId, result.getCanonicalRegistrationId());
                    }
                } catch (IOException e) {
                    // TODO: reschedule
                    log(e);
                }
            } else {
                log("Croco ID " + clientCrocoId + " hasn't got a token");
            }
        }

        mySqlAccess.cleanMessages();
    }

    protected void processMessages(List<NetworkMessage> messages) {
        log("processMessages");

        final Date now = Calendar.getInstance().getTime();
        final NetworkHop networkHop = new NetworkHop(
                null, 51.507222, -0.1275, now, "Debian 5.0 64bit", now, "WiFON server"
        );

        // TODO: remove, only for debug purposes...
        Set<String> crocoIdCommunity = mySqlAccess.getCommunity(crocoId);
        log("Community before processing messages: " + crocoIdCommunity.toString());

        // first parse all the hops to create the final community and acquaintances set
        for (NetworkMessage networkMessage : messages) {
            log(networkMessage.toString());

            Map<Set<String>, Boolean> updatedCommunities = mySqlAccess.updateCommunities(networkMessage.getHops());
            for (Map.Entry<Set<String>, Boolean> updatedCommunitiesEntry : updatedCommunities.entrySet()) {
                log("Updated community: " + updatedCommunitiesEntry.getKey() + " [" + updatedCommunitiesEntry.getValue() + "]");
                // has the community been created/updated/merged with another?
                if (updatedCommunitiesEntry.getValue()) {
                    includeMyself |= updatedCommunitiesEntry.getKey().contains(crocoId);
                    // notify all its members
                    for (String communityCrocoId : updatedCommunitiesEntry.getKey()) {
                        if (!clientsToNotify.containsKey(communityCrocoId)) {
                            clientsToNotify.put(communityCrocoId, false);   // low priority, if there's a message for 'crocoId', we'll find out later
                        }
                    }
                }

                if (!networkMessage.header.getTo().equals(BROADCAST_ID)
                        && networkMessage.header.getType() == NetworkHeader.Type.NORMAL) {
                    // if one device writes to another, let's make them buddies ;-) (until one or another is blocked)
                    mySqlAccess.addAcquaintance(networkMessage.header.getFrom(), networkMessage.header.getTo());
                    mySqlAccess.addAcquaintance(networkMessage.header.getTo(), networkMessage.header.getFrom());
                }
            }
        }

        // do it now, after all the hops have been processed
        crocoIdCommunity = mySqlAccess.getCommunity(crocoId);
        log("Community after processing messages: " + crocoIdCommunity.toString());

        for (NetworkMessage networkMessage : messages) {
            final String from = networkMessage.header.getFrom();
            final String   to = networkMessage.header.getTo();

            if (!to.equals(BROADCAST_ID)) {
                if (networkMessage.header.getType() == NetworkHeader.Type.NORMAL) {
                    networkMessage.addHop(networkHop);

                    // NORMAL, assume we don't have it yet (it's possible some other thread has inserted it now)
                    mySqlAccess.insertMessage(networkMessage);

                    // we need to do this global check because we don't want to notify to's community if 'to' himself blocks 'from'
                    if (canNotify(to, from)) {
                        for (String communityCrocoId : mySqlAccess.getCommunity(to)) {  // TODO: cache
                            if (canNotify(communityCrocoId, from) && canNotify(communityCrocoId, to)) { // incl. 'to'
                                notify(communityCrocoId, communityCrocoId.equals(crocoId));   // NORMAL => high priority if for us
                            }
                        }
                    }
                } else {
                    // ACK, assume we have a NORMAL
                    if (!mySqlAccess.updateMessageToAck(networkMessage.header.getIdentifier(), networkMessage.getDeliveredTime(), networkMessage.getHops())) {
                        // not found, insert then
                        mySqlAccess.insertMessage(networkMessage);
                    }
                    // we don't have to check isExpectingAck -- such messages shouldn't arrive at all!

                    if (canNotify(from, to)) {
                        for (String communityCrocoId : mySqlAccess.getCommunity(from)) {    // TODO: cache
                            if (canNotify(communityCrocoId, from) && canNotify(communityCrocoId, to)) {
                                notify(communityCrocoId, false);   // ACK => low priority
                            }
                        }
                    }
                }
            } else {
                networkMessage.addHop(networkHop);

                // NORMAL, assume we don't have it yet (it's possible some other thread has inserted it now)
                mySqlAccess.insertMessage(networkMessage);

                if (!syncedFollowers.contains(from)) {  // have the followers of 'from' been notified?
                    Set<String> followers = mySqlAccess.getPeopleWhoBefriendedThisCrocoId(from);
                    for (String follower : followers) {
                        // perhaps not needed, 'follower' can't be blocked by 'from'
                        if (canNotify(follower, from)) {
                            Set<String> followerCommunity = mySqlAccess.getCommunity(follower); // incl. the follower himself
                            for (String communityCrocoId : followerCommunity) {
                                // don't offer the courtesy of delivering broadcast if either 'from' or 'follower' are blocked by 'communityCrocoId'
                                if (canNotify(communityCrocoId, from) && canNotify(communityCrocoId, follower)) {
                                    notify(communityCrocoId, communityCrocoId.equals(follower)); // high priority if direct BROADCAST
                                }
                            }
                        }
                    }
                    syncedFollowers.add(from);
                }

                if (!syncedAcquaintances.contains(from)) {  // have the acquaintances of 'from' been notified?
                    Set<String> acquaintances = mySqlAccess.getAcquaintances(from);
                    for (String acquaintance : acquaintances) {
                        // notify the community only if 'acquaintance' himself doesn't block 'from'
                        if (canNotify(acquaintance, from)) {
                            Set<String> acquaintanceCommunity = mySqlAccess.getCommunity(acquaintance); // incl. the acquaintance himself
                            for (String communityCrocoId : acquaintanceCommunity) {
                                // don't offer the courtesy of delivering broadcast if either 'from' or 'acquaintance' are blocked by 'communityCrocoId'
                                if (canNotify(communityCrocoId, from) && canNotify(communityCrocoId, acquaintance)) {
                                    notify(communityCrocoId, false); // BROADCAST for an acquaintance => low priority
                                }
                            }
                        }
                    }
                    syncedAcquaintances.add(from);
                }

                // more or less an equivalent to P2P broadcast but faster -- if 2 of 3 are online,
                // this minimizes the number of p2p connections
                for (String communityCrocoId : mySqlAccess.getCommunity(from)) {    // TODO: cache
                    if (canNotify(communityCrocoId, from)) {
                        notify(communityCrocoId, false);   // low priority...
                    }
                }
            }
        }

        notifyClients();
    }

    private boolean canNotify(String whom, String by) {
        if (!blockedCrocoIds.containsKey(whom)) {
            blockedCrocoIds.put(whom, mySqlAccess.getBlockedCrocoIds(whom));
        }
        return !blockedCrocoIds.get(whom).contains(by);
    }

    private void notify(String who, boolean highPriority) {
        if (!clientsToNotify.containsKey(who) || highPriority) {
            clientsToNotify.put(who, highPriority);
        }
    }
}