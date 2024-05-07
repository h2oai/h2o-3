package water.api.schemas3;

import water.api.API;
import water.api.FramesHandler.Frames;
import water.fvec.Frame;
import water.util.ExportFileFormat;

public class FramesV3 extends RequestSchemaV3<Frames, FramesV3> {

  // Input fields
  @API(help="Name of Frame of interest", json=false)
  public KeyV3.FrameKeyV3 frame_id;

  @API(help="Name of column of interest", json=false)
  public String column;

  @API(help="Row offset to return", direction=API.Direction.INOUT)
  public long row_offset;

  @API(help="Number of rows to return", direction=API.Direction.INOUT)
  public int row_count = -1;

  @API(help="Column offset to return", direction=API.Direction.INOUT)
  public int column_offset;

  @API(help="Number of full columns to return. The columns between full_column_count and column_count will be returned without the data", direction=API.Direction.INOUT)
  public int full_column_count = -1;

  @API(help="Number of columns to return", direction=API.Direction.INOUT)
  public int column_count = -1;

  @API(help="Find and return compatible models?", json=false)
  public boolean find_compatible_models = false;

  @API(help="File output path",json=false)
  public String path;

  @API(help="Overwrite existing file",json=false)
  public boolean force;

  @API(help="Number of part files to use (1=single file,-1=automatic)",json=false)
  public int num_parts = 1;

  @API(help="Use parallel export to a single file (doesn't apply when num_parts != 1, creates temporary files in the destination directory)",json=false)
  public boolean parallel;

  @API(help="Output file format. Defaults to 'csv'.", values = { "csv", "parquet"} , json=false)
  public ExportFileFormat format;

  @API(help="Compression method (default none; gzip, bzip2, zstd and snappy available depending on runtime environment)")
  public String compression;

  @API(help="Specifies if checksum should be written next to data files on export (if supported by export format).")
  public boolean write_checksum = true;

  @API(help="Field separator (default ',')")
  public byte separator = Frame.CSVStreamParams.DEFAULT_SEPARATOR;

  @API(help="Use header (default true)")
  public boolean header = true;

  @API(help="Quote column names in header line (default true)")
  public boolean quote_header = true;

  @API(help="Job for export file",direction=API.Direction.OUTPUT)
  public JobV3 job;

  // Output fields
  @API(help="Frames", direction=API.Direction.OUTPUT)
  public FrameV3[] frames;

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
}
