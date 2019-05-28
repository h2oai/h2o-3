package hex.genmodel;

import hex.genmodel.transformers.te.TETransformerReader;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to deserialize a model from MOJO format. This is a counterpart to `ModelMojoWriter`.
 */
public abstract class TransformerMojoReader<M extends GenTransformer> extends Reader{

  protected M _transformer;

  public abstract String getTransformerName();

  /**
   * Maximal version of the mojo file current model reader supports. Follows the <code>major.minor</code>
   * format, where <code>minor</code> is a 2-digit number. For example "1.00",
   * "2.05", "2.13". See README in mojoland repository for more details.
   */
  public abstract String mojoVersion();

  public static GenTransformer readFrom(MojoReaderBackend reader, final boolean readModelDescriptor) throws IOException {
    try {
        // Lookup from factory based on transformer type
        TransformerMojoReader tmr = new TETransformerReader();
        tmr._reader = reader;
        tmr.readAll(readModelDescriptor);
        return tmr._transformer;

    } finally {
      if (reader instanceof Closeable)
        ((Closeable) reader).close();
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  public void readAll(final boolean readDescriptor) throws IOException {
//    String[] columns = (String[]) _lkv.get("[columns]");
    if (readDescriptor) {
//      _transformer._modelDescriptor = readTransformerDescriptor();
    }
    readTransformerSpecific();
  }

//  private TransformerDescriptor readTransformerDescriptor() {
//    return null;
//  }

  public abstract void readTransformerSpecific() throws IOException ; 
    
  
}
