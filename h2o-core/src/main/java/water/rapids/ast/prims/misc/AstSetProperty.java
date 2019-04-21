package water.rapids.ast.prims.misc;

import water.MRTask;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValStr;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.StringUtils;

/**
 * Internal operator that lets user set a given system property on all nodes of H2O cluster.
 * It is meant for debugging of running clusters and it is not meant to be directly exposed to users.
 */
public class AstSetProperty extends AstBuiltin<AstSetProperty> {

  @Override
  public String[] args() {
    return new String[]{"property", "value"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (setproperty property value)

  @Override
  public String str() {
    return "setproperty";
  }

  @Override
  protected ValStr exec(Val[] args) {
    String property = args[1].getStr();
    String value = args[2].getStr();
    String debugMessage = setClusterProperty(property, value);
    return new ValStr(debugMessage);
  }

  private static String setClusterProperty(String property, String value) {
    String[] oldValues = new SetClusterPropertyTask(property, value).doAllNodes()._oldValues;
    return "Old values of " + property + " (per node): " + StringUtils.join(",", oldValues);
  }

  private static class SetClusterPropertyTask extends MRTask<SetClusterPropertyTask> {
    private String _property;
    private String _value;

    private String[] _oldValues;

    private SetClusterPropertyTask(String property, String value) {
      _property = property;
      _value = value;
      _oldValues = new String[0];
    }

    @Override
    protected void setupLocal() {
      _oldValues = ArrayUtils.append(_oldValues, String.valueOf(System.getProperty(_property)));
      Log.info("Setting property: " + _property + "=" + _value);
      System.setProperty(_property, _value);
    }

    @Override
    public void reduce(SetClusterPropertyTask mrt) {
      _oldValues = ArrayUtils.append(_oldValues, mrt._oldValues);
    }
  }

}