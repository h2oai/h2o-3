package water.init;

import java.util.*;
import javassist.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;
import water.Iced;
import water.Icer;
import water.Freezable;
import water.TypeMap;
import water.H2O.H2OCountedCompleter;
import water.nbhm.UtilUnsafe;

public class Weaver {
  private static final ClassPool _pool = ClassPool.getDefault();
  private static final CtClass _icer, _dtask;
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();

  static {
    try { 
      _icer = _pool.get("water.Icer"); 
      _dtask= _pool.get("water.DTask"); // these also need copyOver
    } catch( NotFoundException nfe ) { throw new RuntimeException(nfe); }
  }

  public static <T extends Freezable> Icer<T> genDelegate( int id, Class<T> clazz ) {
    Exception e2;
    try { 
      T ice = (T)_unsafe.allocateInstance(clazz);
      Class<Icer<T>> icer_clz = javassistLoadClass(id,clazz);
      Constructor<Icer<T>> cstr = (Constructor<Icer<T>>)icer_clz.getDeclaredConstructors()[0];
      return cstr.newInstance(ice);
    }
    catch( InvocationTargetException e ) { e2 = e; }
    catch( InstantiationException    e ) { e2 = e; }
    catch( IllegalAccessException    e ) { e2 = e; }
    catch( NotFoundException         e ) { e2 = e; }
    catch( CannotCompileException    e ) { e2 = e; }
    catch( NoSuchFieldException      e ) { e2 = e; }
    catch( ClassNotFoundException    e ) { e2 = e; }
    throw new RuntimeException(e2);
  }

  // The name conversion from a Iced subclass to an Icer subclass.
  private static String implClazzName( String name ) {
    return name + "$Icer";
  }

  // See if javaassist can find this class, already generated
  private static Class javassistLoadClass(int id, Class iced_clazz) throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException, InvocationTargetException {
    // End the super class lookup chain at "water.Iced",
    // returning the known delegate class "water.Icer".
    String iced_name = iced_clazz.getName();
    if( iced_name.equals("water.Iced") ) return water.Icer.class;
    if( iced_name.equals("water.H2O$H2OCountedCompleter") ) return water.Icer.class;

    // Now look for a pre-cooked Icer.  No locking, 'cause we're just looking
    String icer_name = implClazzName(iced_name);
    CtClass icer_cc = _pool.getOrNull(icer_name); // Full Name Lookup of Icer
    if( icer_cc != null ) return Class.forName(icer_name); // Found a pre-cooked Icer implementation

    // Serialize parent.  No locking; occasionally we'll "onIce" from the
    // remote leader more than once.
    Class super_clazz = iced_clazz.getSuperclass();
    int super_id = TypeMap.onIce(super_clazz.getName());
    Class super_icer_clazz = javassistLoadClass(super_id,super_clazz);
    // After making parent Icer, force its full initialization by making an instance.
    Constructor<Icer> cstr = (Constructor<Icer>)super_icer_clazz.getDeclaredConstructors()[0];
    cstr.newInstance((Iced)null);

    CtClass super_icer_cc = _pool.get(super_icer_clazz.getName());
    CtClass iced_cc = _pool.get(iced_name); // Lookup the based Iced class

    // Lock on the Iced class (prevent multiple class-gens of the SAME Iced
    // class, but also to allow parallel class-gens of unrelated Iced).
    synchronized( iced_clazz ) {
      icer_cc = _pool.getOrNull(icer_name); // Retry under lock
      if( icer_cc != null ) return Class.forName(icer_name); // Found a pre-cooked Icer implementation
      icer_cc = genIcerClass(id,iced_cc,iced_clazz,icer_name,super_id,super_icer_cc);
      return icer_cc.toClass();
    }
  }

