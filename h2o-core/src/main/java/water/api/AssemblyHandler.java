package water.api;

import water.DKV;
import water.Key;
import water.rapids.Assembly;
import water.rapids.transforms.Transform;
import water.fvec.Frame;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class AssemblyHandler extends Handler {
  public AssemblyV99 fit(int version, AssemblyV99 ass) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if( ass==null ) return null;
    if( ass.steps == null ) return ass;
    // process assembly:
    //   of the form [name__class__ast__inplace, name__class__ast__inplace, ...]
    // s[0] : stepName
    // s[1] : transform class
    // s[2] : ast (can be noop)
    // s[3] : inplace
    ArrayList<Transform> steps = new ArrayList<>();
    for(String step: ass.steps) {
      String[] s = step.split("__");
      Class transformClass = Class.forName("water.rapids.transforms."+s[1]);
      Class[] constructorTypes = new Class[]{String.class /*name*/, String.class /*ast*/, boolean.class /*inplace*/};
      Object[] constructorArgs = new Object[]{s[0], s[2], Boolean.valueOf(s[3])};
      steps.add((Transform) transformClass.getConstructor(constructorTypes).newInstance(constructorArgs));
    }
    Assembly assembly = new Assembly(Key.make("assembly_"+Key.make().toString()), steps.toArray(new Transform[steps.size()]));
    ass.result = new KeyV3.FrameKeyV3(assembly.fit((Frame)DKV.getGet(ass.frame.key()))._key);
    ass.assembly = new KeyV3.AssemblyKeyV3(assembly._key);
    DKV.put(assembly);
    return ass;
  }

  public AssemblyV99 toJava(int version, AssemblyV99 ass) { return ass; }
}
