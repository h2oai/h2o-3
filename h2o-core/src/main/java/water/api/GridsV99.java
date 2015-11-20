package water.api;

import hex.schemas.GridSchemaV99;

class GridsV99 extends Schema<Grids, GridsV99> {
  @API(help="Grids", direction=API.Direction.OUTPUT)
  GridSchemaV99[] grids;
}
