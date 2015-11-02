package water.rapids;

import water.H2O;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.rapids.transforms.Transform;

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

  public Frame fit(Frame f) {
    for(Transform step: _steps)
      f = step.fitTransform(f);
    return f;
  }

  public String toJava(String pojoName) {
    if( pojoName==null ) pojoName = "GeneratedMungingPojo";
    StringBuilder sb = new StringBuilder(
            "import hex.genmodel.GenMunger;\n"+
            "import hex.genmodel.easy.RowData;\n\n" +
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
