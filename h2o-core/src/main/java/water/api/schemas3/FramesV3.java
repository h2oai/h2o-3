package water.api.schemas3;

import water.api.API;
import water.api.FramesHandler.Frames;
import water.fvec.Frame;

public class FramesV3 extends SchemaV3<Frames,FramesV3> {

  // Input fields
  @API(help="Name of Frame of interest", json=false)
  public KeyV3.FrameKeyV3 frame_id;

  @API(help="Name of column of interest", json=false)
  public String column;

  @API(help="Row offset to return", direction=API.Direction.INOUT)
  public long row_offset;

  @API(help="Number of rows to return", direction=API.Direction.INOUT)
  public int row_count;

  @API(help="Column offset to return", direction=API.Direction.INOUT)
  public int column_offset;

  @API(help="Number of columns to return", direction=API.Direction.INOUT)
  public int column_count;

  @API(help="Find and return compatible models?", json=false)
  public boolean find_compatible_models = false;

  @API(help="File output path",json=false)
  public String path;

  @API(help="Overwrite existing file",json=false)
  public boolean force;

  @API(help="Job for export file",direction=API.Direction.OUTPUT)
  public JobV3 job;

  // Output fields
  @API(help="Frames", direction=API.Direction.OUTPUT)
  public FrameBaseV3[] frames;

  @API(help="Compatible models", direction=API.Direction.OUTPUT)
  public ModelSchemaV3[] compatible_models;

  @API(help="Domains", direction=API.Direction.OUTPUT)
  public String[][] domain;

  // Non-version-specific filling into the impl
  @Override public Frames fillImpl(Frames f) {
    super.fillImpl(f);

    if (frames != null) {
      f.frames = new Frame[frames.length];

      int i = 0;
      for (FrameBaseV3 frame : this.frames) {
        f.frames[i++] = frame._fr;
      }
    }
    return f;
  }

  @Override
  public FramesV3 fillFromImpl(Frames f) {
    this.frame_id = new KeyV3.FrameKeyV3(f.frame_id);
    this.column = f.column; // NOTE: this is needed for request handling, but isn't really part of state
    this.find_compatible_models = f.find_compatible_models;

    if (f.frames != null) {
      this.frames = new FrameV3[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameV3(frame, f.row_offset, f.row_count);
      }
    }
    return this;
  }

  public FramesV3 fillFromImplWithSynopsis(Frames f) {
    this.frame_id = new KeyV3.FrameKeyV3(f.frame_id);

    if (f.frames != null) {
      this.frames = new FrameSynopsisV3[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameSynopsisV3(frame);
      }
    }
    return this;
  }
}
