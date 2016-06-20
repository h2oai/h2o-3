package water.api;

import hex.grid.Grid;
import hex.schemas.GridSchemaV99;
import water.Key;
import water.KeySnapshot;
import water.Value;
import water.api.schemas99.GridsV99;

/**
 * /Grids/ end-point handler.
 */
public class GridsHandler extends Handler {

  /**
   * Return all the grids.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GridsV99 list(int version, GridsV99 s) {
    final Key[] gridKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, Grid.class);
      }
    }).keys();

    s.grids = new GridSchemaV99[gridKeys.length];
    for (int i = 0; i < gridKeys.length; i++) {
      s.grids[i] = new GridSchemaV99();
      s.grids[i].fillFromImpl(getFromDKV("(none)", gridKeys[i], Grid.class));
    }

    return s;
  }

  /**
   * Return a specified grid.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GridSchemaV99 fetch(int version, GridSchemaV99 s) {
    return s.fillFromImpl(getFromDKV("grid_id", s.grid_id.key(), Grid.class));
  }

}
