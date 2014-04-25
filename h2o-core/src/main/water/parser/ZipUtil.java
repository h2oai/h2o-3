package water.parser;

import java.io.*;
import java.util.Arrays;
import java.util.zip.*;
import water.UDP;
import water.fvec.ByteVec;
import water.fvec.Vec;
import water.util.Log;

abstract class ZipUtil {

  public static enum Compression { NONE, ZIP, GZIP }

  static byte [] getFirstUnzippedBytes( ByteVec bv ) {
    try{
      byte[] bits = bv.getFirstBytes();
      return unzipBytes(bits, guessCompressionMethod(bits));
    } catch(Exception e) { return null; }
  }

  static Compression guessCompressionMethod(byte [] bits) {
    // Look for ZIP magic
    if( bits.length > ZipFile.LOCHDR && UDP.get4(bits,0) == ZipFile.LOCSIG )
      return Compression.ZIP;
    if( bits.length > 2 && (UDP.get2(bits,0)&0xffff) == GZIPInputStream.GZIP_MAGIC )
      return Compression.GZIP;
    return Compression.NONE;
  }

  static byte[] unzipBytes( byte[] bs, Compression cmp ) {
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
          if( bs.length >= Vec.CHUNK_SZ )
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
}
