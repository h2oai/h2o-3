package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 * Frame import REST end-point.
 */
public class FrameLoadV3 extends RequestSchemaV3<Iced, FrameLoadV3> {

  @API(help="Import frame under given key into DKV.", json=false)
  public KeyV3.FrameKeyV3 frame_id;

  @API(help="Source directory (hdfs, s3, local) containing serialized frame")
  public String dir;

  @API(help="Override existing frame in case it exists or throw exception if set to false")
  public boolean force = true;

  @API(help = "Job indicating progress", direction = API.Direction.OUTPUT)
  public JobV3 job;

}
