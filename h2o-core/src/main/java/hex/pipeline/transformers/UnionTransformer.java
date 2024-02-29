package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import org.apache.commons.lang.StringUtils;
import water.fvec.Frame;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This transformer applies several independent transformers (possibly in parallel) to the same input frame.
 * The results of those transformations to the input frame are then concatenated to produce the result frame, 
 * or possibly appended to the input frame.
 */
public class UnionTransformer extends DataTransformer<UnionTransformer> {
  
  public enum UnionStrategy {
    append, 
    replace
  }
  
  private DataTransformer[] _transformers;
  private UnionStrategy _strategy;


  protected UnionTransformer() {}

  public UnionTransformer(UnionStrategy strategy, DataTransformer... transformers) {
    _strategy = strategy;
    _transformers = transformers;
  }

  @Override
  public Object getParameter(String name) {
    String[] tokens = parseParameterName(name);
    if (tokens.length > 1) {
      String tok0 = tokens[0];
      DataTransformer dt = getTransformer(tok0);
      return dt == null ? null : dt.getParameter(tokens[1]);
    }
    return super.getParameter(name);
  }

  @Override
  public void setParameter(String name, Object value) {
    String[] tokens = parseParameterName(name);
    if (tokens.length > 1) {
      String tok0 = tokens[0];
      DataTransformer dt = getTransformer(tok0);
      if (dt != null) dt.setParameter(tokens[1], value);
      return;
    }
    super.setParameter(name, value);
  }
  
  @Override
  public boolean isParameterAssignable(String name) {
    String[] tokens = parseParameterName(name);
    if (tokens.length > 1) {
      String tok0 = tokens[0];
      DataTransformer dt = getTransformer(tok0);
      return dt != null && dt.hasParameter(tokens[1]);
    }
    return super.isParameterAssignable(name);
  }

  //TODO similar logic as in PipelineParameters: delegate to some kind of ModelParametersAccessor?
  private static final Pattern TRANSFORMER_PAT = Pattern.compile("transformers\\[(\\w+)]");
  private String[] parseParameterName(String name) {
    String[] tokens = name.split("\\.", 2);
    if (tokens.length == 1) return tokens;
    String tok0 = StringUtils.stripStart(tokens[0], "_");
    if (getTransformer(tok0) != null) {
      return new String[]{tok0, tokens[1]} ;
    } else {
      Matcher m = TRANSFORMER_PAT.matcher(tok0);
      if (m.matches()) {
        String id = m.group(1);
        try {
          int idx = Integer.parseInt(id);
          assert idx >=0 && idx < _transformers.length;
          return new String[]{_transformers[idx].name(), tokens[1]};
        } catch(NumberFormatException nfe) {
          if (getTransformer(id) != null) return new String[] {id, tokens[1]};
          throw new IllegalArgumentException("Unknown transformer: "+tok0);
        }
      } else {
        throw new IllegalArgumentException("Unknown parameter: "+name);
      }
    }
  }

  private DataTransformer getTransformer(String id) {
    if (_transformers == null) return null;
    return Stream.of(_transformers).filter(t -> t.name().equals(id)).findFirst().orElse(null);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    Frame result = null;
    switch (_strategy) {
      case append:
        result = new Frame(fr);
        break;
      case replace:
        result = new Frame();
        break;
    }
    for (DataTransformer dt : _transformers) {
      result.add(dt.transform(fr, type, context));
    }
    return result;
  }
  
}
