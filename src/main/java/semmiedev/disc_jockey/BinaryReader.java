package semmiedev.disc_jockey;

import java.io.IOException;
import java.io.InputStream;

public class BinaryReader {
    private final InputStream stream;

    public BinaryReader(InputStream stream) {
        this.stream = stream;
    }

    public short readShort() throws IOException {
        return (short) (stream.read() | (stream.read() << 8));
    }

    public byte readByte() throws IOException {
        return (byte) stream.read();
    }

    public int readInt() throws IOException {
        return stream.read() | (stream.read() << 8) | (stream.read() << 16) | (stream.read() << 24);
    }

    public String readString() throws IOException {
        int length = readInt();
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length) {
            int result = stream.read(bytes, read, length - read);
            if (result == -1) break;
            read += result;
        }
        return new String(bytes);
    }
}
