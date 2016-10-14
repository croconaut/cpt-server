package com.croconaut.cpt.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Streamable {
    // requires a public zero-argument constructor

    void readFrom(DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException;
    void writeTo(DataOutputStream dos) throws IOException;
}
