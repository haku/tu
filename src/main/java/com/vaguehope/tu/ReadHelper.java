package com.vaguehope.tu;

import java.io.IOException;
import java.io.InputStream;

public final class ReadHelper {

    private ReadHelper() {
        throw new AssertionError();
    }

    /**
     * Read an input stream into a byte array while filling the array as
     * full as possible.
     * If there is insufficient data available the array may not be written all the way to the
     * <code>end</code> position.
     * Use the return value to determine exactly how much data was written.
     *
     * @param in
     *            The source to read data from.
     * @param b
     *            The array to read data into.
     * @param start
     *            Position to writing data from.
     * @param end
     *            the position to stop writing data at, assuming there is that much data available.
     * @return The upper boundary for the data written into the array or less than 0 if there was no
     *         data available.
     */
    public static int readFully(final InputStream in, final byte b[], final int start, final int end) throws IOException {
        int x = start;
        while (x < end) {
            int count = in.read(b, x, end - x);
            if (count < 0) {
                return x > start ? x : count;
            }
            x += count;
        }
        return x;
    }

}
