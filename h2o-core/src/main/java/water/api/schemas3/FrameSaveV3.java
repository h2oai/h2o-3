package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 * Frame export REST end-point.
 */
public class FrameSaveV3 extends RequestSchemaV3<Iced, FrameSaveV3> {

    @API(help = "Name of Frame of interest", json = false)
    public KeyV3.FrameKeyV3 frame_id;

    @API(help = "Destination directory (hdfs, s3, local)")
    public String dir;

    @API(help = "Overwrite destination file in case it exists or throw exception if set to false.")
    public boolean force = true;

    @API(help = "Job indicating progress", direction = API.Direction.OUTPUT)
    public JobV3 job;

}
