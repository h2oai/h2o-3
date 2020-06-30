package hex.genmodel.attributes.parameters;

import java.io.Serializable;

public class ModelParameter implements Serializable {

  public String name;
  public String label;
  public String help;
  public boolean required;
  public String type;
  public Object default_value;
  public Object actual_value;
  public Object input_value;
  public String level;
  public String[] values;
  public String[] is_member_of_frames;
  public String[] is_mutually_exclusive_with;
  public boolean gridable;

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  public String getHelp() {
    return help;
  }

  public boolean isRequired() {
    return required;
  }

  public String getType() {
    return type;
  }

  public Object getDefaultValue() {
    return default_value;
  }

  public Object getActualValue() {
    return actual_value;
  }
  
  public Object getInputValue() {
    return input_value;
  }

  public String getLevel() {
    return level;
  }

  public String[] getValues() {
    return values;
  }

  public String[] getIsMemberOfFrames() {
    return is_member_of_frames;
  }

  public String[] getIsMutuallyExclusiveWith() {
    return is_mutually_exclusive_with;
  }

  public boolean isGridable() {
    return gridable;
  }
}
