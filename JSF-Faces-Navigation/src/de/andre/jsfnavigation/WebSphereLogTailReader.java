package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

public final class WebSphereLogTailReader {

    private WebSphereLogTailReader() {
    }

    public static String readTail(
            File file,
            int maxBytes)
            throws IOException {

        if (file == null || !file.isFile()) {
            return "(log file not found)";
        }

        RandomAccessFile in =
                new RandomAccessFile(file, "r");

        try {
            long length = in.length();

            int bytes =
                    (int) Math.min(
                            Math.max(4096, maxBytes),
                            Math.min(
                                    Integer.MAX_VALUE,
                                    length));

            long start =
                    Math.max(0L, length - bytes);

            in.seek(start);

            byte[] buffer =
                    new byte[bytes];

            in.readFully(buffer);

            String text =
                    new String(
                            buffer,
                            Charset.defaultCharset());

            if (start > 0L) {
                int newline =
                        Math.max(
                                text.indexOf('\n'),
                                text.indexOf('\r'));

                if (newline >= 0
                        && newline + 1 < text.length()) {

                    text =
                            text.substring(
                                    newline + 1);
                }

                text =
                        "[showing tail of log]\n"
                        + text;
            }

            return text;

        } finally {
            in.close();
        }
    }
}
