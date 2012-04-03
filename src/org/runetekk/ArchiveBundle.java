package org.runetekk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/**
 * ArchiveBundle.java
 * @version 1.0.0
 * @author RuneTekk Development (SiniSoul)
 * 
 * This class is only meant for write only purposes and has been optimized for
 * writing the source of this package to a byte array payload.
 */
public final class ArchiveBundle {
    
    /**
     * The amount of entries listed in this bundle. Null entries are allowed
     * but the maximum size is non negotiable without reinitializing the bundle.
     */
    private int amountEntries;
    
    /**
     * The current amount of active entries in the bundle.
     */
    private int activeEntries;
    
    /**
     * The byte array payloads for each individual archive in the bundle.
     */
    private byte[][] archivePayloads;
    
    /**
     * The name hashes for each individual archive in the bundle.
     */
    private int[] nameHashes;
    
    /**
     * The uncompressed sizes for each individual archive.
     */
    private int[] uSizes;
    
    /**
     * The uncompressed sizes for each individual archive.
     */
    private int[] cSizes;
    
    /**
     * The total current compressed size.
     */
    private int cSize;
    
    /**
     * If the entire bundle will be compressed or just the files individually 
     * using the BZip2 compression algorithm. By default the bundle will not
     * be entirely compressed.
     */
    private boolean isCompressed;
    
    /**
     * Initializes this {@link ArchiveBundle};
     * @param amountEntries The amount of entries in this bundle.
     * @param isCompressed The entire archive will be compressed or each
     *                     individual archive.
     */
    public void initialize(int amountEntries, boolean isCompressed) {
        archivePayloads = new byte[amountEntries][];
        nameHashes = new int[amountEntries];
        uSizes = new int[amountEntries];
        cSizes = new int[amountEntries];
        this.amountEntries = amountEntries;
        this.isCompressed = isCompressed;
    }
    
    /**
     * Puts an archive at an index in this bundle.
     * @param index The index to put the payload and entry data at.
     * @param name The name of the archive.
     * @param payload The byte array payload of the archive.
     */
    public void put(int index, String name, byte[] payload) throws IOException {
        int nameHash = 0;
        name = name.toUpperCase();
        for(int j = 0; j < name.length(); j++)
            nameHash = (nameHash * 61 + name.charAt(j)) - 32;    
        if(!isCompressed) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BZip2CompressorOutputStream bz2os = new BZip2CompressorOutputStream(os);
            bz2os.write(payload);
            payload = os.toByteArray();         
        }
        cSize += cSizes[index] = payload.length;
        nameHashes[index] = nameHash;
        if(archivePayloads[index] == null)
            activeEntries++;
        archivePayloads[index] = payload;
    }
    
    /**
     * Removes an archive from being indexed.
     * @param index The index of the payload to remove.
     */
    public void remove(int index) {
        if(archivePayloads[index] != null)
            activeEntries--;        
        archivePayloads[index] = null;
    }
    
    /**
     * Packs this bundle into an archive.
     * @return The byte array payload of the created archive.
     */
    public byte[] pack() throws IOException {        
        byte[] footer = new byte[activeEntries * 10 + cSize + 2];
        footer[0] = (byte) (activeEntries >> 8);
        footer[1] = (byte)  activeEntries;
        int informationOffset = 2;
        for(int i = 0; i < amountEntries; i++) {
            if(archivePayloads[i] != null) {
                footer[informationOffset++] = (byte) (nameHashes[i] >> 24);
                footer[informationOffset++] = (byte) (nameHashes[i] >> 16);
                footer[informationOffset++] = (byte) (nameHashes[i] >> 8);
                footer[informationOffset++] = (byte)  nameHashes[i];
                footer[informationOffset++] = (byte) (uSizes[i] >> 16);
                footer[informationOffset++] = (byte) (uSizes[i] >> 8);
                footer[informationOffset++] = (byte)  uSizes[i];
                footer[informationOffset++] = (byte) (cSizes[i] >> 16);
                footer[informationOffset++] = (byte) (cSizes[i] >> 8);
                footer[informationOffset++] = (byte)  cSizes[i];
            }
        }
        for(int i = 0; i < amountEntries; i++) {
            if(archivePayloads[i] != null) {
                System.arraycopy(archivePayloads[i], 0, footer, informationOffset, cSizes[i]);
                informationOffset += cSizes[i];
            }
        }
        byte[] header = new byte[3 + 3];      
        int usize = footer.length;
        header[0] = (byte) (usize >> 16);
        header[1] = (byte) (usize >> 8);
        header[2] = (byte)  usize;
        if(isCompressed) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BZip2CompressorOutputStream bz2os = new BZip2CompressorOutputStream(os);
            bz2os.write(footer);
            footer = os.toByteArray();    
        }
        int csize = footer.length;
        header[3] = (byte) (csize >> 16);
        header[4] = (byte) (csize >> 8);
        header[5] = (byte)  csize;
        byte[] payload = new byte[header.length + csize];
        System.arraycopy(header, 0, payload, 0, payload.length);
        System.arraycopy(footer, 0, payload, header.length, footer.length);
        return payload;
    }
    
    /**
     * Constructs a new {@link ArchiveBundle};
     * @param amountEntries The amount of entries in this bundle.
     * @param isCompressed The entire archive will be compressed or each
     *                     individual archive.
     */
    public ArchiveBundle(int amountEntries, boolean isCompressed) {
        initialize(amountEntries, isCompressed);
    }
}