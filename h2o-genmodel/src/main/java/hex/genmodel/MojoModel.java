package hex.genmodel;

import java.io.*;


/**
 * Prediction model based on the persisted binary data.
 */
public abstract class MojoModel extends GenModel {

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

  /**
   * Primary factory method for constructing MojoModel instances.
   *
   * @param file Name of the zip file (or folder) with the model's data. This should be the data retrieved via
   *             the `GET /3/Models/{model_id}/mojo` endpoint.
   * @return New `MojoModel` object.
   * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
   */
  public static MojoModel load(String file) throws IOException {
    File f = new File(file);
    if (!f.exists())
      throw new FileNotFoundException("File " + file + " cannot be found.");
    MojoReaderBackend cr = f.isDirectory()? new FolderMojoReaderBackend(file)
                                          : new ZipfileMojoReaderBackend(file);
    return ModelMojoReader.readFrom(cr);
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
