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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * 从JBSPatch修改而来，JBSPatch 只支持Gzip的diff格式，现迁移到Bzip2格式下
 */
public class DSPatch {

    /**
     * the patch process is end up successfully
     */
    public static final int RETURN_SUCCESS = 1;

    /**
     * diffFile is null, or the diffFile does not exist
     */
    public static final int RETURN_DIFF_FILE_ERR = 2;

    /**
     * oldFile is null, or the oldFile does not exist
     */
    public static final int RETURN_OLD_FILE_ERR = 3;

    /**
     * newFile is null, or can not create the newFile
     */
    public static final int RETURN_NEW_FILE_ERR = 4;

    /**
     * DSPatch using less memory size. 占用更小内存
     * Memory size = diffFile size + max block size
     */
    public static int patchLessMemory(RandomAccessFile oldFile, File newFile, File diffFile, int extLen)
        throws IOException {
        if (oldFile == null || oldFile.length() <= 0) {
            return RETURN_OLD_FILE_ERR;
        }
        if (newFile == null) {
            return RETURN_NEW_FILE_ERR;
        }
        if (diffFile == null || diffFile.length() <= 0) {
            return RETURN_DIFF_FILE_ERR;
        }

        byte[] diffBytes = new byte[(int) diffFile.length()];
        InputStream diffInputStream = new FileInputStream(diffFile);
        try {
            BSUtil.readFromStream(diffInputStream, diffBytes, 0, diffBytes.length);
        } finally {
            diffInputStream.close();
        }
        return patchLessMemory(oldFile, (int) oldFile.length(), diffBytes, diffBytes.length, newFile, extLen);
    }

    /**
     * DSPatch using less memory size. 占用更小的内存
     * Memory size = diffFile size + max block size
     * extLen   一般没有额外的数据，此处设置为0
     */
    public static int patchLessMemory(RandomAccessFile oldFile, int oldsize, byte[] diffBuf, int diffSize, File newFile,
                                      int extLen) throws IOException {

        if (oldFile == null || oldsize <= 0) {
            return RETURN_OLD_FILE_ERR;
        }
        if (newFile == null) {
            return RETURN_NEW_FILE_ERR;
        }
        if (diffBuf == null || diffSize <= 0) {
            return RETURN_DIFF_FILE_ERR;
        }

        int commentLenPos = oldsize - extLen - 2;
        if (commentLenPos <= 2) {
            return RETURN_OLD_FILE_ERR;
        }

        /* 读取头 */
        if (diffBuf.length < 32) {
            throw new IOException("Header.len < 32");
        }
        byte[] header = diffBuf;
        if (header[0] != 'B' || header[1] != 'S' || header[2] != 'D' || header[3] != 'I' ||
            header[4] != 'F' || header[5] != 'F' || header[6] != '4' || header[7] != '0') {
            throw new IOException("Invalid header signature");
        }
        /* 读取header长度 */
        long bzctrllen = BSUtil.offtin(header, 8);
        long bzdatalen = BSUtil.offtin(header, 16);
        long newsize = BSUtil.offtin(header, 24);
        if (bzctrllen < 0 || bzdatalen < 0 || newsize < 0) {
            throw new IOException("Invalid header lengths");
        }

        InputStream ctrlBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE, bzctrllen);

