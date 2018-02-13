package com.croconaut;

import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.network.NetworkAttachmentPreview;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.Socket;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CptSyncThread extends LoggableThread {
    public static final String BROADCAST_ID = "ff:ff:ff:ff:ff:ff";
    public static final String   AUTHORS_ID = "66:bc:0c:51:4a:0a";

    private final Socket socket;
    protected final MySqlAccess mySqlAccess;
    // croco id, high priority
    protected final HashMap<String, Boolean> clientsToNotify = new HashMap<>();
    protected final HashSet<String> clientsNotRegistered = new HashSet<>();
    protected final HashSet<String> clientsUnknown = new HashSet<>();

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

        Pattern p = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
        Matcher m = p.matcher(crocoId);
        if (!m.find()) {
            throw new IOException("Received an illegal croco id:" + crocoId);
        }

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

                com.google.android.gcm.server.Message message
                        = new com.google.android.gcm.server.Message.Builder()
                        .collapseKey("sync")
                        .timeToLive(7 * 24 * 60 * 60)  // TODO: read from the DB a relativne ku creationTime
                        .priority(isHighPriority
                                ? com.google.android.gcm.server.Message.Priority.HIGH
                                : com.google.android.gcm.server.Message.Priority.NORMAL)
                        .delayWhileIdle(!isHighPriority)
                        .build();
                //log(message.toString());
                try {
                    Sender sender = new Sender(CptServer.SERVER_API_KEY);
                    Result result = sender.send(message, token, 5);
                    log("sender.send(" + clientCrocoId + ") result: " + result.toString());

                    if (result.getMessageId() == null) {
                        if (result.getErrorCodeName().equals("NotRegistered")) {
                            mySqlAccess.setToken(clientCrocoId, null);
                            clientsNotRegistered.add(clientCrocoId);
                        } // else TODO: reschedule
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
                clientsUnknown.add(clientCrocoId);
            }
        }

        mySqlAccess.cleanMessages();
    }

    protected void processMessages(List<NetworkMessage> messages, boolean alertNotRegistered) {
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

            List<NetworkHop> hops = networkMessage.getHops();
            if (hops.isEmpty()) {
                // non-tracking mode
                hops.add(new NetworkHop(networkMessage.header.getFrom(),
                        0, 0, now, "n/a", now, "WiFON user"));
                if (!networkMessage.header.getTo().equals(BROADCAST_ID)) {
                    hops.add(new NetworkHop(networkMessage.header.getTo(),
                            0, 0, now, "n/a", now, "WiFON user"));
                }
            }

            Map<Set<String>, Boolean> updatedCommunities = mySqlAccess.updateCommunities(hops);
            for (Map.Entry<Set<String>, Boolean> updatedCommunitiesEntry : updatedCommunities.entrySet()) {
                log("Updated community: " + updatedCommunitiesEntry.getKey() + " [" + updatedCommunitiesEntry.getValue() + "]");
                // has the community been created/updated/merged with another?
                if (updatedCommunitiesEntry.getValue()) {
                    includeMyself |= updatedCommunitiesEntry.getKey().contains(crocoId);
                    // notify all its members
                    for (String communityCrocoId : updatedCommunitiesEntry.getKey()) {
                        notify(communityCrocoId, false);    // low priority, if there's a message for 'crocoId', we'll find out later
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

                    if (to.equals(AUTHORS_ID)) {
                        log("creating emails...");
                        try {
                            String what;
                            String name;
                            String content;

                            ByteArrayInputStream bais = new ByteArrayInputStream(networkMessage.getAppPayload());
                            ObjectInputStream ois = new ObjectInputStream(bais);
                            Object obj = ois.readObject();
                            if (obj instanceof com.croconaut.ratemebuddy.data.pojo.Message) {
                                com.croconaut.ratemebuddy.data.pojo.Message appMessage
                                        = (com.croconaut.ratemebuddy.data.pojo.Message) obj;
                                what = "message";
                                name = appMessage.getProfileName();
                                content = appMessage.decoded().getContent();
                            } else if (obj instanceof com.croconaut.ratemebuddy.data.pojo.Comment) {
                                com.croconaut.ratemebuddy.data.pojo.Comment appComment
                                        = (com.croconaut.ratemebuddy.data.pojo.Comment) obj;
                                what = "comment";
                                name = appComment.getProfileName();
                                content = appComment.getComment();
                            } else if (obj instanceof com.croconaut.ratemebuddy.data.pojo.VoteUp) {
                                com.croconaut.ratemebuddy.data.pojo.VoteUp appLike
                                        = (com.croconaut.ratemebuddy.data.pojo.VoteUp) obj;
                                what = "like";
                                name = appLike.getProfileName();
                                content = "Status id: " + appLike.getStatusId();
                            } else {
                                log("Got object " + obj.toString());
                                continue;
                            }

                            String encodedName = URLEncoder.encode(name, "utf-8");
                            String encodedCrocoId = URLEncoder.encode(networkMessage.header.getFrom(), "utf-8");

                            String subject = "New WiFON " + what + " from " + name;
                            String body = "Dear WiFON author,\n"
                                    + "\n"
                                    + name + " has written to our authors Croco ID. Please add him as a friend via"
                                    + " http://wifon.sk/profiles/profile?name=" + encodedName + "&croco_id=" + encodedCrocoId
                                    + " and reply to this email if/when you reply " + name + " in WiFON.\n"
                                    + "\n"
                                    + "New " + what + ":\n"
                                    + "\n"
                                    + content;
                            log("sending emails...");
                            // send the email notification
                            sendFromGMail(CptServer.GMAIL_USERNAME, CptServer.GMAIL_PASSWORD,
                                    new String[]{"mikro@wifon.sk", "xi@wifon.sk", "spili@wifon.sk"},
                                    subject, body);
                            log("emails sent");
                        } catch (IOException e) {
                            log(e);
                        } catch (ClassNotFoundException e) {
                            log(e);
                        }
                    }

                    // we need to do this global check because we don't want to notify to's community if 'to' himself blocks 'from'
                    if (canNotify(to, from)) {
                        for (String communityCrocoId : mySqlAccess.getCommunity(to)) {  // TODO: cache
                            if (canNotify(communityCrocoId, from) && canNotify(communityCrocoId, to)) { // incl. 'to'
                                notify(communityCrocoId, communityCrocoId.equals(to));   // NORMAL => high priority if for us
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
                                notify(communityCrocoId, true/*false*/);   // ACK => low priority
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
                                    notify(communityCrocoId, true/*false*/); // BROADCAST for an acquaintance => low priority
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

        if (alertNotRegistered) {
            clientsToNotify.clear();
            ArrayList<NetworkMessage> alertMessages = new ArrayList<>();

            try {
                for (NetworkMessage networkMessage : messages) {
                    final String from = networkMessage.header.getFrom();
                    final String to = networkMessage.header.getTo();

                    if (!to.equals(BROADCAST_ID) && networkMessage.header.getType() == NetworkHeader.Type.NORMAL) {
                        String name = mySqlAccess.getName(to);
                        if (clientsNotRegistered.contains(to)) {
                            alertMessages.add(createNetworkMessage(to, from, name,
                                    "It seems that user '" + name + "' has uninstalled WiFON. :-("
                            ));
                        } else if (clientsUnknown.contains(to)) {
                            alertMessages.add(createNetworkMessage(to, from, name,
                                    "The WiFON server hasn't recognised user '" + name + "'. This may be " +
                                            "either because the WiFON app hasn't connected to the server yet " +
                                            "or because the user has WiFON no longer installed."
                            ));
                        }
                    }
                }
            } catch (IOException e) {
                log(e);
            }

            processMessages(alertMessages, false);
        }
    }

    protected boolean canNotify(String whom, String by) {
        if (!blockedCrocoIds.containsKey(whom)) {
            blockedCrocoIds.put(whom, mySqlAccess.getBlockedCrocoIds(whom));
        }
        return !blockedCrocoIds.get(whom).contains(by);
    }

    protected void notify(String who, boolean highPriority) {
        if (!clientsToNotify.containsKey(who) || highPriority) {
            clientsToNotify.put(who, highPriority);
        }
    }

    protected NetworkMessage createNetworkMessage(String from, String to, String name, String text) throws IOException {
        ArrayList<NetworkAttachmentPreview> networkAttachmentPreviews = new ArrayList<>();

        com.croconaut.ratemebuddy.data.pojo.Message textMessage = new com.croconaut.ratemebuddy.data.pojo.Message(
                text, null, name).encoded();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(textMessage);
        oos.close();
        byte[] appPayload = baos.toByteArray();

        NetworkHeader networkHeader = new NetworkHeader(
                -1,
                new MessageIdentifier(
                        "com.croconaut.ratemebuddy",
                        from,
                        to,
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

        // just to be sure we have different creation time next time we call this function
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return networkMessage;
    }

    private void sendFromGMail(String from, String pass, String[] to, String subject, String body) {
        Properties props = System.getProperties();
        String host = "smtp.gmail.com";
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.user", from);
        props.put("mail.smtp.password", pass);
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");

        Session session = Session.getDefaultInstance(props);
        MimeMessage message = new MimeMessage(session);

        try {
            message.setFrom(new InternetAddress(from));
            InternetAddress[] toAddress = new InternetAddress[to.length];

            // To get the array of addresses
            for( int i = 0; i < to.length; i++ ) {
                toAddress[i] = new InternetAddress(to[i]);
            }

            for( int i = 0; i < toAddress.length; i++) {
                message.addRecipient(Message.RecipientType.TO, toAddress[i]);
            }

            message.setSubject(subject);
            message.setText(body);
            Transport transport = session.getTransport("smtps");    // "smtp" doesn't work
            transport.connect(host, from, pass);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
        }
        catch (AddressException ae) {
            ae.printStackTrace();
        }
        catch (MessagingException me) {
            me.printStackTrace();
        }
    }
}
