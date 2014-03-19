package water.init;

import water.Iced;
import water.H2O;
import water.TypeMap;
import java.util.*;
import javassist.*;

public class Weaver {
  private static final ClassPool _pool = ClassPool.getDefault();
  private static final CtClass _icer;

  static {
    try { _icer = _pool.get("water.Iced$Icer"); }
    catch( NotFoundException nfe ) {
      throw new RuntimeException(nfe);
    }
  }

  public static <T extends Iced> Iced.Icer<T> genDelegate( int id, Class<T> clazz ) {
    Exception e2;
    try { return (Iced.Icer<T>)javassistLoadClass(id,clazz).toClass().newInstance(); }
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
  private static CtClass javassistLoadClass(int id, Class iced_clazz) throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException {
    // End the super class lookup chain at "water.Iced",
    // returning the known delegate class "water.Iced.Icer".
    String iced_name = iced_clazz.getName();
    if( iced_name.equals("water.Iced") ) return _icer;

    // Now look for a pre-cooked Icer.  No locking, 'cause we're just looking
    String icer_name = implClazzName(iced_name);
    CtClass icer_cc = _pool.getOrNull(icer_name); // Full Name Lookup of Iced.Impl
    if( icer_cc != null ) return icer_cc; // Found a pre-cooked Iced impl

    // Serialize parent.  No locking; occasionally we'll "onIce" from the
    // remote leader more than once.
    Class super_clazz = iced_clazz.getSuperclass();
    int super_id = TypeMap.onIce(super_clazz.getName());
    CtClass super_icer = javassistLoadClass(super_id,super_clazz);
    CtClass iced_cc = _pool.get(iced_name); // Lookup the based Iced class

    // Lock on the Iced class (prevent multiple class-gens of the SAME Iced
    // class, but also to allow parallel class-gens of unrelated Iced).
    synchronized( iced_clazz ) {
      return genIcerClass(id,iced_cc,icer_name,super_id,super_icer);
    }
  }

  // Generate the Icer class
  private static CtClass genIcerClass(int id, CtClass iced_cc, String icer_name, int super_id, CtClass super_icer ) throws CannotCompileException, NotFoundException {
    // Generate the Icer class
    CtClass icer_cc = _pool.makeClass(icer_name);
    icer_cc.setSuperclass(super_icer);

    // 
    make_body(icer_cc, iced_cc,
              "protected final water.AutoBuffer write"+id+"(water.AutoBuffer ab, "+iced_cc.getName()+" ice) {\n",
              "  write"+super_id+"(ab,ice);\n",
              "  ab.put%z(ice.%s);\n",
              "  ab.putEnum(ice.%s);\n",
              "  ab.put%z(ice.%s);\n",
              "",
              "  return ab;\n" +
              "}", null);

    // The generic override method.  Called virtually at the start of a
    // serialization call.  Only calls thru to the named static method.
    String body = "public water.AutoBuffer write(water.AutoBuffer ab, "+icer_name+" ice) {\n"+
      "  return write"+id+"(ab,ice);\n"+
      "}";
    System.out.println("Adding: "+icer_cc.getName()+" "+body);
    addMethod(body,icer_cc);

    return icer_cc;
  }

  // Generate a method body string
  private static void make_body(CtClass icer, CtClass iced,
                                String header,
                                String supers,
                                String prims,
                                String enums,
                                String freezables,
                                String field_sep,
                                String trailer,
                                FieldFilter ff
                                ) throws CannotCompileException, NotFoundException {
    StringBuilder sb = new StringBuilder();
    sb.append(header);
    boolean debug_print = false;
    boolean first = supers==null;
    if( !first ) sb.append(supers);
    // For all fields...
    CtField ctfs[] = iced.getDeclaredFields();
    for( CtField ctf : ctfs ) {
      int mods = ctf.getModifiers();
      if( javassist.Modifier.isTransient(mods) || javassist.Modifier.isStatic(mods) ) {
        debug_print |= ctf.getName().equals("DEBUG_WEAVER");
        continue;  // Only serialize not-transient instance fields (not static)
      }
      if( ff != null && !ff.filter(ctf) ) continue; // Fails the filter
      if( first ) first = false; // Seperator between field lists
      else sb.append(field_sep);

      CtClass base = ctf.getType();
      while( base.isArray() ) base = base.getComponentType();

      int ftype = ftype(iced, ctf.getSignature() );   // Field type encoding
      if( ftype%20 == 9 ) {
        sb.append(freezables);
      } else if( ftype%20 == 10 ) { // Enums
        sb.append(enums);
      } else {
        sb.append(prims);
      }

      String z = FLDSZ1[ftype % 20];
      for(int i = 0; i < ftype / 20; ++i ) z = 'A'+z;
      subsub(sb, "%z", z);                                         // %z ==> short type name
      subsub(sb, "%s", ctf.getName());                             // %s ==> field name
      subsub(sb, "%c", base.getName().replace('$', '.'));          // %c ==> base class name
      subsub(sb, "%C", ctf.getType().getName().replace('$', '.')); // %C ==> full class name

    }
    sb.append(trailer);
    String body = sb.toString();
    if( debug_print )
      System.err.println(icer.getName()+" "+body);
    addMethod(body,icer);
  }

  // Add a gen'd method.  Politely print if there's an error during generation.
  private static void addMethod( String body, CtClass icer_cc ) throws CannotCompileException {
    try {
      icer_cc.addMethod(CtNewMethod.make(body,icer_cc));
    } catch( CannotCompileException ce ) {
      System.err.println("--- Compilation failure while compiling "+icer_cc.getName()+"\n"+body+"\n------");
      throw ce;
    }
  }

  // Arbitrary filter fields.
  private static abstract class FieldFilter {
    abstract boolean filter( CtField ctf ) throws NotFoundException;
  }

  static private final String[] FLDSZ1 = {
    "Z","1","2","2","4","4f","8","8d","Str","","Enum" // prims, String, Freezable, Enum
  };

  // Field types:
  // 0-7: primitives
  // 8,9, 10: String, Freezable, Enum
  // 20-27: array-of-prim
  // 28,29, 30: array-of-String, Freezable, Enum
  // Barfs on all others (eg Values or array-of-Frob, etc)
  private static int ftype( CtClass ct, String sig ) throws NotFoundException {
    switch( sig.charAt(0) ) {
    case 'Z': return 0;         // Booleans: I could compress these more
    case 'B': return 1;         // Primitives
    case 'C': return 2;
    case 'S': return 3;
    case 'I': return 4;
    case 'F': return 5;
    case 'J': return 6;
    case 'D': return 7;
    case 'L':                   // Handled classes
      if( sig.equals("Ljava/lang/String;") ) return 8;

      String clz = sig.substring(1,sig.length()-1).replace('/', '.');
      CtClass argClass = _pool.get(clz);
      if( argClass.subtypeOf(_pool.get("water.Freezable")) ) return 9;
      if( argClass.subtypeOf(_pool.get("java.lang.Enum")) ) return 10;
      break;
    case '[':                   // Arrays
      return ftype(ct, sig.substring(1))+20; // Same as prims, plus 20
    }
    throw barf(ct, sig);
  }

  // Replace 2-byte strings like "%s" with s2.
  static private void subsub( StringBuilder sb, String s1, String s2 ) {
    int idx;
    while( (idx=sb.indexOf(s1)) != -1 ) sb.replace(idx,idx+2,s2);
  }


  private static RuntimeException barf( CtClass ct, String sig ) {
    return new RuntimeException(ct.getSimpleName()+"."+sig+": Serialization not implemented");
  }

}
