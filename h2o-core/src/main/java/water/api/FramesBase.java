package water.api;

import water.api.FramesHandler.Frames;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.schemas3.FrameSynopsisV3;
import water.api.schemas3.FrameV3;
import water.api.schemas3.JobV3;
import water.api.schemas3.SchemaV3;
import water.fvec.Frame;

public class FramesBase<I extends Frames, S extends FramesBase<I, S>> extends SchemaV3<I, S> {

  // Input fields
  @API(help="Name of Frame of interest", json=false)
  public FrameKeyV3 frame_id;

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
  public FrameBase[] frames;

  @API(help="Compatible models", direction=API.Direction.OUTPUT)
  public ModelSchema[] compatible_models;

  @API(help="Domains", direction=API.Direction.OUTPUT)
  public String[][] domain;

  // Non-version-specific filling into the impl
  @Override public I fillImpl(I f) {
    super.fillImpl(f);

    if (null != frames) {
      f.frames = new Frame[frames.length];

      int i = 0;
      for (FrameBase frame : this.frames) {
        f.frames[i++] = frame._fr;
      }
    }
    return f;
  }

  @Override public S fillFromImpl(I f) {
    this.frame_id = new FrameKeyV3(f.frame_id);
    this.column = f.column; // NOTE: this is needed for request handling, but isn't really part of state
    this.find_compatible_models = f.find_compatible_models;

    if (null != f.frames) {
      this.frames = new FrameV3[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameV3(frame, f.row_offset, f.row_count);
      }
    }
    return (S)this;
  }

  public S fillFromImplWithSynopsis(I f) {
    this.frame_id = new FrameKeyV3(f.frame_id);

    if (null != f.frames) {
      this.frames = new FrameSynopsisV3[f.frames.length];

      int i = 0;
      for (Frame frame : f.frames) {
        this.frames[i++] = new FrameSynopsisV3(frame);
      }
    }
    return (S)this;
  }
}
