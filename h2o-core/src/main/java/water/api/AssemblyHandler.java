package water.api;

import water.DKV;
import water.currents.Assembly;
import water.currents.transforms.Transform;
import water.fvec.Frame;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class AssemblyHandler extends Handler {
  public AssemblyV99 fit(int version, AssemblyV99 ass) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if( ass==null ) return null;
    if( ass.assembly == null ) return ass;
    // process assembly:
    //   of the form [name__class__ast__inplace, name__class__ast__inplace, ...]
    // s[0] : stepName
    // s[1] : transform class
    // s[2] : ast (can be noop)
    // s[3] : inplace
    ArrayList<Transform> steps = new ArrayList<>();
    for(String step: ass.assembly) {
      String[] s = step.split("__");
      Class transformClass = Class.forName("water.currents.transforms."+s[1]);
      Class[] constructorTypes = new Class[]{String.class /*name*/, String.class /*ast*/, boolean.class /*inplace*/};
      Object[] constructorArgs = new Object[]{s[0], s[2], Boolean.valueOf(s[3])};
      steps.add((Transform) transformClass.getConstructor(constructorTypes).newInstance(constructorArgs));
    }
    Assembly assembly = new Assembly(steps.toArray(new Transform[steps.size()]));
    ass.result = assembly.fit((Frame)DKV.getGet(ass.frame.key()))._key.toString();
    return ass;
  }

//  public AssemblyV99 toJava(int version, AssemblyV99 ass) {
//    return ass;
//  }
}
