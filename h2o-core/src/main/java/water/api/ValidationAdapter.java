package water.api;

import hex.Model;
import water.fvec.Frame;
import water.fvec.Vec;

public class ValidationAdapter {

//  public ValidationAdapter(Frame validation, boolean classification) {
//    this.validation = validation;
//    this.classification = classification;
//  }
//
//  /** Validation frame */
//  final private transient Frame validation;
//  /** Whether classification is done or not */
//  final private transient boolean classification;
//
//  /** Validation vector extracted from validation frame. */
//  private transient Vec _validResponse;
//  /** Validation response domain or null if validation is not specified or null if response is float. */
//  private transient String[] _validResponseDomain;
//  /** Source response domain or null if response is float. */
//  private transient String[] _sourceResponseDomain;
//  /** CM domain derived from {@link #_validResponseDomain} and {@link #_sourceResponseDomain}. */
//  private transient String[] _cmDomain;
//  /** Names of columns */
//  private transient String[] _names;
//  /** Name of validation response. Should be same as source response. */
//  private transient String _responseName;
//
//  /** Adapted validation frame to a computed model. */
//  private transient Frame _adaptedValidation;
//  private transient Vec   _adaptedValidationResponse; // Validation response adapted to computed CM domain
//  private transient int[][] _fromModel2CM;            // Transformation for model response to common CM domain
//  private transient int[][] _fromValid2CM;            // Transformation for validation response to common CM domain
//
//  /** Returns true if the job has specified validation dataset. */
//  private final boolean  hasValidation() { return validation!=null; }
//  /** Returns a domain for confusion matrix. */
//  private final String[] getCMDomain() { return _cmDomain; }
//  /** Return validation dataset which can be adapted to a model if it is necessary. */
//  public  final Frame    getValidation() { return _adaptedValidation!=null ? _adaptedValidation : validation; }
//  /** Returns original validation dataset. */
//  private final Frame    getOrigValidation() { return validation; }
//  public  final Response2CMAdaptor getValidAdaptor() { return new Response2CMAdaptor(); }
//
//  /** */
//  public final void prepareValidationWithModel(final Model model) {
//    if (validation == null) return;
//    Frame[] av = model.adapt(validation, false);
//    _adaptedValidation = av[0];
////    gtrash(av[1]); // delete this after computation
//    if (_fromValid2CM!=null) {
//      assert classification : "Validation response transformation should be declared only for classification!";
//      assert _fromModel2CM != null : "Model response transformation should exist if validation response transformation exists!";
//      Vec tmp = _validResponse.toCategoricalVec();
//      _adaptedValidationResponse = tmp.makeTransf(_fromValid2CM, getCMDomain()); // Add an original response adapted to CM domain
////      gtrash(_adaptedValidationResponse); // Add the created vector to a clean-up list
////      gtrash(tmp);
//    }
//  }
//
//  /** A micro helper for transforming model/validation responses to confusion matrix domain. */
//  public class Response2CMAdaptor {
//    /** Adapt given vector produced by a model to confusion matrix domain. Always return a new vector which needs to be deleted. */
//    public  Vec adaptModelResponse2CM(final Vec v) { return  v.makeTransf(_fromModel2CM, getCMDomain()); }
//    /** Adapt given validation vector to confusion matrix domain. Always return a new vector which needs to be deleted. */
//    private Vec adaptValidResponse2CM(final Vec v) { return  v.makeTransf(_fromValid2CM, getCMDomain()); }
//    /** Returns validation dataset. */
//    private Frame getValidation() { return getValidation(); }
//    /** Return cached validation response already adapted to CM domain. */
//    public  Vec getAdaptedValidationResponse2CM() { return _adaptedValidationResponse; }
//    /** Return cm domain. */
//    private String[] getCMDomain() { return getCMDomain(); }
//    /** Returns true if model/validation responses need to be adapted to confusion matrix domain. */
//    public  boolean needsAdaptation2CM() { return _fromModel2CM != null; }
//    /** Return the adapted response name */
//    public  String adaptedValidationResponse(final String response) { return response + ".adapted"; }
//  }
}
