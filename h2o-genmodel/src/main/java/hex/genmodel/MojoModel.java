package hex.genmodel;

import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.Table;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.easy.*;
import hex.genmodel.easy.EasyPredictModelWrapper.Config;
import hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer;

import java.io.*;
import java.util.Map;

/**
 * Prediction model based on the persisted binary data.
 */
public abstract class MojoModel extends GenModel implements RowConverterFactory {

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
  public MojoPreprocessor[] _preprocessors;

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

  // RowConverterFactory

  @Override
  public RowToRawDataConverter _makeRowConverter(CategoricalEncoding categoricalEncoding,
                                                 ErrorConsumer errorConsumer,
                                                 Config config) {
    if (_preprocessors != null) {
      RowToRawDataConverter[] converters = new RowToRawDataConverter[_preprocessors.length+1];
      int i = 0;
      GenModel preprocessedModel = this;
      for (MojoPreprocessor preprocessor : _preprocessors) {
        MojoPreprocessor.ModelProcessor processor = preprocessor.makeProcessor(preprocessedModel);
        converters[i] = processor.makeRowConverter(errorConsumer, config);
        preprocessedModel = processor.getProcessedModel();
        i++;
      }
      converters[i] = new CategoricalEncodingAsModelProcessor(preprocessedModel, this, categoricalEncoding).makeRowConverter(errorConsumer, config);
      return new CompositeRowToRawDataConverter<>(converters);
    }
    
    Map<String, Integer> columnToOffsetIdx = categoricalEncoding.createColumnMapping(this);
    Map<Integer, CategoricalEncoder> offsetToEncoder = categoricalEncoding.createCategoricalEncoders(this, columnToOffsetIdx);
    return makeDefaultRowConverter(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
  }
  
  protected RowToRawDataConverter makeDefaultRowConverter(Map<String, Integer> columnToOffsetIdx,
                                                          Map<Integer, CategoricalEncoder> offsetToEncoder,
                                                          ErrorConsumer errorConsumer,
                                                          Config config) {
    return new DefaultRowToRawDataConverter<>(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
  }
}
