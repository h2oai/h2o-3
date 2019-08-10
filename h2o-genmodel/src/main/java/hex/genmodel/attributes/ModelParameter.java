package hex.genmodel.attributes;


import java.io.Serializable;

/**
 * Represents model's parameter within `h2o-genmodel` module
 */
public class ModelParameter implements Serializable {

  public String _name;
  public String _label;
  public String _help;
  public boolean _required;
  public String _type;
  public Object default_value;
  public Object actual_value;
  public String level;
  public String[] values;
  public String[] is_member_of_frames;
  public String[] is_mutually_exclusive_with;
  public boolean gridable;
}
