package com.croconaut.cpt.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UriIdentifierResponse extends UriIdentifier {
    private /*final*/ String name;  // file name
    private /*final*/ long length;  // file length
    private /*final*/ long lastModified;    // last modification time
    private /*final*/ String type;  // mime type

    private /*transient*/ String path;    // exclusive to the app server

    public String getName() {
        return name;
    }

    public long getLength() {
        return length;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "UriIdentifierResponse{" +
                "name='" + name + '\'' +
                ", length=" + length +
                ", lastModified=" + lastModified +
                ", type='" + type + '\'' +
                ", path='" + path + '\'' +
                '}';
    }

    // this is super important -- we must use the path as a key as well -- we might have two different
    // attachments with the same sourceUri and everything but the file can be different (uploaded later with
    // different content...)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        UriIdentifierResponse that = (UriIdentifierResponse) o;

        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }

    // Streamable

    public UriIdentifierResponse() {
    }

    @Override
    public void readFrom(DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        super.readFrom(dis);

        name = dis.readUTF();
        length = dis.readLong();
        lastModified = dis.readLong();
        type = dis.readUTF();
    }

    @Override
    public void writeTo(DataOutputStream dos) throws IOException {
        super.writeTo(dos);

        dos.writeUTF(name);
        dos.writeLong(length);
        dos.writeLong(lastModified);
        dos.writeUTF(type);
    }
}
