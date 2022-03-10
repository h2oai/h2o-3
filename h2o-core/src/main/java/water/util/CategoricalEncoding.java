package water.util;

import hex.Model;
import hex.genmodel.ICategoricalEncoding;
import water.H2O;

public final class CategoricalEncoding {
    
    public enum Scheme {
      AUTO(false),
      OneHotInternal(false),
      OneHotExplicit(false),
      Enum(false),
      Binary(false),
      Eigen(false),
      LabelEncoder(false),
      SortByResponse(true),
      EnumLimited(false),
      TargetEncoding(true)
      ;

      private final boolean _needResponse;
      
      Scheme(boolean needResponse) { _needResponse = needResponse; }
      
      public boolean needsResponse() { return _needResponse; }
      
      public static Scheme fromGenModel(ICategoricalEncoding encoding) {
        if (encoding == null)
          return null;
        try {
            return Enum.valueOf(Scheme.class, encoding.name());
        } catch (IllegalArgumentException iae) {
            throw new UnsupportedOperationException("Unknown encoding " + encoding);
        }
      }
    }
    
    
  public static CategoricalEncoder newEncoder(Scheme scheme, Model.AdaptFrameParameters params) {
    switch (scheme) {
      case AUTO:
      case Enum:
      case SortByResponse: //the work is done in ModelBuilder - the domain is all we need to change once, adaptTestTrain takes care of test set adaptation
      case OneHotInternal:
        return new CategoricalEncoders.NoopEncoder(); //leave as is - most algos do their own internal default handling of enums
      case OneHotExplicit:
        return new CategoricalEncoders.CategoricalOneHotEncoder();
      case Binary:
        return new CategoricalEncoders.CategoricalBinaryEncoder();
      case EnumLimited:
        return new CategoricalEncoders.CategoricalEnumLimitedEncoder(params);
      case Eigen:
        return new CategoricalEncoders.CategoricalEigenEncoder(params);
      case LabelEncoder:
        return new CategoricalEncoders.CategoricalLabelEncoder();
      case TargetEncoding:
        return new CategoricalEncoders.NoopEncoder();
      default:
        throw H2O.unimpl();
    }
  }
}
