package water.persist;

import water.H2O;
import water.Key;
import water.Value;

import java.io.IOException;
import java.net.URI;

/**
 * Parent class for Persist implementations that do eager-load
 * (data is loaded at import time as opposed to parse time).
 */
public abstract class EagerPersistBase extends Persist {

    /* ********************************************* */
    /* UNIMPLEMENTED methods (inspired by PersistS3) */
    /* ********************************************* */

    @Override
    public Key uriToKey(URI uri) {
        throw new UnsupportedOperationException();
    }

    // Store Value v to disk.
    @Override public void store(Value v) {
        if( !v._key.home() ) return;
        throw H2O.unimpl();         // VA only
    }

    @Override
    public void delete(Value v) {
        throw H2O.unimpl();
    }

    @Override
    public void cleanUp() {
        throw H2O.unimpl(); /* user-mode swapping not implemented */
    }

    @Override
    public byte[] load(Value v) throws IOException {
        throw H2O.unimpl();
    }

}
