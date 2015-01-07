package water.api;

import hex.Model;
import water.*;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.Frame;

public class InspectHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // TODO: REMOVE: this class is now gratuitous.
  protected static final class InspectPojo extends Iced {
    // Inputs
    Value _val;            // Thing to inspect
    long _off;
    int _len;

    // Outputs
    Schema _schema;        // Schema for viewing

    protected InspectPojo() { }

    protected InspectPojo(Value val, long off, int len) {
      this.init(val, off, len);
    }

    protected void init(Value val, long off, int len) {
      _val = val;
      _off = off;
      _len = len;
      if( _val.isFrame() ) {
        _schema = new FrameV2((Frame) _val.get(), off, len); // TODO: version!
      } else if( _val.isModel() ) {
        Model m = (Model) _val.get();
        _schema = m.schema().fillFromImpl(m); // TODO: need a way to look up the schema in the API layer, not polluting the back end
      } else {
        throw H2O.unimpl("Unexpected val class for Inspect: " + _val.get().getClass());
      }
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InspectV1 inspect(int version, InspectV1 s) {
    InspectPojo i = s.createAndFillImpl();
    assert i._val != null : "schema checks null-ness";

    if( i._val.isKey() ) {        // Peek thru a Key
      i._val = DKV.get((Key) i._val.get());
      if( i._val == null ) throw new H2OKeyNotFoundArgumentException("key", s.key.name);
    }

    if( i._val.isFrame() ) {
      // do paging. . .
      // TODO: this should call FrameBase.schema(version).. . .
      i._schema = new FrameV2((Frame)i._val.get(),i._off,i._len);
    } else {
      Model m = (Model)i._val.get();
      i._schema.fillFromImpl(m);
    }

    return s.fillFromImpl(i);
  }
}
