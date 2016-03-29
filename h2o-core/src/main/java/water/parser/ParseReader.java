package water.parser;

/** Manage bulk streaming input data to the parser.  Sometimes the data comes
 *  from parallel raw byte file reads, with speculative line starts.
 *  Sometimes the data comes from an InputStream - probably a GZIP stream.  */
interface ParseReader {
  // Get another chunk of byte data
  abstract byte[] getChunkData( int cidx );
  abstract int  getChunkDataStart( int cidx );
  abstract void setChunkDataStart( int cidx, int offset );
  long getGlobalByteOffset();
}