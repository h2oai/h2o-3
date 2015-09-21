package water.rapids;

import water.H2O;
import water.Key;
import water.Keyed;
import water.rapids.transforms.Transform;
import water.fvec.Frame;

import java.util.HashMap;

/**
 * Assemblies are essentially Pipelines.
 */

// TODO: add in/out col names, in/out col types
public class Assembly extends Keyed<Assembly> {
  private Transform[] _steps;
  public Assembly(Key key, Transform[] steps) { super(key); _steps = steps; }

  String[] names() {
    String[] names = new String[_steps.length];
    for(int i=0;i<names.length;++i) names[i] = _steps[i].name();
    return names;
  }

  Transform[] steps() { return _steps; }

  HashMap<String, HashMap<String,ASTParameter>> getParams(boolean deep) {
    HashMap<String, HashMap<String, ASTParameter>> out = new HashMap<>();
    for (Transform step: _steps)
      out.put(step.name(), step.getParams(deep));
    return out;
  }

  public Frame applyTransforms(Frame f) {
    for(Transform step: _steps)
      f = step.fitTransform(f);
    return f;
  }

  public Frame fit(Frame f) { return applyTransforms(f); }

  public String toJava(String pojoName) {
    if( pojoName==null ) pojoName = "GeneratedMungingPojo";
    StringBuilder sb = new StringBuilder(
            "class " + pojoName + " extends GenMunger {\n"+
            "  public " + pojoName + "() {\n"+
            "    _steps = new Step[" + _steps.length + "];\n"
    );
    int i=0;
    for(Transform step: _steps)
      sb.append("    _steps["+(i++)+"] = new "+step.name()+"();\n");
    sb.append("  }\n");
    for(Transform step: _steps)
      sb.append(step.genClass());
    sb.append("}\n");
    return sb.toString();
  }

  @Override protected long checksum_impl() {
    throw H2O.unimpl();
  }
}
