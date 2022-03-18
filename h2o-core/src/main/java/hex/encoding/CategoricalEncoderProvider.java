package hex.encoding;

public interface CategoricalEncoderProvider {
  String getScheme();

  CategoricalEncoder getEncoder(CategoricalEncodingSupport params);
}
