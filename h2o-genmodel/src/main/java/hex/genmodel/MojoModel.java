package hex.genmodel;

import hex.ModelCategory;
import hex.genmodel.descriptor.VariableImportances;

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
  
  protected class MojoModelDescriptor implements ModelDescriptor{
    //TODO: Investigate if we should expose these internal structures like that (make a local copy ?)
    @Override
    public String[][] scoringDomains() {
      return _domains;
    }

    @Override
    public String projectVersion() {
      return _h2oVersion;
    }

    @Override
    public String algoName() {
      return getClass().getName();
    }

    @Override
    public String algoFullName() {
      return getClass().getName();
    }

    @Override
    public String offsetColumn() {
      return _offsetColumn;
    }

    @Override
    public String weightsColumn() {
      return null;
    }

    @Override
    public String foldColumn() {
      return null;
    }

    @Override
    public ModelCategory getModelCategory() {
      return _category;
    }

    @Override
    public boolean isSupervised() {
      return _supervised;
    }

    @Override
    public int nfeatures() {
      return _nfeatures;
    }

    @Override
    public int nclasses() {
      return _nclasses;
    }

    @Override
    public String[] columnNames() {
      return _names;
    }

    @Override
    public boolean balanceClasses() {
      return _balanceClasses;
    }

    @Override
    public double defaultThreshold() {
      return _defaultThreshold;
    }

    @Override
    public double[] priorClassDist() {
      return _priorClassDistrib;
    }

    @Override
    public double[] modelClassDist() {
      return _modelClassDistrib;
    }

    @Override
    public String uuid() {
      return _uuid;
    }

    @Override
    public String timestamp() {
      return null;
    }

    @Override
    public VariableImportances variableImportances() {
      return null;
    }
  }

  public ModelDescriptor modelDescriptor() {
     return new MojoModelDescriptor();
  }


}
