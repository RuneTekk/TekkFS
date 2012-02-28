package org.runetekk;

import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * FileIndex.java
 * @version 1.0.0
 * @author RuneTekk Development (SiniSoul)
 */
public final class FileIndex {
    
    /**
     * The index id for this {@link FileIndex}.
     */
    private int indexId;
    
    /**
     * The {@link RandomAccessFile} that contains all the chunks for the archives
     * in the file system. 
     */
    private RandomAccessFile mainFile;
    
    /**
     * The {@link RandomAccessFile} that contains all the chunk reference and
     * archive lengths for the archives in this file system.
     */
    private RandomAccessFile indexFile;
    
    /**
     * The byte array that will be used for parsing/writing archives.
     */
    private byte[] chunkBuffer;
    
    /**
     * Creates a new source byte array for an archive parsed from this 
     * {@link FileIndex}. If an {@link IOExceptioN} is thrown while parsing the
     * archive then the method will return null. 
     * @param id The archive id.
     * @return The created source byte array.
     */
    public byte[] get(int id) {
        try {
            indexFile.seek(6L * id);
            int read;
            for(int i = 0; i < 6; i += read) {
                read = indexFile.read(chunkBuffer, i, 6 - i);
                if(read == -1)
                    return null;
            }
            int size = ((chunkBuffer[0] & 0xff) << 16) + ((chunkBuffer[1] & 0xff) << 8) + (chunkBuffer[2] & 0xff);
            int block = ((chunkBuffer[3] & 0xff) << 16) + ((chunkBuffer[4] & 0xff) << 8) + (chunkBuffer[5] & 0xff);
            if(size < 0)
                return null;
            if(block <= 0 || (long)block > mainFile.length() / 520L)
                return null;
            byte src[] = new byte[size];
            int archiveOffset = 0;
            for(int chunk = 0; archiveOffset < size; chunk++) {
                if(block == 0)
                    return null;
                mainFile.seek(520L * block);
                int off = 0;
                int blockSize = size - archiveOffset;
                if(blockSize > 512)
                    blockSize = 512;
                read = 0;
                for(; off < blockSize + 8; off += read)  {
                    read = mainFile.read(chunkBuffer, off, (blockSize + 8) - off);
                    if(read == -1)
                        return null;
                }
                int expectedArchive = ((chunkBuffer[0] & 0xff) << 8) + (chunkBuffer[1] & 0xff);
                int expectedChunk = ((chunkBuffer[2] & 0xff) << 8) + (chunkBuffer[3] & 0xff);
                int nextBlock = ((chunkBuffer[4] & 0xff) << 16) + ((chunkBuffer[5] & 0xff) << 8) + (chunkBuffer[6] & 0xff);
                int expectedIndex = chunkBuffer[7] & 0xff;
                if(expectedArchive != id || expectedChunk != chunk || expectedIndex != indexId)
                    return null;
                if(nextBlock < 0 || (long)nextBlock > mainFile.length() / 520L)
                    return null;
                for(int i = 0; i < blockSize; i++)
                    src[archiveOffset++] = chunkBuffer[i + 8];
                block = nextBlock;
            }
            return src;
        } catch(IOException ioex) {
            return null;
        }
    }
    
    public synchronized boolean put(byte src[], int id, int len) {
        boolean successful = put(src, id, len, true);
        if(!successful)
            successful = put(src, id, len, false);
        return successful;
    }

