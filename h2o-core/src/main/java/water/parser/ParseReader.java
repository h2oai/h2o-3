package water.parser;

/** Manage bulk streaming input data to the parser.  Sometimes the data comes
 *  from parallel raw byte file reads, with speculative line starts.
 *  Sometimes the data comes from an InputStream - probably a GZIP stream.  */
public interface ParseReader {
  // Get another chunk of byte data
  byte[] getChunkData( int cidx );
  int  getChunkDataStart( int cidx );
  void setChunkDataStart( int cidx, int offset );
  long getGlobalByteOffset();
}