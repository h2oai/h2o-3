package hex.encoding;

import hex.Model;
import hex.genmodel.ICategoricalEncoding;
import water.H2O;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
import water.nbhm.NonBlockingHashMap;

import java.util.ServiceLoader;

public final class CategoricalEncoding {

  private static final NonBlockingHashMap<String, CategoricalEncoderProvider> providersByName = new NonBlockingHashMap<>();


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
    TargetEncoding(true);

    private final boolean _needResponse;

    Scheme(boolean needResponse) {
      _needResponse = needResponse;
    }

    public boolean needsResponse() {
      return _needResponse;
    }

    public static Scheme fromGenModel(ICategoricalEncoding encoding) {
      if (encoding == null)
        return null;
      try {
        return Enum.valueOf(Scheme.class, encoding.name());
      } catch (IllegalArgumentException iae) {
        throw new UnsupportedOperationException("Unknown encoding "+encoding);
      }
    }
  }


  static class DefaultCategoricalEncoderProvider implements CategoricalEncoderProvider {

    private Scheme _scheme;
    private Class<? extends CategoricalEncoder> _ceClass;
    private boolean _hasParams;

    public DefaultCategoricalEncoderProvider(Scheme scheme, Class<? extends CategoricalEncoder> ceClass, boolean hasParams) {
      _scheme = scheme;
      _ceClass = ceClass;
      _hasParams = hasParams;
    }

    @Override
    public String getScheme() {
      return _scheme.name();
    }

    @Override
    public CategoricalEncoder getEncoder(CategoricalEncodingSupport params) {
      try {
        if (_hasParams)
          return _ceClass.getConstructor(CategoricalEncodingSupport.class).newInstance(params);
        else
          return _ceClass.getConstructor().newInstance();
      } catch (Exception eParams) {
        throw new IllegalStateException(eParams.getMessage());
      }
    }
  }

  static {
    // add statically defined encoders
    CategoricalEncoderProvider[] defaultProviders = {
            //for those, leave as is (no encoding) - most algos do their own internal default handling of enums for example
            new DefaultCategoricalEncoderProvider(Scheme.AUTO, NoopCategoricalEncoder.class, false),
            new DefaultCategoricalEncoderProvider(Scheme.Enum, NoopCategoricalEncoder.class, false),
            new DefaultCategoricalEncoderProvider(Scheme.SortByResponse, NoopCategoricalEncoder.class, false),   //the work is done in ModelBuilder - the domain is all we need to change once, adaptTestTrain takes care of test set adaptation
            new DefaultCategoricalEncoderProvider(Scheme.OneHotInternal, NoopCategoricalEncoder.class, false),
            //for those next ones, the encoding is done by a specific encoder
            new DefaultCategoricalEncoderProvider(Scheme.OneHotExplicit, OneHotCategoricalEncoder.class, false),
            new DefaultCategoricalEncoderProvider(Scheme.Binary, BinaryCategoricalEncoder.class, false),
            new DefaultCategoricalEncoderProvider(Scheme.EnumLimited, EnumLimitedCategoricalEncoder.class, true),
            new DefaultCategoricalEncoderProvider(Scheme.Eigen, EigenCategoricalEncoder.class, true),
            new DefaultCategoricalEncoderProvider(Scheme.LabelEncoder, LabelCategoricalEncoder.class, false),
    };
    for (CategoricalEncoderProvider provider : defaultProviders) {
      providersByName.put(provider.getScheme(), provider);
    }

    // add dynamically loaded encoders
    ServiceLoader<CategoricalEncoderProvider> categoricalEncoderProviders = ServiceLoader.load(CategoricalEncoderProvider.class);
    for (CategoricalEncoderProvider provider : categoricalEncoderProviders) {
      providersByName.put(provider.getScheme(), provider);
    }
  }

  public static CategoricalEncoder getEncoder(Scheme scheme, CategoricalEncodingSupport params) {
    CategoricalEncoderProvider provider = providersByName.get(scheme.name());
    if (provider == null)
      throw new H2OIllegalArgumentException("Categorical encoder `"+scheme+"` is not available.");
    return provider.getEncoder(params);
  }
  
}
