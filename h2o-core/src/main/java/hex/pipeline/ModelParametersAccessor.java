package hex.pipeline;

import hex.Parameterizable;

public final class ModelParametersAccessor implements Parameterizable {

  @Override
  public boolean hasParameter(String name) {
    return false;
  }

  @Override
  public Object getParameter(String name) {
    return null;
  }

  @Override
  public void setParameter(String name, Object value) {}

  @Override
  public boolean isParameterSetToDefault(String name) {
    return false;
  }

  @Override
  public Object getParameterDefaultValue(String name) {
    return null;
  }

  @Override
  public boolean isValidHyperParameter(String name) {
    return false;
  }
}
