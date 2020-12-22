package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.ModelExportAware;

/**
 * Model export REST end-point.
 */
public class ModelExportV3 extends RequestSchemaV3<Iced, ModelExportV3> implements ModelExportAware {

  /** Model to export. */
  @API(help="Name of Model of interest", json=false)
  public KeyV3.ModelKeyV3 model_id;

  /** Destination directory to save exported model. */
  @API(help="Destination file (hdfs, s3, local)")
  public String dir;

  /** Destination directory to save exported model. */
  @API(help="Overwrite destination file in case it exists or throw exception if set to false.")
  public boolean force = true;

  @API(direction = API.Direction.INPUT, help = "Flag indicating whether the exported model artifact should also include CV Holdout Frame predictions",
          level = API.Level.secondary)
  public boolean export_cross_validation_predictions;

  @Override
  public boolean isExportCVPredictionsEnabled() {
    return export_cross_validation_predictions;
  }

}
