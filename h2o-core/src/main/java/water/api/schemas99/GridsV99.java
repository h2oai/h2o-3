package water.api.schemas99;

import hex.schemas.GridSchemaV99;
import water.api.API;
import water.api.schemas3.SchemaV3;
import water.api.Grids;


public class GridsV99 extends SchemaV3<Grids, GridsV99> {

  @API(help="Grids", direction=API.Direction.OUTPUT)
  public GridSchemaV99[] grids;

}
