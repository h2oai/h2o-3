package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.SaveToHiveTableHandler;

public class SaveToHiveTableV3 extends RequestSchemaV3<Iced, SaveToHiveTableV3> {

  //Input fields
  @API(help = "H2O Frame ID", required = true)
  public KeyV3.FrameKeyV3 frame_id;

  @API(help = "HIVE JDBC URL", required = true)
  public String jdbc_url;

  @API(help = "Name of table to save data to.", required = true)
  public String table_name;

  @API(help = "HDFS Path to where the table should be stored.")
  public String table_path;

  @API(help = "Storage format of the created table.", values = {"CSV", "PARQUET"})
  public SaveToHiveTableHandler.HiveFrameSaver.Format format;

  @API(help = "HDFS Path where to store temporary data.")
  public String tmp_path;

}
