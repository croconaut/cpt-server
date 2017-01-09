package com.croconaut;

import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.croconaut.cpt.network.UriIdentifierResponse;
import com.mysql.jdbc.Connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class MySqlAccess {
    private final Connection mySqlConnection;

    public MySqlAccess() throws SQLException {
        mySqlConnection = (Connection) DriverManager.getConnection(
                String.format("jdbc:mysql://localhost/%1$s?user=%2$s&password=%3$s", CptServer.JDBC_DB_NAME, CptServer.JDBC_USERNAME, CptServer.JDBC_PASSWORD)
        );
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                 TABLE_TOKENS = "tokens";
    private static final String TABLE_TOKENS_COLUMN_CROCO_ID = "croco_id";
    private static final String     TABLE_TOKENS_COLUMN_NAME = "name";
    private static final String    TABLE_TOKENS_COLUMN_TOKEN = "token";

    public String getToken(String crocoId) {
        String token = null;

        StringBuilder statement = new StringBuilder("SELECT ").append(TABLE_TOKENS_COLUMN_TOKEN)
                .append(" FROM ").append(TABLE_TOKENS)
                .append(" WHERE ").append(TABLE_TOKENS_COLUMN_CROCO_ID).append(" = ").append("'" + crocoId + "'")
                .append(";");

        try {
            ResultSet rs = mySqlConnection.createStatement().executeQuery(statement.toString());
            if (rs.next()) {
                token = rs.getString(TABLE_TOKENS_COLUMN_TOKEN);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return token;
    }

    public void setName(String crocoId, String name) {
        insertOrUpdate(TABLE_TOKENS, TABLE_TOKENS_COLUMN_CROCO_ID, TABLE_TOKENS_COLUMN_NAME, crocoId, name);
    }

    public void setToken(String crocoId, String token) {
        insertOrUpdate(TABLE_TOKENS, TABLE_TOKENS_COLUMN_CROCO_ID, TABLE_TOKENS_COLUMN_TOKEN, crocoId, token);
    }

    public void setNameAndToken(String crocoId, String name, String token) {
        if (name == null) {
            // unlikely, just to be sure
            setToken(crocoId, token);
        }

        StringBuilder statement = new StringBuilder("INSERT INTO ").append(TABLE_TOKENS)
                .append(" (")
                    .append(TABLE_TOKENS_COLUMN_CROCO_ID)
                    .append(", ").append(TABLE_TOKENS_COLUMN_NAME)
                    .append(", ").append(TABLE_TOKENS_COLUMN_TOKEN)
                .append(") VALUES (")
                    .append("'" + crocoId + "'")
                    .append(", ").append("'" + name + "'")
                    .append(", ").append("'" + token + "'")
                .append(") ON DUPLICATE KEY UPDATE ")
                    .append(TABLE_TOKENS_COLUMN_NAME).append(" = ").append("'" + name + "'")
                    .append(", ").append(TABLE_TOKENS_COLUMN_TOKEN).append(" = ").append("'" + token + "'")
                .append(";");

        try {
            //System.out.println(statement.toString());
            mySqlConnection.createStatement().execute(statement.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                   TABLE_BLOCKED = "blocked";
    private static final String   TABLE_BLOCKED_COLUMN_CROCO_ID = "croco_id";
    private static final String TABLE_BLOCKED_COLUMN_BLOCKED_ID = "blocked_croco_id";

    public Set<String> getBlockedCrocoIds(String crocoId) {
        return getOneToMany(TABLE_BLOCKED, TABLE_BLOCKED_COLUMN_CROCO_ID, TABLE_BLOCKED_COLUMN_BLOCKED_ID, crocoId);
    }

    public Set<String> getPeopleWhoBlockedThisCrocoId(String crocoId) {
        return getOneToMany(TABLE_BLOCKED, TABLE_BLOCKED_COLUMN_BLOCKED_ID, TABLE_BLOCKED_COLUMN_CROCO_ID, crocoId);
    }

    public void setBlockedCrocoIds(String crocoId, Set<String> blockedCrocoIds) {
        setOneToMany(TABLE_BLOCKED, TABLE_BLOCKED_COLUMN_CROCO_ID, TABLE_BLOCKED_COLUMN_BLOCKED_ID, crocoId, blockedCrocoIds);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                  TABLE_FRIENDS = "friends";
    private static final String  TABLE_FRIENDS_COLUMN_CROCO_ID = "croco_id";
    private static final String TABLE_FRIENDS_COLUMN_FRIEND_ID = "friend_croco_id";

    public Set<String> getFriends(String crocoId) {
        return getOneToMany(TABLE_FRIENDS, TABLE_FRIENDS_COLUMN_CROCO_ID, TABLE_FRIENDS_COLUMN_FRIEND_ID, crocoId);
    }

    public Set<String> getPeopleWhoBefriendedThisCrocoId(String crocoId) {
        return getOneToMany(TABLE_FRIENDS, TABLE_FRIENDS_COLUMN_FRIEND_ID, TABLE_FRIENDS_COLUMN_CROCO_ID, crocoId);
    }

    public void setFriends(String crocoId, Set<String> friends) {
        setOneToMany(TABLE_FRIENDS, TABLE_FRIENDS_COLUMN_CROCO_ID, TABLE_FRIENDS_COLUMN_FRIEND_ID, crocoId, friends);
    }

    public void addFriend(String crocoId, String friend) {
        insertOrIgnore(TABLE_FRIENDS, TABLE_FRIENDS_COLUMN_CROCO_ID, TABLE_FRIENDS_COLUMN_FRIEND_ID, crocoId, friend);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                        TABLE_ACQUAINTANCES = "acquaintances";
    private static final String        TABLE_ACQUAINTANCES_COLUMN_CROCO_ID = "croco_id";
    private static final String TABLE_ACQUAINTANCES_COLUMN_ACQUAINTANCE_ID = "acquaintance_croco_id";

    public Set<String> getAcquaintances(String crocoId) {
        return getOneToMany(TABLE_ACQUAINTANCES, TABLE_ACQUAINTANCES_COLUMN_CROCO_ID, TABLE_ACQUAINTANCES_COLUMN_ACQUAINTANCE_ID, crocoId);
    }

    public void addAcquaintance(String crocoId, String acquaintance) {
        insertOrIgnore(TABLE_ACQUAINTANCES, TABLE_ACQUAINTANCES_COLUMN_CROCO_ID, TABLE_ACQUAINTANCES_COLUMN_ACQUAINTANCE_ID, crocoId, acquaintance);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static Set<Set<String>> mCommunities = Collections.newSetFromMap(new IdentityHashMap<Set<String>, Boolean>());
    private static final String mCommunitiesFilename = "communities.obj";

    public Set<String> getCommunity(String crocoId) {
        synchronized (mCommunities) {
            Set<String> communityForCrocoId = new HashSet<>();
            for (Set<String> community : mCommunities) {
                if (community.contains(crocoId)) {
                    communityForCrocoId.addAll(community);
                    break;
                }
            }
            return communityForCrocoId;
        }
    }

    // return unchanged or updated or merged community containing all the hops
    private void updateCommunitiesPrivate(List<String> hopCrocoIds, Map<Set<String>, Boolean> updatedCommunities) {
        synchronized (mCommunities) {
            // find out whether we can't merge some communities
            Set<Set<String>> foundCommunities = Collections.newSetFromMap(new IdentityHashMap<Set<String>, Boolean>());
            for (Set<String> community : mCommunities) {
                for (Iterator<String> crocoIdIterator = hopCrocoIds.iterator(); crocoIdIterator.hasNext(); ) {
                    String crocoId = crocoIdIterator.next();
                    if (community.contains(crocoId)) {
                        foundCommunities.add(community);
                        // there's no way other communities contain this croco id
                        crocoIdIterator.remove();
                        // although the community has been added, let it loop to remove other croco ids if possible
                    }
                }
            }

            Set<String> newCommunity = new HashSet<>();
            for (Set<String> community : foundCommunities) {
                // merge
                newCommunity.addAll(community);
                // remove old
                // this is the place where we take advantage of the identity set (comparing references)
                // although it wouldn't hurt to use the classic set either -- communities mustn't be same
                mCommunities.remove(community);
            }
            // if there are any hops left...
            boolean isUpdated = newCommunity.addAll(hopCrocoIds) || foundCommunities.size() > 1;

            if (!updatedCommunities.containsKey(newCommunity) || isUpdated) {
                updatedCommunities.put(newCommunity, isUpdated);
            }

            // finally add it to the global set
            mCommunities.add(newCommunity);
        }
    }

    // return all the communities which are relevant to the hops (updated or not)
    // (realistically, just one or two, we shouldn't have more than one 'null' in the way
    // except some obscure clear all/reset server scenarios)
    public Map<Set<String>, Boolean> updateCommunities(List<NetworkHop> hops) {
        // use anything but IdentityMap as the top-level container, we must not compare references!
        // (useful for the case when hops are divided by the server and still a single community)
        Map<Set<String>, Boolean> updatedCommunities = new HashMap<>();

        ArrayList<String> hopCrocoIds = new ArrayList<>();
        for (NetworkHop hop : hops) {
            if (hop.crocoId == null) {
                // if there's the server in our way, split the hops
                // (typical example is ACK -- it goes from the sender via the server to the recipient
                // and yes, we want this precious information about the community behind the server...)
                updateCommunitiesPrivate(hopCrocoIds, updatedCommunities);
                hopCrocoIds.clear();
            } else {
                hopCrocoIds.add(hop.crocoId);
            }
        }

        if (!hopCrocoIds.isEmpty()) {
            updateCommunitiesPrivate(hopCrocoIds, updatedCommunities);
            hopCrocoIds.clear();
        }

        return updatedCommunities;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static Map<MessageAttachmentIdentifier, UriIdentifierResponse> mUploadedAttachments = new HashMap<>();
    private static Set<MessageAttachmentIdentifier> mDeliveredAttachments = new HashSet<>();
    private static final String mUploadedAttachmentsFilename = "uploaded_attachments.obj";
    private static final String mDeliveredAttachmentsFilename = "delivered_attachments.obj";

    // get a list of delivered attachments from the app server to the recipient
    // (sent by 'crocoId')
    public List<MessageAttachmentIdentifier> getDeliveredAttachments(String crocoId) {
        synchronized (mUploadedAttachments) {
            List<MessageAttachmentIdentifier> deliveredAttachments = new ArrayList<>();

            for (MessageAttachmentIdentifier messageAttachmentIdentifier : mUploadedAttachments.keySet()) {
                if (messageAttachmentIdentifier.getFrom().equals(crocoId)) {
                    if (!getBlockedCrocoIds(crocoId).contains(messageAttachmentIdentifier.getTo())
                            && isAttachmentDelivered(messageAttachmentIdentifier)) {
                        deliveredAttachments.add(messageAttachmentIdentifier);
                    }
                }
            }

            return deliveredAttachments;
        }
    }

    private boolean isAttachmentDelivered(MessageAttachmentIdentifier messageAttachmentIdentifier) {
        synchronized (mDeliveredAttachments) {
            return mDeliveredAttachments.contains(messageAttachmentIdentifier);
        }
    }

    public void markAttachmentAsDelivered(MessageAttachmentIdentifier messageAttachmentIdentifier) {
        synchronized (mDeliveredAttachments) {
            // TODO: delete the file
            mDeliveredAttachments.add(messageAttachmentIdentifier);
        }
    }

    public void addAttachment(UriIdentifierResponse uriIdentifierResponse, List<MessageAttachmentIdentifier> messageAttachmentIdentifiers) {
        synchronized (mUploadedAttachments) {
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                if (!mUploadedAttachments.containsKey(messageAttachmentIdentifier)) {
                    mUploadedAttachments.put(messageAttachmentIdentifier, uriIdentifierResponse);
                } else {
                    System.err.println("MessageAttachmentIdentifier is already uploaded: " + messageAttachmentIdentifier);
                }
            }
        }
    }

    public Map<UriIdentifierResponse, List<MessageAttachmentIdentifier>> getAttachments(Set<MessageAttachmentIdentifier> requestedMessageAttachmentIdentifiers) {
        synchronized (mUploadedAttachments) {
            Map <UriIdentifierResponse, List<MessageAttachmentIdentifier>> map = new HashMap<>();

            for (MessageAttachmentIdentifier messageAttachmentIdentifier : requestedMessageAttachmentIdentifiers) {
                if (mUploadedAttachments.containsKey(messageAttachmentIdentifier)) {
                    UriIdentifierResponse uriIdentifierResponse = mUploadedAttachments.get(messageAttachmentIdentifier);

                    List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = map.get(uriIdentifierResponse);
                    if (messageAttachmentIdentifiers == null) {
                        messageAttachmentIdentifiers = new ArrayList<>();
                        map.put(uriIdentifierResponse, messageAttachmentIdentifiers);
                    }
                    messageAttachmentIdentifiers.add(messageAttachmentIdentifier);
                }
            }

            return map;
        }
    }

    public Map<String, List<MessageAttachmentIdentifier>> getOpaqueMessageAttachmentIdentifiers(Set<MessageAttachmentIdentifier> inputMessageAttachmentIdentifiers) {
        synchronized (mUploadedAttachments) {
            Set<MessageAttachmentIdentifier> identifiers = new HashSet<>(inputMessageAttachmentIdentifiers);
            identifiers.removeAll(mUploadedAttachments.keySet());

            Map<String, List<MessageAttachmentIdentifier>> map = new HashMap<>();

            // same sourceUri in 'identifiers' always refers to same file
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : identifiers) {
                List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = map.get(messageAttachmentIdentifier.getSourceUri());
                if (messageAttachmentIdentifiers == null) {
                    messageAttachmentIdentifiers = new ArrayList<>();
                    map.put(messageAttachmentIdentifier.getSourceUri(), messageAttachmentIdentifiers);
                }
                messageAttachmentIdentifiers.add(messageAttachmentIdentifier);
            }

            return map;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static Map<NetworkHeader, NetworkMessage> mMessages = new HashMap<>();
    private static final String mMessagesFilename = "messages.obj";

    // get headers for clients which wish upload something to to the server
    public TreeSet<NetworkHeader> getHeadersForUpload() {
        synchronized (mMessages) {
            TreeSet<NetworkHeader> list = new TreeSet<>();
            // basically all messages, why would we hide something here (so it could lead to unnecessary upload)?
            list.addAll(mMessages.keySet());

            return list;
        }
    }

    // get headers of local messages for 'clientCrocoId' and either non-local for 'clientCrocoId' or all of them
    public TreeSet<NetworkHeader> getHeadersForDownload(String clientCrocoId, boolean allHeaders) {
        synchronized (mMessages) {
            TreeSet<NetworkHeader> list = new TreeSet<>();

            Set<String> clientBlockers = getBlockedCrocoIds(clientCrocoId);
            Set<String> clientFriends = getFriends(clientCrocoId);
            Set<String> clientAcquaintances = getAcquaintances(clientCrocoId);
            Set<String> clientCommunity = getCommunity(clientCrocoId);    // TODO: 'allHeaders' should be used for downloading for community or not

            Map<String, Set<String>> communityBlockers = new HashMap<>();
            Map<String, Boolean> communityFollowers = new HashMap<>();
            Map<String, Boolean> communityAcquaintances = new HashMap<>();

            for (NetworkHeader header : mMessages.keySet()) {
                if (!header.getTo().equals(CptSyncThread.BROADCAST_ID)) {
                    if (!clientBlockers.contains(header.getFrom()) && !clientBlockers.contains(header.getTo())) {
                        if (header.getType() == NetworkHeader.Type.NORMAL
                                && (header.getTo().equals(clientCrocoId) || clientCommunity.contains(header.getTo()))) {
                            list.add(header);
                        } else if (header.getType() == NetworkHeader.Type.ACK
                                && (header.getFrom().equals(clientCrocoId) || clientCommunity.contains(header.getFrom()))) {
                            list.add(header);
                        }
                    }
                } else {
                    final String from = header.getFrom();
                    // if the client is blocking 'from', he is not going to be carrier for others
                    if (!clientBlockers.contains(from)) {
                        if (clientFriends.contains(from) || clientAcquaintances.contains(from) || clientCommunity.contains(from)) {
                            list.add(header);
                        } else {
                            // for later usage
                            if (!communityBlockers.containsKey(from)) {
                                // unknown, let's find out
                                Set<String> msgBlockers = getPeopleWhoBlockedThisCrocoId(from);
                                communityBlockers.put(from, msgBlockers);
                            }

                            if (!communityFollowers.containsKey(from)) {
                                // unknown, let's find out
                                Set<String> msgFollowers = getPeopleWhoBefriendedThisCrocoId(from);
                                msgFollowers.retainAll(clientCommunity);
                                msgFollowers.removeAll(clientBlockers);
                                msgFollowers.removeAll(communityBlockers.get(from));    // perhaps not needed
                                communityFollowers.put(from, !msgFollowers.isEmpty());    // whether this sender's follower is in the community
                            }

                            if (!communityAcquaintances.containsKey(from)) {
                                // unknown, let's find out
                                Set<String> msgAcquaintances = getAcquaintances(from);
                                msgAcquaintances.retainAll(clientCommunity);    // only from the community of 'croco id'
                                msgAcquaintances.removeAll(clientBlockers); // only the members 'croco id' doesn't block
                                msgAcquaintances.removeAll(communityBlockers.get(from));    // only the members which do not block 'from'
                                communityAcquaintances.put(from, !msgAcquaintances.isEmpty());    // whether this sender's acquaintance is in the community
                            }

                            if (communityFollowers.get(from) || communityAcquaintances.get(from)) {
                                // is it in the community? all right, let's add this message as well, hopefully making p2p useful here
                                list.add(header);
                            }
                        }
                    }
                }
            }

            return list;
        }
    }

    public TreeSet<NetworkHeader> getBroadcastHeaders(String clientCrocoId) {
        synchronized (mMessages) {
            // TODO: replace with a DB query

            TreeSet<NetworkHeader> list = new TreeSet<>();

            Set<String> blockedCrocoIds = getBlockedCrocoIds(clientCrocoId);
            for (NetworkHeader header : mMessages.keySet()) {
                if (!blockedCrocoIds.contains(header.getFrom())
                        && header.getTo().equals(CptSyncThread.BROADCAST_ID)) {
                    list.add(header);
                }
            }

            return list;
        }
    }

    // get all the headers the target side doesn't have but the source side does
    // (no need for a lock, it's not exactly a DB operation)
    public List<NetworkHeader> getOpaqueHeaders(Collection<NetworkHeader> sourceHeaders, TreeSet<NetworkHeader> targetHeaders) {
        List<NetworkHeader> headersMissingOnTarget = new ArrayList<>();

        for (NetworkHeader sourceHeader : sourceHeaders) {
            if (!targetHeaders.contains(sourceHeader)) {
                if (sourceHeader.isPersistent()) {
                    NetworkHeader higher = targetHeaders.higher(sourceHeader);
                    // don't care about creationTime (it must be lower than sourceHeader's if the other fields are equal) and type/to (these can be overwritten if needed)
                    if (higher == null || !sourceHeader.getAppId().equals(higher.getAppId()) || !sourceHeader.getFrom().equals(higher.getFrom()) || sourceHeader.getPersistentId() != higher.getPersistentId()) {
                        // source header is definitely the latest/only possible candidate, add it
                        headersMissingOnTarget.add(sourceHeader);
                    }
                } else if (!targetHeaders.contains(sourceHeader.flipped()) || sourceHeader.getType() == NetworkHeader.Type.ACK) {
                    // target side either really doesn't have it or it has NORMAL and source side has ACK
                    headersMissingOnTarget.add(sourceHeader);
                }
            }
        }

        return headersMissingOnTarget;
    }

    // return all the other messages than those specified by 'clientHeaders'
    public List<NetworkMessage> getMessages(List<NetworkHeader> headers) {
        synchronized (mMessages) {
            List<NetworkMessage> list = new ArrayList<>();

            for (NetworkHeader header : headers) {
                NetworkMessage networkMessage = mMessages.get(header);
                if (networkMessage != null) {
                    list.add(networkMessage);
                }
            }

            return list;
        }
    }

    public void cleanMessages() {
        // TODO: clean messages older than N days
    }

    public boolean updateMessageToAck(MessageIdentifier messageIdentifier, Date deliveredTime, List<NetworkHop> hops) {
        synchronized (mMessages) {
            NetworkHeader header = new NetworkHeader(-1, messageIdentifier, NetworkHeader.Type.NORMAL, -1);
            NetworkMessage networkMessage = mMessages.get(header);
            if (networkMessage != null) {
                mMessages.remove(header);
                header = new NetworkHeader(header.getRowId(), header.getIdentifier(), NetworkHeader.Type.ACK, -1);
                networkMessage = new NetworkMessage(
                        header,
                        networkMessage.getExpirationTime(),
                        null,   // payload
                        hops,
                        false,  // isSentToRecipient
                        false,  // isSentToOtherDevice
                        false,  // isSentToAppServer
                        false,  // isExpectingSent
                        networkMessage.isExpectingAck(),  // useless but still, sent over the network
                        networkMessage.isLocal()
                );
                networkMessage.setDeliveredTime(deliveredTime);
                mMessages.put(header, networkMessage);
                return true;
            }
            return false;
        }
    }

    public boolean insertMessage(NetworkMessage networkMessage) {
        synchronized (mMessages) {
            boolean alreadyExists;
            alreadyExists = mMessages.put(networkMessage.header, networkMessage) != null;   // "index"

            return !alreadyExists;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public static void loadNonPersistent() {
        System.out.println("loadNonPersistent");

        synchronized (mCommunitiesFilename) {
            if (new File(mCommunitiesFilename).exists()) {
                mCommunities = (Set<Set<String>>) deserializeObject(mCommunitiesFilename, mCommunities);
            } else {
                System.out.println("File " + mCommunitiesFilename + " not found, skipping deserialization");
            }
        }

        synchronized (mUploadedAttachmentsFilename) {
            if (new File(mUploadedAttachmentsFilename).exists()) {
                mUploadedAttachments = (Map<MessageAttachmentIdentifier, UriIdentifierResponse>) deserializeObject(mUploadedAttachmentsFilename, mUploadedAttachments);
            } else {
                System.out.println("File " + mUploadedAttachmentsFilename + " not found, skipping deserialization");
            }
        }

        synchronized (mDeliveredAttachmentsFilename) {
            if (new File(mDeliveredAttachmentsFilename).exists()) {
                mDeliveredAttachments = (Set<MessageAttachmentIdentifier>) deserializeObject(mDeliveredAttachmentsFilename, mDeliveredAttachments);
            } else {
                System.out.println("File " + mDeliveredAttachmentsFilename + " not found, skipping deserialization");
            }
        }

        synchronized (mMessagesFilename) {
            if (new File(mMessagesFilename).exists()) {
                mMessages = (Map<NetworkHeader, NetworkMessage>) deserializeObject(mMessagesFilename, mMessages);
            } else {
                System.out.println("File " + mMessagesFilename + " not found, skipping deserialization");
            }

            String statusPath = CptSyncThread.AUTHORS_ID + "/com.croconaut.ratemebuddy/status.obj";
            File statusFile = new File(statusPath);
            if (statusFile.exists()) {
                NetworkMessage message = (NetworkMessage) deserializeObject(statusPath, null);
                if (message != null) {
                    mMessages.put(message.header, message);
                }
            }

            String profilePath = CptSyncThread.AUTHORS_ID + "/com.croconaut.ratemebuddy/profile.obj";
            File profileFile = new File(profilePath);
            if (profileFile.exists()) {
                NetworkMessage message = (NetworkMessage) deserializeObject(profilePath, null);
                if (message != null) {
                    mMessages.put(message.header, message);
                }
            }
        }
    }

    public static void saveNonPersistent() {
        System.out.println("saveNonPersistent");

        synchronized (mCommunitiesFilename) {
            serializeObject(mCommunitiesFilename, mCommunities);
        }
        synchronized (mUploadedAttachmentsFilename) {
            serializeObject(mUploadedAttachmentsFilename, mUploadedAttachments);
        }
        synchronized (mDeliveredAttachmentsFilename) {
            serializeObject(mDeliveredAttachmentsFilename, mDeliveredAttachments);
        }
        synchronized (mMessagesFilename) {
            serializeObject(mMessagesFilename, mMessages);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private Set<String> getOneToMany(String tableName, String keyColumnName, String valueColumnName, String key) {
        Set<String> set = new HashSet<>();

        StringBuilder statement = new StringBuilder("SELECT ").append(valueColumnName)
                .append(" FROM ").append(tableName)
                .append(" WHERE ").append(keyColumnName).append(" = ").append("'" + key + "'")
                .append(";");

        try {
            //System.out.println(statement.toString());
            ResultSet rs = mySqlConnection.createStatement().executeQuery(statement.toString());
            while (rs.next()) {
                set.add(rs.getString(valueColumnName));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return set;
    }

    private void setOneToMany(String tableName, String keyColumnName, String valueColumnName, String key, Set<String> values) {
        StringBuilder deleteStatement = new StringBuilder("DELETE FROM ").append(tableName)
                .append(" WHERE ").append(keyColumnName).append(" = ").append("'" + key + "'")
                .append(";");

        StringBuilder insertStatement = new StringBuilder("INSERT INTO ").append(tableName)
                .append("(")
                    .append(keyColumnName).append(", ").append(valueColumnName)
                .append(") VALUES ");
        for (String value : values) {
            insertStatement.append("(").append("'" + key + "'").append(", ").append("'" + value + "'").append("),");
        }
        if (insertStatement.charAt(insertStatement.length() - 1) == ',') {
            insertStatement.deleteCharAt(insertStatement.length() - 1);
        }
        insertStatement.append(";");

        try {
            mySqlConnection.createStatement().execute("START TRANSACTION;");
            //System.out.println(deleteStatement.toString());
            mySqlConnection.createStatement().execute(deleteStatement.toString());
            if (!values.isEmpty()) {
                //System.out.println(insertStatement.toString());
                mySqlConnection.createStatement().execute(insertStatement.toString());
            }
            mySqlConnection.createStatement().execute("COMMIT;");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void insertOrUpdate(String tableName, String keyColumnName, String valueColumnName, String key, String value) {
        StringBuilder statement = new StringBuilder("INSERT INTO ").append(tableName)
                .append(" (")
                    .append(keyColumnName)
                    .append(", ").append(valueColumnName)
                .append(") VALUES (")
                    .append("'" + key + "'")
                    .append(", ").append("'" + value + "'")
                .append(") ON DUPLICATE KEY UPDATE ")
                    .append(valueColumnName).append(" = ").append("'" + value + "'")
            .append(";");

        try {
            //System.out.println(statement.toString());
            mySqlConnection.createStatement().execute(statement.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void insertOrIgnore(String tableName, String keyColumnName, String valueColumnName, String key, String value) {
        StringBuilder statement = new StringBuilder("INSERT IGNORE INTO ").append(tableName)
                .append(" (")
                    .append(keyColumnName)
                    .append(", ").append(valueColumnName)
                .append(") VALUES (")
                    .append("'" + key + "'")
                    .append(", ").append("'" + value + "'")
                .append(")")
            .append(";");

        try {
            //System.out.println(statement.toString());
            mySqlConnection.createStatement().execute(statement.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void serializeObject(String filename, Object object) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
            oos.writeObject(object);
            oos.flush();
            System.out.println("Serialized: " + object.getClass().getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Object deserializeObject(String filename, Object defaultValue) {
        Object object = defaultValue;
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
            object = ois.readObject();
            System.out.println("Deserialized: " + object.getClass().getName());
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return object;
    }
}
