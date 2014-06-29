package water.api;


import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.IcedHashMap;

class FramesV2 extends FramesBase {
  // Input fields
  @API(help="Key of Frame of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  Key key; // TODO: this should NOT appear in the output

  @API(help="Name of column of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  String column; // TODO: this should NOT appear in the output

  // Output fields
  @API(help="Map of (string representation of) key to Frame summary.")
  IcedHashMap<String, FrameSummaryV2> frames;

  @API(help="Map of (string representation of) key to Model summary.")
  IcedHashMap<String, ModelsV2.ModelSummaryV2> models;

  @API(help="General information on the response.")
  ResponseInfoV2 response;

  /**
   * Schema for the simple Frame summary representation used (only) in /2/Frames and
   * /2/Models.
   */
  static final class FrameSummaryV2 extends Schema {
    @API(help="String representation of the Frame's key.")
    String key;

    @API(help="Names of all the columns in the Frame.")
    String[] column_names;

    @API(help="Names of all the models compatible with the Frame (only if that is asked for in the request).")
    String[] compatible_models;

    @API(help="Creation time of the Frame.")
    long creation_epoch_time_millis;

    @API(help="Checksum of the Frame (should be treated as an opaque value).")
    String id;

    @API(help="Is this a frame that contains unparsed raw data?")
    boolean is_raw_frame;

    FrameSummaryV2(Frame frame) {
      this.key = frame._key.toString();

      this.column_names = new String[frame._names.length];
      System.arraycopy(frame._names, 0, this.column_names, 0, this.column_names.length);

      this.compatible_models = new String[0]; // TODO
      this.creation_epoch_time_millis = -1; // TODO
      this.id = "deadb33fcafed00d"; // TODO
      this.is_raw_frame = false; // TODO
    }

    @Override protected FrameSummaryV2 fillInto( Handler h ) { throw H2O.fail("fillInto should never be called on FrameSummaryV2"); }
    @Override protected FrameSummaryV2 fillFrom( Handler h ) { throw H2O.fail("fillFrom should never be called on FrameSummaryV2"); }
  }


  // Version-specific filling into the handler
  @Override protected FramesBase fillInto( FramesHandler h ) {
    h.key = this.key;
    h.column = this.column; // NOTE: this is needed for request handling, but isn't really part of state

    if (null != frames) {
      h.frames = new Frame[frames.size()];

      int i = 0;
      for (FrameSummaryV2 frame : this.frames.values()) {
        h.frames[i++] = FramesHandler.getFromDKV(frame.key);
      }
    }
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected FramesBase fillFrom( FramesHandler h ) {
    this.key = h.key;
    this.column = h.column; // NOTE: this is needed for request handling, but isn't really part of state

    this.frames = new IcedHashMap<>();
    if (null != h.frames) {
      for (Frame frame : h.frames) {
        this.frames.put(frame._key.toString(), new FrameSummaryV2(frame));
      }
    }

    // TODO:
    this.models = new IcedHashMap<>();

    // TODO:
    this.response = new ResponseInfoV2();

    return this;
  }
}
