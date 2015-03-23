package water.parser;

import java.io.*;
import java.util.Arrays;
import java.util.zip.*;
import water.fvec.ByteVec;
import water.fvec.FileVec;
import water.util.Log;
import water.util.UnsafeUtils;

abstract class ZipUtil {

  public static enum Compression { NONE, ZIP, GZIP }

  static byte [] getFirstUnzippedBytes( ByteVec bv ) {
    try{
      byte[] bits = bv.getFirstBytes();
      return unzipBytes(bits, guessCompressionMethod(bits), FileVec.DFLT_CHUNK_SIZE);
    } catch(Exception e) { return null; }
  }

  static Compression guessCompressionMethod(byte [] bits) {
    // Look for ZIP magic
    if( bits.length > ZipFile.LOCHDR && UnsafeUtils.get4(bits, 0) == ZipFile.LOCSIG )
      return Compression.ZIP;
    if( bits.length > 2 && (UnsafeUtils.get2(bits,0)&0xffff) == GZIPInputStream.GZIP_MAGIC )
      return Compression.GZIP;
    return Compression.NONE;
  }

  static float decompressionRatio(ByteVec bv) {
    byte[] zips = bv.getFirstBytes();
    ZipUtil.Compression cpr = ZipUtil.guessCompressionMethod(zips);
    if (cpr == Compression.NONE )
      return 1; // no compression
    else {
      byte[] bits = ZipUtil.unzipBytes(zips, cpr, FileVec.DFLT_CHUNK_SIZE);
      return bits.length / zips.length;
    }
  }

  static byte[] unzipBytes( byte[] bs, Compression cmp, int chkSize ) {
    if( cmp == Compression.NONE ) return bs; // No compression
    // Wrap the bytes in a stream
    ByteArrayInputStream bais = new ByteArrayInputStream(bs);
    InputStream is = null;
    try {
      if( cmp == Compression.ZIP ) {
        ZipInputStream zis = new ZipInputStream(bais);
        ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
        // There is at least one entry in zip file and it is not a directory.
        if( ze == null || ze.isDirectory() ) return bs; // Don't crash, ignore file if cannot unzip
        is = zis;
      } else {
        assert cmp == Compression.GZIP;
        is = new GZIPInputStream(bais);
      }

      // If reading from a compressed stream, estimate we can read 2x uncompressed
      bs = new byte[bs.length * 2];
      // Now read from the compressed stream
      int off = 0;
      while( off < bs.length ) {
        int len = is.read(bs, off, bs.length - off);
        if( len < 0 )
          break;
        off += len;
        if( off == bs.length ) { // Dataset is uncompressing alot! Need more space...
          if( bs.length >= chkSize )
            break; // Already got enough
          bs = Arrays.copyOf(bs, bs.length * 2);
        }
      } 
    } catch( IOException ioe ) { 
      throw Log.throwErr(ioe); 
    } finally { 
      try { if( is != null ) is.close(); } catch( IOException ignore ) { }
    }

    return bs;
  }

  static public int getFileCount(ByteVec bv) {
    int cnt = 0;
    byte[] zips = bv.getFirstBytes();
    ZipUtil.Compression cpr = guessCompressionMethod(zips);
    if (cpr == Compression.NONE || cpr == Compression.GZIP)
      cnt = 1;
    else { //ZIP archives allow multiple files in a single archive
      try {
        ZipInputStream zis = new ZipInputStream(bv.openStream(null));
        ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
        while (ze != null && !ze.isDirectory()) {
          cnt++;
          ze = zis.getNextEntry();
        }
      } catch(IOException ioe) {
          throw new RuntimeException(ioe);
      }
    }
    return cnt;
  }
}
