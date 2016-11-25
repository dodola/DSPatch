/*
 * Copyright 2016 dodola http://github.com/dodola
 * Copyright (C) 2016 THL A29 Limited, a Tencent company.
 * Copyright (c) 2005, Joe Desbonnet, (jdesbonnet@gmail.com)
 * Copyright 2003-2005 Colin Percival
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package dodola.lib.dspatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class BSUtil {

    /**
     * Length of the diff file header.
     */
    public static final int HEADER_SIZE = 32;
    public static final int BUFFER_SIZE = 8192;

    /**
     * Read from input stream and fill the given buffer from the given offset up
     * to length len.
     */
    public static final boolean readFromStream(InputStream in, byte[] buf, int offset, int len) throws IOException {

        int totalBytesRead = 0;
        while (totalBytesRead < len) {
            int bytesRead = in.read(buf, offset + totalBytesRead, len - totalBytesRead);
            if (bytesRead < 0) {
                return false;
            }
            totalBytesRead += bytesRead;
        }
        return true;
    }

    /**
     * input stream to byte
     *
     * @param in InputStream
     *
     * @return byte[]
     *
     * @throws IOException
     */
    public static byte[] inputStreamToByte(InputStream in) throws IOException {

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] data = new byte[BUFFER_SIZE];
        int count = -1;
        while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
            outStream.write(data, 0, count);
        }

        data = null;
        return outStream.toByteArray();
    }

    @SuppressWarnings("unused")
    public static byte toSigned(int unsigned) {
        return unsigned < 128 ? (byte) unsigned : (byte) (unsigned - 256);
    }

    public static short toUnsigned(byte signed) {
        if (signed >= 0) {
            return signed;
        } else {
            return (short) ((short) 256 + signed);
        }
    }

    public static InputStream readBzip2Data(byte[] data, long offset, long length) throws IOException {
        if (length == -1) {
            length = data.length - offset;
        }
        ByteArrayInputStream is = new ByteArrayInputStream(data, (int) offset, (int) length);
        BZip2CompressorInputStream bis = new BZip2CompressorInputStream(is);
        return bis;
    }

    public static long BZ2_bzRead(InputStream bzip2is, byte[] dest, long offset, long length)
        throws IOException {
        for (long i = 0; i < length; ) {
            int len = bzip2is.read(dest, (int) (offset + i), (int) (length - i));
            if (len == -1) {
                throw new IOException("Bzip2 EOF");
            }
            i += len;
        }
        return length;
    }

    public static long offtin(byte[] header, int offset) {
        long y = 0;
        offset += 7;
        y = header[offset] & 0x7F;
        boolean sign = (header[offset] & 0x80) != 0;
        for (int i = 6; i >= 0; i--) {
            y = y * 256 + toUnsigned(header[--offset]);
        }
        if (sign) {
            y = -y;
        }
        return y;
    }
}