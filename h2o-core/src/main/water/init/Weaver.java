package water.init;

import water.Iced;
import water.H2O;
import water.TypeMap;
import java.util.*;
import javassist.*;

public class Weaver {
  private static final ClassPool _pool = ClassPool.getDefault();

  public static <T extends Iced> Iced.Icer<T> genDelegate( int id, Class<T> clazz ) {
    Exception e2;
    try { return javassistLoadClass(id,clazz); }
    catch( InstantiationException e ) { e2 = e; }
    catch( IllegalAccessException e ) { e2 = e; }
    catch( NotFoundException      e ) { e2 = e; }
    catch( CannotCompileException e ) { e2 = e; }
    throw new RuntimeException(e2);
  }

  // The name conversion from a Iced subclass to an Iced.Impl subclass.
  private static String implClazzName( String name ) {
    return name + "$Icer";
  }

  // See if javaassist can find this class, already generated
  private static <T extends Iced> Iced.Icer<T> javassistLoadClass(int id, Class<T> clazz) throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException {
    // End the super class lookup chain at "water.Iced",
    // returning an instance of the known delegate class "water.Iced.Icer".
    String name = clazz.getName();
    if( name.equals("water.Iced") ) return (Iced.Icer<T>)Iced.ICER;

    // Now look for a pre-cooked Icer.  No locking, 'cause we're just looking
    String icer_name = implClazzName(name);
    CtClass cci = _pool.getOrNull(icer_name); // Full Name Lookup of Iced.Impl
    if( cci != null ) 
      return (Iced.Icer<T>)cci.toClass().newInstance(); // Found a pre-cooked Iced impl

    // Serialize parent.  No locking; occasionally we'll "onIce" from the
    // remote leader more than once.
    Class super_clazz = clazz.getSuperclass();
    int super_id = TypeMap.onIce(super_clazz.getName());
    javassistLoadClass(super_id,super_clazz);

    CtClass cc = _pool.get(name); // Lookup the based Iced class

    // Lock on the Iced class (prevent multiple class-gens of the SAME Iced
    // class, but also to allow parallel class-gens of unrelated Iced).
    synchronized( clazz ) {

    }

    //
    //// Serialize enums first, since we need the raw_enum function for this class
    //for( CtField ctf : cc.getDeclaredFields() ) {
    //  CtClass base = ctf.getType();
    //  while( base.isArray() ) base = base.getComponentType();
    //  if( base.subclassOf(_enum) && base != cc )
    //    javassistLoadClass(base);
    //}
    //return addSerializationMethods(cc);
    throw H2O.unimpl();
  }
}
