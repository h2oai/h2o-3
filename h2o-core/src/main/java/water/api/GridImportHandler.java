package water.api;

import hex.Model;
import hex.grid.Grid;
import jdk.internal.util.xml.impl.Input;
import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Key;
import water.api.schemas3.GridImportV3;
import water.api.schemas3.KeyV3;
import water.persist.Persist;
import water.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class GridImportHandler extends Handler {

  @SuppressWarnings("unused")
  public KeyV3.GridKeyV3 importGrid(final int version, final GridImportV3 gridImportV3) throws IOException {
    final URI gridUri = FileUtils.getURI(gridImportV3.grid_directory + "/" + gridImportV3.grid_id);
    final Persist persist = H2O.getPM().getPersistForURI(gridUri);
    try (final InputStream inputStream = persist.open(gridUri.toString())) {
      final AutoBuffer gridAutoBuffer = new AutoBuffer(inputStream);
      final Freezable freezable = gridAutoBuffer.get();
      if (!(freezable instanceof Grid)) {
        throw new IllegalArgumentException(String.format("Given file '%s' is not a Grid", gridImportV3.grid_directory));
      }
      final Grid grid = (Grid) freezable;
      
      loadGridModels(grid,gridImportV3);
      return new KeyV3.GridKeyV3(grid._key);
    }

  }

  private static void loadGridModels(final Grid grid, final GridImportV3 gridImportV3) throws IOException {

    for (Key<Model> k : grid.getModelKeys()) {
      final Model<?, ?, ?> model = Model.importBinaryModel(gridImportV3.grid_directory + "/" + k.toString());
      assert model != null;
    }
  }
}