    /**
     * Writes an archive to the cache, the archive will succeed always
     * if the file does not exist and {@link IOException} is not thrown.
     * If the exists option is true then the written archive will override
     * all the data for the previously written archive.
     * @param src The source byte array.
     * @param id The archive id.
     * @param len The length of the source byte array.
     * @param exists The archive is expected to already preexist.
     * @return If writing the file to this {@link FileIndex} was successful.
     */
    private boolean put(byte src[], int id, int len, boolean exists) {
        try {
            int firstBlock;
            if(exists) {
                indexFile.seek(id * 6L);
                int read;
                for(int off = 0; off < 6; off += read) {
                    read = indexFile.read(chunkBuffer, off, 6 - off);
                    if(read == -1)
                        return false;
                }
                firstBlock = ((chunkBuffer[3] & 0xff) << 16) + ((chunkBuffer[4] & 0xff) << 8) + (chunkBuffer[5] & 0xff);
                if(firstBlock <= 0 || (long)firstBlock > mainFile.length() / 520L)
                    return false;
            } else {
                firstBlock = (int)((mainFile.length() + 519L) / 520L);
                if(firstBlock == 0)
                    firstBlock = 1;
            }
            chunkBuffer[0] = (byte) (len >> 16);
            chunkBuffer[1] = (byte) (len >> 8);
            chunkBuffer[2] = (byte)  len;
            chunkBuffer[3] = (byte) (firstBlock >> 16);
            chunkBuffer[4] = (byte) (firstBlock >> 8);
            chunkBuffer[5] = (byte)  firstBlock;
            indexFile.seek(id * 6L);
            indexFile.write(chunkBuffer, 0, 6);
            int archiveOffset = 0;
            for(int chunk = 0; archiveOffset < len; chunk++) {
                int nextBlock = 0;
                if(exists) {
                    mainFile.seek(firstBlock * 520L);
                    int off;
                    int read;
                    for(off = 0; off < 8; off += read) {
                        read = mainFile.read(chunkBuffer, off, 8 - off);
                        if(read == -1)
                            break;
                    }
                    if(off == 8) {
                        int expectedArchive = ((chunkBuffer[0] & 0xff) << 8) + (chunkBuffer[1] & 0xff);
                        int expectedChunk = ((chunkBuffer[2] & 0xff) << 8) + (chunkBuffer[3] & 0xff);
                        nextBlock = ((chunkBuffer[4] & 0xff) << 16) + ((chunkBuffer[5] & 0xff) << 8) + (chunkBuffer[6] & 0xff);
                        int expectedIndex = chunkBuffer[7] & 0xff;
                        if(expectedArchive != id || expectedChunk != chunk || expectedIndex != indexId)
                            return false;
                        if(nextBlock < 0 || (long)nextBlock > mainFile.length() / 520L)
                            return false;
                    }
                }
                if(nextBlock == 0) {
                    exists = false;
                    nextBlock = (int)((mainFile.length() + 519L) / 520L);
                    if(nextBlock == 0)
                        nextBlock++;
                    if(nextBlock == firstBlock)
                        nextBlock++;
                }
                if(len - archiveOffset <= 512)
                    nextBlock = 0;
                chunkBuffer[0] = (byte)(id >> 8);
                chunkBuffer[1] = (byte) id;
                chunkBuffer[2] = (byte)(chunk >> 8);
                chunkBuffer[3] = (byte) chunk;
                chunkBuffer[4] = (byte)(nextBlock >> 16);
                chunkBuffer[5] = (byte)(nextBlock >> 8);
                chunkBuffer[6] = (byte) nextBlock;
                chunkBuffer[7] = (byte) indexId;
                mainFile.seek(firstBlock * 520L);
                mainFile.write(chunkBuffer, 0, 8);
                int blockSize = len - archiveOffset;
                if(blockSize > 512)
                    blockSize = 512;
                mainFile.write(src, archiveOffset, blockSize);
                archiveOffset += blockSize;
                firstBlock = nextBlock;
            }
            return true;
        } catch(IOException ex) {
            return false;
        }
    }
    
    /**
     * Destroys this {@link FileIndex}.
     * This {@link FileIndex} will not be usable after it is destroyed.
     */
    public void destroy() {
        try {
            mainFile.close();
            indexFile.close();
        } catch(IOException ioex) {}
        chunkBuffer = null;
    }
    
    /**
     * Constructs a new {@link FileIndex};
     * @param indexId The index id.
     * @param mainChannel The {@link RandomAccessFile} for the main index file.
     * @param indexChannel The {@link RandomAccessFile} for the index file.
     */
    public FileIndex(int indexId, RandomAccessFile mainChannel, RandomAccessFile indexChannel) {
        this.indexId = indexId;
        this.mainFile = mainChannel;
        this.indexFile = indexChannel;
        chunkBuffer = new byte[520];
    }
}