  // Generate the Icer class
  private static CtClass genIcerClass(int id, CtClass iced_cc, Class iced_clazz, String icer_name, int super_id, CtClass super_icer ) throws CannotCompileException, NotFoundException, NoSuchFieldException {
    // Generate the Icer class
    String iced_name = iced_cc.getName();
    CtClass icer_cc = _pool.makeClass(icer_name);
    icer_cc.setSuperclass(super_icer);
    icer_cc.setModifiers(javassist.Modifier.PUBLIC);

    // The write call
    String debug = 
    make_body(icer_cc, iced_cc, iced_clazz, "write",
              "protected final water.AutoBuffer write"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              "  write"+super_id+"(ab,ice);\n",
              "  ab.put%z(ice.%s);\n"  ,  "  ab.put%z((%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "  ab.putEnum(ice.%s);\n",  "  ab.putEnum((%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "  ab.put%z(ice.%s);\n"  ,  "  ab.put%z((%C)_unsafe.get%u(ice,%dL)); // %s\n"  ,
              "",
              "  return ab;\n" +
              "}");
    if( debug != null ) {
      System.out.println("class "+icer_cc.getName()+" extends "+super_icer.getName()+" {");
      System.out.println(debug);
    }

    // The generic override method.  Called virtually at the start of a
    // serialization call.  Only calls thru to the named static method.
    String wbody = "water.AutoBuffer write(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "  return write"+id+"(ab,("+iced_name+")ice);\n"+
      "}";
    if( debug != null ) System.out.println(wbody);
    addMethod(wbody,icer_cc);


    // The read call
    String rbody_impl =
    make_body(icer_cc, iced_cc, iced_clazz, "read",
              "protected final "+iced_name+" read"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              "  read"+super_id+"(ab,ice);\n",
              "  ice.%s = ab.get%z();\n",            "  _unsafe.put%u(ice,%dL,ab.get%z());  //%s\n",
              "  ice.%s = %c.raw_enum(ab.get1());\n","  _unsafe.putObject(ice,%dL,%c.raw_enum(ab.get1()));  //%s\n",
              "  ice.%s = (%C)ab.get%z(%c.class);\n","  _unsafe.putObject(ice,%dL,(%C)ab.get%z(%c.class));  //%s\n",
              "",
              "  return ice;\n" +
              "}");
    if( debug != null ) System.out.println(rbody_impl);

    // The generic override method.  Called virtually at the start of a
    // serialization call.  Only calls thru to the named static method.
    String rbody = "water.Freezable read(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "  return read"+id+"(ab,("+iced_name+")ice);\n"+
      "}";
    if( debug != null ) System.out.println(rbody);
    addMethod(rbody,icer_cc);

    String cnbody = "java.lang.String className() { return \""+iced_name+"\"; }";
    if( debug != null ) System.out.println(cnbody);
    addMethod(cnbody,icer_cc);

    String ftbody = "int frozenType() { return "+id+"; }";
    if( debug != null ) System.out.println(ftbody);
    addMethod(ftbody,icer_cc);

    // DTasks need to be able to copy all their (non transient) fields from one
    // DTask instance over another, to match the MRTask API.
    if( iced_cc.subclassOf(_dtask) ) {
      String cpbody_impl =
        make_body(icer_cc, iced_cc, iced_clazz, "copyOver",
                  "void copyOver(water.Freezable fdst, water.Freezable fsrc) {\n",
                  "  super.copyOver(fdst,fsrc);\n"+
                  "  "+iced_name+" dst = ("+iced_name+")fdst;\n"+
                  "  "+iced_name+" src = ("+iced_name+")fsrc;\n",
                  "  dst.%s = src.%s;\n","  _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "  dst.%s = src.%s;\n","  _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "  dst.%s = src.%s;\n","  _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "",
                  "}");
      if( debug != null ) System.out.println(cpbody_impl);
    }

    String cstrbody = "public "+icer_cc.getSimpleName()+"( "+iced_name+" iced) { super(iced); }";
    if( debug != null ) System.out.println(cstrbody);
    try {
      icer_cc.addConstructor(CtNewConstructor.make(cstrbody,icer_cc));
    } catch( CannotCompileException ce ) {
      System.err.println("--- Compilation failure while compiling "+icer_cc.getName()+"\n"+cstrbody+"\n------");
      throw ce;
    }
    if( debug != null ) System.out.println("}");

    return icer_cc;
  }

  // Generate a method body string
  private static String make_body(CtClass icer, CtClass iced_cc, Class iced_clazz, String impl,
                                  String header,
                                  String supers,
                                  String prims,  String prims_unsafe,
                                  String enums,  String enums_unsafe,
                                  String  iced,  String  iced_unsafe,
                                  String field_sep,
                                  String trailer
                                  ) throws CannotCompileException, NotFoundException, NoSuchFieldException {
    StringBuilder sb = new StringBuilder();
    sb.append(header);
    boolean debug_print = false;
    boolean first = supers==null;
    if( !first ) sb.append(supers);
    // Customer serializer?
    String mimpl = impl+"_impl";
    for( CtMethod mth : iced_cc.getDeclaredMethods() ) 
      if( mth.getName().equals(mimpl) ) {
        sb.append("  return ice."+mimpl+"(ab);\n}");
        mimpl = null;           // flag it
        break;
      }
    // For all fields...
    CtField ctfs[] = iced_cc.getDeclaredFields();
    for( CtField ctf : ctfs ) {
      int mods = ctf.getModifiers();
      if( javassist.Modifier.isTransient(mods) || javassist.Modifier.isStatic(mods) ) {
        debug_print |= ctf.getName().equals("DEBUG_WEAVER");
        continue;  // Only serialize not-transient instance fields (not static)
      }
      if( mimpl == null ) continue; // Custom serializer, do not dump fields
      if( first ) first = false; // Separator between field lists
      else sb.append(field_sep);

      CtClass ctft = ctf.getType();
      CtClass base = ctft;
      while( base.isArray() ) base = base.getComponentType();

      long off = !javassist.Modifier.isPublic(mods)
        ? _unsafe.objectFieldOffset(iced_clazz.getDeclaredField(ctf.getName()))
        : -1;
      int ftype = ftype(iced_cc, ctf.getSignature() );   // Field type encoding
      if( ftype%20 == 9 ) {
        sb.append(off==-1 ?  iced :  iced_unsafe);
      } else if( ftype%20 == 10 ) { // Enums
        sb.append(off==-1 ? enums : enums_unsafe);
      } else {
        sb.append(off==-1 ? prims : prims_unsafe);
      }

      String z = FLDSZ1[ftype % 20];
      for(int i = 0; i < ftype / 20; ++i ) z = 'A'+z;
      subsub(sb, "%z", z);                                // %z ==> short type name
      subsub(sb, "%s", ctf.getName());                    // %s ==> field name
      subsub(sb, "%c", base.getName().replace('$', '.')); // %c ==> base class name
      subsub(sb, "%C", ctft.getName().replace('$', '.')); // %C ==> full class name
      subsub(sb, "%d", ""+off);                           // %d ==> field offset
      subsub(sb, "%u", utype(ctf.getSignature()));        // %u ==> unsafe type name

    }
    if( mimpl != null )         // default auto-gen serializer?
      sb.append(trailer);
    String body = sb.toString();
    addMethod(body,icer);
    return debug_print ? body : null;
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

  // Unsafe field access
  private static String utype( String sig ) {
    switch( sig.charAt(0) ) {
    case 'Z': return "Boolean";
    case 'B': return "Byte";
    case 'C': return "Char";
    case 'S': return "Char";
    case 'I': return "Int";
    case 'F': return "Float";
    case 'J': return "Long";
    case 'D': return "Double";
    case 'L': return "Object";
    case '[': return "Object";
    }
    throw new RuntimeException("unsafe access to type "+sig);
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
