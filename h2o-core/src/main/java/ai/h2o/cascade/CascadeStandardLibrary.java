package ai.h2o.cascade;

import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNull;
import ai.h2o.cascade.vals.ValNum;

import java.util.HashMap;
import java.util.Map;


/**
 *
 */
public class CascadeStandardLibrary implements ICascadeLibrary {
  private static CascadeStandardLibrary instance;
  private Map<String, Val> members;


  public static CascadeStandardLibrary instance() {
    if (instance == null) instance = new CascadeStandardLibrary();
    return instance;
  }

  public Map<String, Val> members() {
    return members;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  private CascadeStandardLibrary() {
    members = new HashMap<>(100);

    // Constants
    members.put("true", new ValNum(1));
    members.put("True", new ValNum(1));
    members.put("TRUE", new ValNum(1));
    members.put("false", new ValNum(0));
    members.put("False", new ValNum(0));
    members.put("FALSE", new ValNum(0));
    members.put("NaN", new ValNum(Double.NaN));
    members.put("nan", new ValNum(Double.NaN));
    members.put("NA", new ValNum(Double.NaN));
    members.put("null", new ValNull());

    
  }
}
