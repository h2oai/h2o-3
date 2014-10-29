package water.hadoop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Simple class to help serialize messages from the Mapper to the Driver.
 */
class DriverToMapperMessage extends AbstractMessage {
  public static final char TYPE_FETCH_FLATFILE_RESPONSE = 23;

  private char _type = TYPE_UNKNOWN;

  private String _flatfile = "";

  public DriverToMapperMessage() {
  }

  // Readers
  // -------
  public char getType() { return _type; }
  public String getFlatfile() { return _flatfile; }

  public void read(Socket s) throws Exception {
    _type = readType(s);

    if (_type == TYPE_FETCH_FLATFILE_RESPONSE) {
      _flatfile = readString(s);
    }
    else {
      // Ignore unknown types.
    }
  }

  // Writers
  // -------

  public void setMessageFetchFlatfileResponse(String flatfile) {
    _type = TYPE_FETCH_FLATFILE_RESPONSE;
    _flatfile = flatfile;
  }

  public void write(Socket s) throws Exception {
    if (_type == TYPE_FETCH_FLATFILE_RESPONSE) {
      writeFetchFlatfileResponse(s);
    }
    else {
      throw new Exception("MapperToDriverMessage: write: Unknown type");
    }

    s.getOutputStream().flush();
  }

  //-----------------------------------------------------------------
  // Private below this line.
  //-----------------------------------------------------------------

  private void writeFetchFlatfileResponse(Socket s) throws Exception {
    writeType(s, TYPE_FETCH_FLATFILE_RESPONSE);
    writeString(s, _flatfile);
  }
}
