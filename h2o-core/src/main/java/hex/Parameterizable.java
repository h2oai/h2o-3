package hex;

public interface Parameterizable {
  
  boolean hasParameter(String name);
  
  Object getParameter(String name);
  
  void setParameter(String name, Object value);
  
  boolean isParameterSetToDefault(String name);
  
  Object getParameterDefaultValue(String name);
  
  boolean isValidHyperParameter(String name);
}
