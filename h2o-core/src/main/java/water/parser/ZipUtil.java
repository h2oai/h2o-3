package water.parser;

import water.DKV;
import water.Iced;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.ByteVec;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.util.Log;
import water.util.UnsafeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static water.fvec.FileVec.getPathForKey;

abstract class ZipUtil {

  public enum Compression { NONE, ZIP, GZIP }

  /**
   * This method will attempt to read the few bytes off a file which will in turn be used
   * to guess what kind of parsers we should use to parse the file.
   *
   * @param bv
   * @return
   */
  static byte [] getFirstUnzippedBytes( ByteVec bv ) {
    try {
      byte[] bits = bv.getFirstBytes();
      return unzipBytes(bits, guessCompressionMethod(bits), FileVec.DFLT_CHUNK_SIZE);
    } catch(Exception e) {
      Log.debug("Cannot get unzipped bytes from ByteVec!", e);
      return null;
    }
  }

  /**
   * This method check if the input argument is a zip directory containing files.
   *
   * @param key
   * @return true if bv is a zip directory containing files, false otherwise.
   */
  static boolean isZipDirectory(Key key) {
    Iced ice = DKV.getGet(key);
    if (ice == null) throw new H2OIllegalArgumentException("Missing data", "Did not find any data under " +
            "key " + key);
    ByteVec bv = (ByteVec) (ice instanceof ByteVec ? ice : ((Frame) ice).vecs()[0]);

    return isZipDirectory(bv);
  }

  static boolean isZipDirectory(ByteVec bv) {
    byte[] bits = bv.getFirstBytes();
    ZipUtil.Compression compressionMethod = guessCompressionMethod(bits);
    try {
      if (compressionMethod == Compression.ZIP) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bits);
        ZipInputStream zis = new ZipInputStream(bais);
        ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
        boolean isDir = ze.isDirectory();
        zis.close();
        // There is at least one entry in zip file and it is not a directory.
        return isDir;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return false;
  }

  static ArrayList<String> getFileNames(ByteVec bv) {
    ArrayList<String> fileList = new ArrayList<String>();

    if (bv instanceof FileVec) {
      String strPath = getPathForKey(((FileVec) bv)._key);

      try {
        ZipFile zipFile = new ZipFile(strPath);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.isDirectory()) {// add file to list to parse if not a directory.
            fileList.add(entry.getName());
          }
        }
        zipFile.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return fileList;
  }


  /**
   *   When a file is a zip file that contains multiple files, this method will return the decompression ratio.
   *
   * @param bv
   * @return
   */
  static float getDecompressionRatio(ByteVec bv) {
    long totalSize = 0L;
    long totalCompSize = 0L;

    if (bv instanceof FileVec) {
      String strPath = getPathForKey(((FileVec) bv)._key);

      try {
        ZipFile zipFile = new ZipFile(strPath);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.isDirectory()) {// add file to list to parse if not a directory.
            totalSize = totalSize + entry.getSize();
            totalCompSize = totalCompSize + entry.getCompressedSize();
          }
        }
        zipFile.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (totalCompSize == 0) // something is wrong.  Return no compression.
      return 1;
    else
      return totalSize/totalCompSize;
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
    else if (cpr == Compression.ZIP) {
      ByteArrayInputStream bais = new ByteArrayInputStream(zips);
      ZipInputStream zis = new ZipInputStream(bais);
      ZipEntry ze = null; // Get the *FIRST* entry

      try {
        ze = zis.getNextEntry();
        boolean isDir = ze.isDirectory();

        if (isDir) {
          return getDecompressionRatio(bv);
        } else {
          byte[] bits = ZipUtil.unzipBytes(zips, cpr, FileVec.DFLT_CHUNK_SIZE);
          return bits.length / zips.length;
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      byte[] bits = ZipUtil.unzipBytes(zips, cpr, FileVec.DFLT_CHUNK_SIZE);
      return bits.length / zips.length;
    }
    return 1;
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
        if( ze == null || ze.isDirectory() )
          zis.getNextEntry(); // read the next entry which should be a file
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

  /**
   * This method will read a compressed zip file and return the uncompressed bits so that we can
   * check the beginning of the file and make sure it does not contain the column names.
   *
   * @param bs
   * @param chkSize
   * @return
   */
  static byte[] unzipForHeader( byte[] bs, int chkSize ) {

    ByteArrayInputStream bais = new ByteArrayInputStream(bs);
    ZipInputStream zis = new ZipInputStream(bais);

    InputStream is = zis;

    // Now read from the compressed stream
    int off = 0;

    try {
      while( off < bs.length ) {
        int len = 0;
        len = is.read(bs, off, bs.length - off);
        if( len < 0 )
          break;
        off += len;
        if( off == bs.length ) { // Dataset is uncompressing alot! Need more space...
          if( bs.length >= chkSize )
            break; // Already got enough
          bs = Arrays.copyOf(bs, bs.length * 2);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      is.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return bs;
  }
}
