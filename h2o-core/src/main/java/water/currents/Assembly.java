package water.currents;

import water.currents.transforms.Transform;
import water.fvec.Frame;

import java.util.HashMap;

/**
 * Assemblies are essentially Pipelines.
 */

// TODO: add in/out col names, in/out col types
public class Assembly {
  HashMap<String, Transform> _steps;
  Assembly(HashMap<String, Transform> steps) { _steps = steps; }

  String[] names() {
    return _steps.keySet().toArray(new String[_steps.size()]);
  }

  Transform[] steps() {
    return _steps.values().toArray(new Transform[_steps.size()]);
  }

  HashMap<String, HashMap<String,Object>> getParams(boolean deep) {
    HashMap<String, HashMap<String, Object>> out = new HashMap<>();
    for (String step : _steps.keySet())
      out.put(step, _steps.get(step).getParams(deep));
    return out;
  }

  public Frame applyTransforms(Frame f) {
    for(String step:_steps.keySet()) {
      f = _steps.get(step).fitTransform(f);
    }
    return f;
  }

  public Frame fit(Frame f) {
    return applyTransforms(f);
  }

  public StringBuilder toJava(String pojoName) {
    if( pojoName==null ) pojoName = "GeneratedMungingPojo";
    StringBuilder sb = new StringBuilder(
            "class " + pojoName + " extends GenMunger {\n"+
            "  public " + pojoName + "() {\n"+
            "    _steps = new Step[" + _steps.size() + "];\n"
    );
    int i=0;
    for(String stepName: _steps.keySet())
      sb.append("    _steps["+(i++)+"] = new "+stepName+"();\n");
    sb.append("  }\n");
    for(String stepName: _steps.keySet())
      sb.append(_steps.get(stepName).genClass(stepName));
    sb.append("}\n");
    return sb;
  }
}
