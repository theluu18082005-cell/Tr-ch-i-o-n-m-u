package vn.ptit.colorgame;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Mỗi thông điệp: COMMAND + các trường Base64, ngăn bằng TAB, kết thúc LF. */
public final class Wire {
    public static final int MAX_LINE = 16_384;
    private Wire() {}

    public static String encode(String command, String... fields) {
        if (!command.matches("[A-Z_]{1,32}")) throw new IllegalArgumentException("Bad command");
        StringBuilder b = new StringBuilder(command);
        for (String field : fields) {
            b.append('\t').append(Base64.getEncoder().encodeToString(field.getBytes(StandardCharsets.UTF_8)));
        }
        if (b.length() > MAX_LINE) throw new IllegalArgumentException("Message too long");
        return b.append('\n').toString();
    }

    public static String[] read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (;;) {
            int b = input.read();
            if (b < 0) {
                if (buffer.size() == 0) return null;
                throw new EOFException("Incomplete message");
            }
            if (b == '\n') break;
            if (buffer.size() >= MAX_LINE) throw new IOException("Message too long");
            if (b > 127 || b == '\r') throw new IOException("Invalid wire character");
            buffer.write(b);
        }
        String[] fields = buffer.toString(StandardCharsets.US_ASCII).split("\t", -1);
        if (fields.length > 24 || !fields[0].matches("[A-Z_]{1,32}")) throw new IOException("Invalid command");
        try {
            for (int i = 1; i < fields.length; i++)
                fields[i] = new String(Base64.getDecoder().decode(fields[i]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid Base64", e); }
        return fields;
    }
}
