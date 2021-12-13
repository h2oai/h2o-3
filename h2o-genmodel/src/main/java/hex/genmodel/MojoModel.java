package hex.genmodel;

import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.Table;
import hex.genmodel.descriptor.ModelDescriptor;

import java.io.*;

/**
 * Prediction model based on the persisted binary data.
 */
public abstract class MojoModel extends GenModel {

  public String _algoName;
  public String _h2oVersion;
  public hex.ModelCategory _category;
  public String _uuid;
  public boolean _supervised;
  public int _nfeatures;
  public int _nclasses;
  public boolean _balanceClasses;
  public double _defaultThreshold;
  public double[] _priorClassDistrib;
  public double[] _modelClassDistrib;
  public double _mojo_version;
  public ModelDescriptor _modelDescriptor = null;
  public ModelAttributes _modelAttributes = null;
  public Table[] _reproducibilityInformation;

  /**
   * Primary factory method for constructing MojoModel instances.
   *
   * @param file Name of the zip file (or folder) with the model's data. This should be the data retrieved via
   *             the `GET /3/Models/{model_id}/mojo` endpoint.
   * @return New `MojoModel` object.
   * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
   */
  public static MojoModel load(String file) throws IOException {
    return load(file, false);
  }

  /**
   * Primary factory method for constructing MojoModel instances.
   *
   * @param file Name of the zip file (or folder) with the model's data. This should be the data retrieved via
   *             the `GET /3/Models/{model_id}/mojo` endpoint.
   * @param readMetadata read additional model metadata (metrics...) if enabled, otherwise skip metadata parsing  
   * @return New `MojoModel` object.
   * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
   */
  public static MojoModel load(String file, boolean readMetadata) throws IOException {
    File f = new File(file);
    MojoReaderBackend cr = resolveBackend(f);
    return ModelMojoReader.readFrom(cr, readMetadata);
  }

  /**
   * Advanced way of constructing Mojo models by supplying a custom mojoReader.
   *
   * @param mojoReader a class that implements the {@link MojoReaderBackend} interface.
   * @return New `MojoModel` object
   * @throws IOException if the mojoReader does
   */
  public static MojoModel load(MojoReaderBackend mojoReader) throws IOException {
    return ModelMojoReader.readFrom(mojoReader);
  }

  /**
   * A method for constructing IMetricBuilder instances.
   *
   * @param file Name of the zip file (or folder) with the model's data. This should be the data retrieved via
   *             the `GET /3/Models/{model_id}/mojo` endpoint.
   * @return New `IMetricBuilder` object.
   * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
   */
  public static IMetricBuilder loadMetricBuilder(String file) throws IOException {
    MojoModel mojoModel = load(file, true);
    return loadMetricBuilder(mojoModel, new File(file));
  }
  
  /**
   * A method for constructing IMetricBuilder instances.
   *
   * @param mojoModel De-serialized mojo model.
   * @param file The zip file (or folder) with the model's data. This should be the data retrieved via
   *             the `GET /3/Models/{model_id}/mojo` endpoint.
   * @return New `IMetricBuilder` object.
   * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
   */
  public static IMetricBuilder loadMetricBuilder(MojoModel mojoModel, File file) throws IOException {
    MojoReaderBackend cr = resolveBackend(file);
    try {
      return ModelMojoReader.readMetricBuilder(mojoModel, cr);
    } finally {
      if (cr instanceof Closeable) ((Closeable) cr).close();
    }
  }
  
  private static MojoReaderBackend resolveBackend(File file) throws IOException {
    if (!file.exists())
      throw new FileNotFoundException("File " + file + " cannot be found.");
    MojoReaderBackend cr = file.isDirectory()? new FolderMojoReaderBackend(file.getAbsolutePath())
            : new ZipfileMojoReaderBackend(file.getAbsolutePath());
    return cr;
  }
  
  //------------------------------------------------------------------------------------------------------------------
  // IGenModel interface
  //------------------------------------------------------------------------------------------------------------------

  @Override public boolean isSupervised() { return _supervised; }
  @Override public int nfeatures() { return _nfeatures; }
  @Override public int nclasses() { return _nclasses; }
  @Override public hex.ModelCategory getModelCategory() { return _category; }

  @Override public String getUUID() { return _uuid; }


  protected MojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }
}
