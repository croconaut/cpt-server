package com.croconaut.cpt.network;

import com.croconaut.CptSyncThread;

import java.io.*;
import java.util.Date;

// This is not a 1:1 copy of CPT's class, only the fields and name is the same.
public class NetworkPersistentAttachment implements StreamableAttachment, Serializable {
    private static final long serialVersionUID = 1L;

    private /*final*/ String sourceUri;
    private /*final*/ String name;  // file name to send over network
    private /*final*/ long length;
    private /*final*/ String appId;

    private /*transient*/ String path;  // read in writeTo(), written in readFrom()

    public NetworkPersistentAttachment(String path, String srcUri, String name, long length, String appId) {
        this.sourceUri = srcUri;
        this.name = name;
        this.length = length;
        this.appId = appId;
        this.path = path;
    }

    // these five methods are the only ones allowed. time & type is irrelevant for persistent messages,
    // they can be requested by the app in local attachment when delivered
    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public String getStorageDirectory() {
        // private only
        return null;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getSourceUri() {
        // not really useful
        return sourceUri;
    }

    @Override
    public Date getLastModified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "NetworkPersistentAttachment{" +
                "sourceUri='" + sourceUri + '\'' +
                ", name='" + name + '\'' +
                ", length=" + length +
                ", path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkPersistentAttachment that = (NetworkPersistentAttachment) o;

        if (!sourceUri.equals(that.sourceUri)) return false;
        if (!name.equals(that.name)) return false;
        return path.equals(that.path);

    }

    @Override
    public int hashCode() {
        int result = sourceUri.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }

    // Streamable

    public NetworkPersistentAttachment() {
    }

    @Override
    public void readFrom(DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        sourceUri = dis.readUTF();
        name = dis.readUTF();
        length = dis.readLong();
        appId = dis.readUTF();

        boolean isValid = dis.readBoolean();
        if (!isValid) {
            throw new FileNotFoundException("Unable to download this attachment: " + this);
        }

        final String thisCrocoIdPath = ((CptSyncThread) Thread.currentThread()).getPath();

        File file = new File(new File(thisCrocoIdPath, appId).getPath(), name);
        final File parent = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        parent.mkdirs();
        if (!file.createNewFile()) {
            // file not created because it exists
            file = File.createTempFile("tmp", "_" + file.getName(), parent);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);

            byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
            int len;
            long total = 0;
            while (total != length && (len = dis.read(buffer, 0, Math.min(buffer.length, (int) (length - total)))) != -1) {
                fos.write(buffer, 0, len);
                total += len;
            }

            if (total != length) {
                throw new IOException("read() ended prematurely");
            }

            this.path = file.getPath();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw e;
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    @Override
    public void writeTo(DataOutputStream dos) throws IOException {
        dos.writeUTF(sourceUri);
        dos.writeUTF(name);
        dos.writeLong(length);
        dos.writeUTF(appId);

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(new File(path));

            dos.writeBoolean(true); // stream valid

            byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
            int len;
            long total = 0;
            while (total != length && (len = fis.read(buffer, 0, Math.min(buffer.length, (int) (length - total)))) != -1) {
                dos.write(buffer, 0, len);
                total += len;
            }

            if (total != length) {
                throw new IOException("read() ended prematurely");
            }
        } catch (FileNotFoundException e){
            dos.writeBoolean(false);    // stream invalid
            throw e;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }
}