        InputStream diffBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE + bzctrllen, bzdatalen);
        InputStream extraBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE + bzctrllen + bzdatalen, -1);

        OutputStream outStream = new FileOutputStream(newFile);
        try {
            int oldpos = 0;
            int newpos = 0;
            int[] ctrl = new int[3];
            byte buf[] = new byte[8];

            // int nbytes;
            while (newpos < newsize) {

                long lenread;

                for (int i = 0; i <= 2; i++) {
                    lenread = BSUtil.BZ2_bzRead(ctrlBlockIn, buf, 0, 8);
                    if (lenread < 8) {
                        throw new IOException("Failed to read control data");
                    }
                    ctrl[i] = (int) BSUtil.offtin(buf, 0);
                }

                if (newpos + ctrl[0] > newsize) {
                    outStream.close();
                    return RETURN_DIFF_FILE_ERR;
                }

                // Read ctrl[0] bytes from diffBlock stream
                byte[] buffer = new byte[ctrl[0]];
                if (!BSUtil.readFromStream(diffBlockIn, buffer, 0, ctrl[0])) {
                    outStream.close();
                    return RETURN_DIFF_FILE_ERR;
                }

                byte[] oldBuffer = new byte[ctrl[0]];
                if (oldFile.read(oldBuffer, 0, ctrl[0]) < ctrl[0]) {
                    outStream.close();
                    return RETURN_DIFF_FILE_ERR;
                }
                for (int i = 0; i < ctrl[0]; i++) {
                    if (oldpos + i == commentLenPos) {
                        oldBuffer[i] = 0;
                        oldBuffer[i + 1] = 0;
                    }

                    if ((oldpos + i >= 0) && (oldpos + i < oldsize)) {
                        buffer[i] += oldBuffer[i];
                    }
                }
                outStream.write(buffer);

                //            System.out.println(""+ctrl[0]+ ", " + ctrl[1]+ ", " + ctrl[2]);

                newpos += ctrl[0];
                oldpos += ctrl[0];

                if (newpos + ctrl[1] > newsize) {
                    outStream.close();
                    return RETURN_DIFF_FILE_ERR;
                }

                buffer = new byte[ctrl[1]];
                if (!BSUtil.readFromStream(extraBlockIn, buffer, 0, ctrl[1])) {
                    outStream.close();
                    return RETURN_DIFF_FILE_ERR;
                }
                outStream.write(buffer);
                outStream.flush();

                newpos += ctrl[1];
                oldpos += ctrl[2];
                oldFile.seek(oldpos);
            }
            ctrlBlockIn.close();
            diffBlockIn.close();
            extraBlockIn.close();
        } finally {
            oldFile.close();
            outStream.close();
        }
        return RETURN_SUCCESS;
    }

    /**
     * Memory size = oldBuf + diffBuf + newBuf
     */
    public static int patchFast(File oldFile, File newFile, File diffFile, int extLen) throws IOException {
        if (oldFile == null || oldFile.length() <= 0) {
            return RETURN_OLD_FILE_ERR;
        }
        if (newFile == null) {
            return RETURN_NEW_FILE_ERR;
        }
        if (diffFile == null || diffFile.length() <= 0) {
            return RETURN_DIFF_FILE_ERR;
        }

        InputStream oldInputStream = new BufferedInputStream(new FileInputStream(oldFile));
        byte[] diffBytes = new byte[(int) diffFile.length()];
        InputStream diffInputStream = new FileInputStream(diffFile);
        try {
            BSUtil.readFromStream(diffInputStream, diffBytes, 0, diffBytes.length);
        } finally {
            diffInputStream.close();
        }

        byte[] newBytes = patchFast(oldInputStream, (int) oldFile.length(), diffBytes, extLen);

        OutputStream newOutputStream = new FileOutputStream(newFile);
        try {
            newOutputStream.write(newBytes);
        } finally {
            newOutputStream.close();
        }
        return RETURN_SUCCESS;
    }

    /**
     * This patch method is fast ,but using more memory.
     * Memory size = oldBuf + diffBuf + newBuf
     */
    public static int patchFast(InputStream oldInputStream, InputStream diffInputStream, File newFile)
        throws IOException {
        if (oldInputStream == null) {
            return RETURN_OLD_FILE_ERR;
        }
        if (newFile == null) {
            return RETURN_NEW_FILE_ERR;
        }
        if (diffInputStream == null) {
            return RETURN_DIFF_FILE_ERR;
        }

        byte[] oldBytes = BSUtil.inputStreamToByte(oldInputStream);
        byte[] diffBytes = BSUtil.inputStreamToByte(diffInputStream);

        byte[] newBytes = patchFast(oldBytes, oldBytes.length, diffBytes, diffBytes.length, 0);

        OutputStream newOutputStream = new FileOutputStream(newFile);
        try {
            newOutputStream.write(newBytes);
        } finally {
            newOutputStream.close();
        }
        return RETURN_SUCCESS;
    }

    public static byte[] patchFast(InputStream oldInputStream, InputStream diffInputStream) throws IOException {
        if (oldInputStream == null) {
            return null;
        }

        if (diffInputStream == null) {
            return null;
        }

        byte[] oldBytes = BSUtil.inputStreamToByte(oldInputStream);
        byte[] diffBytes = BSUtil.inputStreamToByte(diffInputStream);

        byte[] newBytes = patchFast(oldBytes, oldBytes.length, diffBytes, diffBytes.length, 0);
        return newBytes;
    }

    /**
     * Memory size = oldBuf + diffBuf + newBuf
     */
    public static byte[] patchFast(InputStream oldInputStream, int oldsize, byte[] diffBytes, int extLen)
        throws IOException {
        byte[] oldBuf = new byte[oldsize];
        BSUtil.readFromStream(oldInputStream, oldBuf, 0, oldsize);
        oldInputStream.close();

        return patchFast(oldBuf, oldsize, diffBytes, diffBytes.length, extLen);
    }

    /**
     * Memory size = oldBuf + diffBuf + newBuf
     */
    public static byte[] patchFast(byte[] oldBuf, int oldsize, byte[] diffBuf, int diffSize, int extLen)
        throws IOException {
       /*
        File format:
            0   8   "BSDIFF40"
            8   8   X
            16  8   Y
            24  8   sizeof(newfile)
            32  X   bzip2(control block)
            32+X    Y   bzip2(diff block)
            32+X+Y  ??? bzip2(extra block)
        */
        /* 读取头 */
        if (diffBuf.length < 32) {
            throw new IOException("Header.len < 32");
        }
        byte[] header = diffBuf;
        if (header[0] != 'B' || header[1] != 'S' || header[2] != 'D' || header[3] != 'I' ||
            header[4] != 'F' || header[5] != 'F' || header[6] != '4' || header[7] != '0') {
            throw new IOException("Invalid header signature");
        }
        /* 读取header长度 */
        long bzctrllen = BSUtil.offtin(header, 8);
        long bzdatalen = BSUtil.offtin(header, 16);
        long newsize = BSUtil.offtin(header, 24);
        if (bzctrllen < 0 || bzdatalen < 0 || newsize < 0) {
            throw new IOException("Invalid header lengths");
        }

        InputStream ctrlBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE, bzctrllen);

        InputStream diffBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE + bzctrllen, bzdatalen);
        InputStream extraBlockIn = BSUtil.readBzip2Data(diffBuf, BSUtil.HEADER_SIZE + bzctrllen + bzdatalen, -1);

        // byte[] newBuf = new byte[newsize + 1];
        byte[] newBuf = new byte[(int) newsize];

        int oldpos = 0;
        int newpos = 0;
        int[] ctrl = new int[3];
        byte buf[] = new byte[8];

        // int nbytes;
        while (newpos < newsize) {

            long lenread;

            for (int i = 0; i <= 2; i++) {
                lenread = BSUtil.BZ2_bzRead(ctrlBlockIn, buf, 0, 8);
                if (lenread < 8) {
                    throw new IOException("Failed to read control data");
                }
                ctrl[i] = (int) BSUtil.offtin(buf, 0);
            }

            if (newpos + ctrl[0] > newsize) {
                throw new IOException("Corrupt by wrong patch file.");
            }

            // Read ctrl[0] bytes from diffBlock stream
            if (!BSUtil.readFromStream(diffBlockIn, newBuf, newpos, ctrl[0])) {
                throw new IOException("Corrupt by wrong patch file.");
            }

            for (int i = 0; i < ctrl[0]; i++) {
                if ((oldpos + i >= 0) && (oldpos + i < oldsize)) {
                    newBuf[newpos + i] += oldBuf[oldpos + i];
                }
            }

            newpos += ctrl[0];
            oldpos += ctrl[0];

            if (newpos + ctrl[1] > newsize) {
                throw new IOException("Corrupt by wrong patch file.");
            }

            if (!BSUtil.readFromStream(extraBlockIn, newBuf, newpos, ctrl[1])) {
                throw new IOException("Corrupt by wrong patch file.");
            }

            newpos += ctrl[1];
            oldpos += ctrl[2];
        }
        ctrlBlockIn.close();
        diffBlockIn.close();
        extraBlockIn.close();

        return newBuf;
    }

}