package water.api;

import hex.Model;
import hex.grid.Grid;
import water.*;
import water.api.schemas3.GridImportExportV3;
import water.api.schemas3.KeyV3;
import water.persist.Persist;
import water.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

public class GridImportExportHandler extends Handler {

  /**
   * Loads a grid from a folder. Path to the folder and grid id (considered to be grid's filename) is required.
   * After a grid is loaded, an attempt to find all it's related models in the very same folder is made.
   * All models must be found in order to successfully import a grid. Grid's version must be the same as the version of
   * H2O it is imported into.
   *
   * @param version      API version
   * @param gridImportV3 Import arguments
   * @return Key to the imported Grid. Grid's key is the same as serialized in the binary file given.
   * @throws IOException Error reading grid or related models.
   */
  @SuppressWarnings("unused")
  public KeyV3.GridKeyV3 importGrid(final int version, final GridImportExportV3 gridImportV3) throws IOException {
    validateGridImportExportV3(gridImportV3);

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
      DKV.put(grid);
      return new KeyV3.GridKeyV3(grid._key);
    }

  }

  @SuppressWarnings("unused")
  public KeyV3.GridKeyV3 exportGrid(final int version, final GridImportExportV3 gridExportV3) throws IOException {
    validateGridImportExportV3(gridExportV3);
    if(DKV.get(gridExportV3.grid_id) == null){
      throw new IllegalArgumentException(String.format("Grid with id '%s' has not been found.", gridExportV3.grid_id));
    }
    final Iced possibleGrid = DKV.get(gridExportV3.grid_id).get();
    if(!(possibleGrid instanceof Grid)){
      throw new IllegalArgumentException(String.format("Given Grid Key '%s' is not a valid Grid.", gridExportV3.grid_id));
    }

    final Grid serializedGrid = (Grid) possibleGrid;
    serializedGrid.export_binary(gridExportV3.grid_directory + "/" + gridExportV3.grid_id);
    serializedGrid.export_models_binary(gridExportV3.grid_directory);

    return new KeyV3.GridKeyV3(serializedGrid._key);
  }


  /**
   * Basic sanity check for given Grid parameters (both import and export).
   *
   * @param input An instance of {@link GridImportExportV3}
   */
  private void validateGridImportExportV3(final GridImportExportV3 input) {
    Objects.requireNonNull(input);
    if (input.grid_directory == null || input.grid_directory.isEmpty()) {
      throw new IllegalArgumentException(String.format("Given grid directory '%s' is not a valid directory.",
              input.grid_directory));
    }

    if (input.grid_id == null || input.grid_id.isEmpty()) {
      throw new IllegalArgumentException(String.format("Invalid Grid id '%s'.", input.grid_id));
    }
  }

  private static void loadGridModels(final Grid grid, final GridImportExportV3 gridImportV3) throws IOException {

    for (Key<Model> k : grid.getModelKeys()) {
      final Model<?, ?, ?> model = Model.importBinaryModel(gridImportV3.grid_directory + "/" + k.toString());
      assert model != null;
    }
  }
}
