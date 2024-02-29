package water;

import javassist.*;
import sun.misc.Unsafe;
import water.api.API;
import water.nbhm.UtilUnsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/** Class to auto-gen serializer delegate classes.  */
public class Weaver {

    /** Get all woven fields in this class, including subclasses, up to the
   *  normal {@link Iced} serialization classes, skipping static and transient
   *  fields, and the required _ice_id field.
   *  @return Array of {@link Field} holding the list of woven fields.
   */
  public static Field[] getWovenFields( Class<?> clz ) {
    ArrayList<Field> flds = new ArrayList<>();
    while( Iced.class.isAssignableFrom(clz) ||
           Freezable.class.isAssignableFrom(clz) ||
           H2O.H2OCountedCompleter.class.isAssignableFrom(clz) ) {
      for( Field f : clz.getDeclaredFields() ) {
        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) ) continue;
        if( "_ice_id".equals(f.getName()) ) continue; // Strip the required typeid field
        flds.add(f);
      }
      clz = clz.getSuperclass();
    }

    return flds.toArray(new Field[0]);
  }


  private static final ClassPool _pool;
  private static final CtClass _dtask, _enum, _serialize;
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();

  static {
    try {
      _pool = ClassPool.getDefault();
      _pool.insertClassPath(new ClassClassPath(Weaver.class));
      _dtask= _pool.get("water.DTask");    // these also need copyOver
      _enum = _pool.get("java.lang.Enum"); // Special serialization
      _serialize = _pool.get("java.io.Serializable"); // Base of serialization

    } catch( NotFoundException nfe ) { throw new RuntimeException(nfe); }
  }


  @SuppressWarnings("unchecked")
  public static <T extends Freezable<T>> Icer<T> genDelegate( int id, Class<T> clazz ) {
    try {
      T ice = Modifier.isAbstract(clazz.getModifiers()) ? null : (T)_unsafe.allocateInstance(clazz);
      Class<?> icer_clz = javassistLoadClass(id,clazz);
      return (Icer<T>)icer_clz.getDeclaredConstructors()[0].newInstance(ice);
    }
    catch( InvocationTargetException | InstantiationException | IllegalAccessException | NotFoundException |
            CannotCompileException | NoSuchFieldException | ClassNotFoundException e) {
      H2O.fail("Fatal error on serialization", e);
      throw new RuntimeException(e);
    }
  }

  // The name conversion from a Iced subclass to an Icer subclass.
  private static String implClazzName( String name ) {
    return name + "$Icer";
  }

  // See if javaassist can find this class, already generated
  private static Class<?> javassistLoadClass(int id, Class<?> iced_clazz) throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException, InvocationTargetException {
    // End the super class lookup chain at "water.Iced",
    // returning the known delegate class "water.Icer".
    String iced_name = iced_clazz.getName();

    assert !iced_name.startsWith("scala.runtime.AbstractFunction");

    // Now look for a pre-cooked Icer.  No locking, 'cause we're just looking
    String icer_name = implClazzName(iced_name);
    CtClass icer_cc = _pool.getOrNull(icer_name); // Full Name Lookup of Icer
    if( icer_cc != null ) {
      synchronized( iced_clazz ) {
        if( !icer_cc.isFrozen() )   icer_cc.toClass(iced_clazz.getClassLoader(), null); // Load class (but does not link & init)
        return Class.forName(icer_name,true,iced_clazz.getClassLoader()); // Found a pre-cooked Icer implementation
      }
    }

    // Serialize parent.  No locking; occasionally we'll "onIce" from the
    // remote leader more than once.
    Class<?> super_clazz = iced_clazz.getSuperclass();
    Class<?> super_icer_clazz;
    int super_id;
    if(Freezable.class.isAssignableFrom(super_clazz)) {
      super_id = TypeMap.onIce(super_clazz.getName());
      super_icer_clazz = javassistLoadClass(super_id, super_clazz);
    } else {
      super_icer_clazz = Icer.class;
      super_id = -1;
    }

    CtClass super_icer_cc = _pool.get(super_icer_clazz.getName());
    CtClass iced_cc = _pool.get(iced_name); // Lookup the based Iced class

    // Lock on the Iced class (prevent multiple class-gens of the SAME Iced
    // class, but also to allow parallel class-gens of unrelated Iced).
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized( iced_clazz ) {
      icer_cc = _pool.getOrNull(icer_name); // Retry under lock
      if( icer_cc != null ) return Class.forName(icer_name); // Found a pre-cooked Icer implementation
      icer_cc = genIcerClass(id,iced_cc,iced_clazz,icer_name,super_id,super_icer_cc);
      icer_cc.toClass(iced_clazz.getClassLoader(), null);               // Load class (but does not link & init)
      return Class.forName(icer_name,true, iced_clazz.getClassLoader()); // Initialize class now, before subclasses
    }
  }

  // Generate the Icer class
  private static CtClass genIcerClass(int id, CtClass iced_cc, Class<?> iced_clazz, String icer_name, int super_id, CtClass super_icer) throws CannotCompileException, NotFoundException, NoSuchFieldException {
    // Generate the Icer class
    String iced_name = iced_cc.getName();
    CtClass icer_cc = _pool.makeClass(icer_name);
    icer_cc.setSuperclass(super_icer);
    icer_cc.setModifiers(javassist.Modifier.PUBLIC);

    // Overall debug printing?
    if (false) {
      System.out.println("Iced class " + icer_cc.getName() + " is number: " + id);
    }

    // Detailed debug printing?
    boolean debug_print=false;
    CtField[] ctfs = iced_cc.getDeclaredFields();
    for( CtField ctf : ctfs ) debug_print |= ctf.getName().equals("DEBUG_WEAVER");
    if( debug_print )
      System.out.println("class "+icer_cc.getName()+" extends "+super_icer.getName()+" {");

    // Make a copy of the enum array, for later deser
    for( CtField ctf : ctfs ) {
      CtClass ctft = ctf.getType();
      String name = ctf.getName();
      int mods = ctf.getModifiers();
      if( javassist.Modifier.isTransient(mods) || javassist.Modifier.isStatic(mods) )
        continue;  // Only serialize not-transient instance fields (not static)
      // Check for enum
      CtClass base = ctft;
      while( base.isArray() ) base = base.getComponentType();

      if( base.subtypeOf(_enum) ) { // either an enum or an array of enum
        // Insert in the Icer, a copy of the enum values() array from Iced
        // e.g. private final myEnum[] _fld = myEnum.values();
        String src = "  private final "+base.getName().replace('$', '.')+"[] "+name+" = "+base.getName().replace('$', '.')+".values();\n";
        if( debug_print ) System.out.println(src);
        CtField ctfr = CtField.make(src,icer_cc);
        icer_cc.addField(ctfr);
      }
    }

    // The write call
    String debug =
    make_body(icer_cc, iced_cc, iced_clazz, "write", null, null,
              "  protected final water.AutoBuffer write"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              super_id == -1?"":"    write"+super_id+"(ab,ice);\n",
              "    ab.put%z(ice.%s);\n"  ,  "    ab.put%z((%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "    ab.put%z(ice.%s);\n"  ,  "    ab.put%z((%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "    ab.put%z(ice.%s);\n"  ,  "    ab.put%z((%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "    return ab;\n" +
              "  }");
    if( debug_print ) System.out.println(debug);
    String debugJ=
    make_body(icer_cc, iced_cc, iced_clazz, "writeJSON", "(supers?ab.put1(','):ab).", "    ab.put1(',').",
              "  protected final water.AutoBuffer writeJSON"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              super_id == -1?"":"    writeJSON"+super_id+"(ab,ice);\n",
              "putJSON%z(\"%s\",ice.%s);\n"  ,  "putJSON%z(\"%s\",(%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "putJSON%z(\"%s\",ice.%s);\n"  ,  "putJSON%z(\"%s\",(%C)_unsafe.get%u(ice,%dL)); // %s\n",
              "putJSON%z(\"%s\",ice.%s);\n"  ,  "putJSON%z(\"%s\",(%C)_unsafe.get%u(ice,%dL)); // %s\n"  ,
              "    return ab;\n" +
              "  }");
    if( debug_print ) System.out.println(debugJ);

    // The generic override method.  Called virtually at the start of a
    // serialization call.  Only calls thru to the named static method.
    String wbody = "  protected water.AutoBuffer write(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "    return write"+id+"(ab,("+iced_name+")ice);\n"+
      "  }";
    if( debug_print ) System.out.println(wbody);
    addMethod(wbody,icer_cc);
    String wbodyJ= "  protected water.AutoBuffer writeJSON(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "    return writeJSON"+id+"(ab.put1('{'),("+iced_name+")ice).put1('}');\n"+
      "  }";
    if( debug_print ) System.out.println(wbodyJ);
    addMethod(wbodyJ,icer_cc);


    // The read call
    String rbody_impl =
    make_body(icer_cc, iced_cc, iced_clazz, "read", null, null,
              "  protected final "+iced_name+" read"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              super_id == -1?"":"    read"+super_id+"(ab,ice);\n",
              "    ice.%s = ab.get%z();\n",            "    _unsafe.put%u(ice,%dL,ab.get%z());  //%s\n",
              "    ice.%s = (%C)ab.get%z(%s);\n",    "    _unsafe.put%u(ice,%dL,ab.get%z(%s));\n",
              "    ice.%s = (%C)ab.get%z(%c.class);\n","    _unsafe.put%u(ice,%dL,(%C)ab.get%z(%c.class));  //%s\n",
              "    return ice;\n" +
              "  }");
    if( debug_print ) System.out.println(rbody_impl);
    String rbodyJ_impl =
    make_body(icer_cc, iced_cc, iced_clazz, "readJSON", null, null,
              "  protected final "+iced_name+" readJSON"+id+"(water.AutoBuffer ab, "+iced_name+" ice) {\n",
              super_id == -1?"":"    readJSON"+super_id+"(ab,ice);\n",
              "    ice.%s = ab.get%z();\n",            "    _unsafe.put%u(ice,%dL,ab.get%z());  //%s\n",
              "    ice.%s = (%C)ab.get%z(%s);\n",    "    _unsafe.put%u(ice,%dL,ab.get%z(%s));\n",
              "    ice.%s = (%C)ab.get%z(%c.class);\n","    _unsafe.put%u(ice,%dL,(%C)ab.get%z(%c.class));  //%s\n",
              "    return ice;\n" +
              "  }");
    if( debug_print )
      System.out.println(rbodyJ_impl);

    // The generic override method.  Called virtually at the start of a
    // serialization call.  Only calls thru to the named static method.
    String rbody = "  protected water.Freezable read(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "    return read"+id+"(ab,("+iced_name+")ice);\n"+
      "  }";
    if( debug_print ) System.out.println(rbody);
    addMethod(rbody,icer_cc);
    String rbodyJ= "  protected water.Freezable readJSON(water.AutoBuffer ab, water.Freezable ice) {\n"+
      "    return readJSON"+id+"(ab,("+iced_name+")ice);\n"+
      "  }";
    if( debug_print ) System.out.println(rbodyJ);
    addMethod(rbodyJ,icer_cc);

    String cnbody = "  protected java.lang.String className() { return \""+iced_name+"\"; }";
    if( debug_print ) System.out.println(cnbody);
    addMethod(cnbody,icer_cc);

    String ftbody = "  protected int frozenType() { return "+id+"; }";
    if( debug_print ) System.out.println(ftbody);
    addMethod(ftbody,icer_cc);

    String cmp2 = "  protected void compute1( water.H2O.H2OCountedCompleter dt ) { dt.compute1(); }";
    if( debug_print ) System.out.println(cmp2);
    addMethod(cmp2,icer_cc);

    // DTasks need to be able to copy all their (non transient) fields from one
    // DTask instance over another, to match the MRTask API.
    if( iced_cc.subclassOf(_dtask) ) {
      String cpbody_impl =
        make_body(icer_cc, iced_cc, iced_clazz, "copyOver", null, null,
                  "  protected void copyOver(water.Freezable fdst, water.Freezable fsrc) {\n",
                  "    super.copyOver(fdst,fsrc);\n"+
                  "    "+iced_name+" dst = ("+iced_name+")fdst;\n"+
                  "    "+iced_name+" src = ("+iced_name+")fsrc;\n",
                  "    dst.%s = src.%s;\n","    _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "    dst.%s = src.%s;\n","    _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "    dst.%s = src.%s;\n","    _unsafe.put%u(dst,%dL,_unsafe.get%u(src,%dL));  //%s\n",
                  "  }");
      if( debug_print ) System.out.println(cpbody_impl);
    }

    String cstrbody = "  public "+icer_cc.getSimpleName()+"( "+iced_name+" iced) { super(iced); }";
    if( debug_print ) System.out.println(cstrbody);
    try {
      icer_cc.addConstructor(CtNewConstructor.make(cstrbody,icer_cc));
    } catch( CannotCompileException ce ) {
      System.err.println("--- Compilation failure while compiling "+icer_cc.getName()+"\n"+cstrbody+"\n------\n"+ce);
      throw ce;
    }
    if( debug_print ) System.out.println("}");

    return icer_cc;
  }

  // Generate a method body string
  private static String make_body(CtClass icer_cc, CtClass iced_cc, Class<?> iced_clazz, String impl, String field_sep1, String field_sep2,
                                  String header,
                                  String supers,
                                  String  prims, String prims_unsafe,
                                  String  enums, String enums_unsafe,
                                  String   iced, String  iced_unsafe,
                                  String trailer
                                  ) throws CannotCompileException, NotFoundException, NoSuchFieldException {
    StringBuilder sb = new StringBuilder();
    sb.append(header);
    if(impl.equals("writeJSON")) {
      if (supers.isEmpty()) {
        sb.append("  boolean supers = false;");
      } else {
        sb.append("  int position = ab.position();\n");
        sb.append(supers);
        sb.append("  boolean supers = ab.position() != position;\n");
      }
    } else
      sb.append(supers);
    // Customer serializer?
    String mimpl = impl+"_impl";


    for( CtMethod mth : iced_cc.getDeclaredMethods() )
      if( mth.getName().equals(mimpl) ) { // Found custom serializer?
        int mods = mth.getModifiers();
        String ice_handle;
        String ice_args;
        if(javassist.Modifier.isStatic(mods)) {
          ice_handle = iced_clazz.getName() + ".";
          ice_args = "(ice,ab)";
        } else if(javassist.Modifier.isFinal(mods)) {
          ice_handle = "ice.";
          ice_args = "(ab)";
        }else if(javassist.Modifier.isAbstract(mods)){
          ice_handle = null;
          ice_args = null;
        } else
            throw barf(iced_cc," Custom serialization methods must be declared either static or final. Failed for method " + mimpl);
        // If the custom serializer is actually abstract, then do nothing - it
        // must be (re)implemented in all child classes which will Do The Right Thing.
        if( javassist.Modifier.isAbstract(mods) || javassist.Modifier.isVolatile(mods) )
          sb.append(impl.startsWith("write") ? "    return ab;\n  }" : "    return ice;\n  }");
        else {
          if (!supers.isEmpty() && impl.equals("writeJSON")) {
            sb.append("    if(supers) {\n");
            sb.append("       ab.put1(',');\n");
            sb.append("       int pos = ab.position();\n");
            sb.append("    ").append(ice_handle).append(mimpl).append(ice_args).append(";\n");
            sb.append("      if(ab.position() == pos) ab.position(pos-1);\n"); // empty json serialization, drop the comma
            sb.append("      return ab;\n    } \n");
          }
          sb.append("    return ").append(ice_handle).append(mimpl).append(ice_args).append(";\n  }");
        }
        mimpl = null;           // flag it
        break;
      }
    // For all fields...
    CtField[] ctfs = iced_cc.getDeclaredFields();
    for( CtField ctf : ctfs ) {
      if( mimpl == null ) break; // Custom serializer, do not dump fields
      int mods = ctf.getModifiers();
      if( javassist.Modifier.isTransient(mods) || javassist.Modifier.isStatic(mods) )
        continue;  // Only serialize not-transient instance fields (not static)
      if (ctf.hasAnnotation(API.class))
        if(!((API)ctf.getAvailableAnnotations()[0]).json())
          continue;
      if( field_sep1 != null ) { sb.append(field_sep1); field_sep1 = null; }
      else if( field_sep2 != null ) sb.append(field_sep2);

      CtClass ctft = ctf.getType();
      CtClass base = ctft;
      while( base.isArray() ) base = base.getComponentType();

      // Can the generated code access the field?  If not - use Unsafe.  If so,
      // use the fieldname (ldX bytecode) directly.  Genned code is in the same
      // package, so public,protected and package-private all have sufficient
      // access, only private is a problem.
      boolean can_access = !javassist.Modifier.isPrivate(mods);
      if( (impl.equals("read") || impl.equals("copyOver")) && javassist.Modifier.isFinal(mods) ) can_access = false;
      long off = _unsafe.objectFieldOffset(iced_clazz.getDeclaredField(ctf.getName()));
      int ftype = ftype(iced_cc, ctf.getSignature() ); // Field type encoding
      if( ftype%20 == 9 || ftype%20 == 11 ) {          // Iced/Objects
        sb.append(can_access ?  iced :  iced_unsafe);
      } else if( ftype%20 == 10 ) { // Enums
        sb.append(can_access ? enums : enums_unsafe);
      } else {                      // Primitives
        sb.append(can_access ? prims : prims_unsafe);
      }

      String z = FLDSZ1[ftype % 20];
      for(int i = 0; i < ftype / 20; ++i ) z = 'A'+z;
      subsub(sb, "%z", z);                         // %z ==> short type name
      subsub(sb, "%s", ctf.getName());             // %s ==> field name
      subsub(sb, "%c", dollarsub(base.getName())); // %c ==> base class name
      subsub(sb, "%C", dollarsub(ctft.getName())); // %C ==> full class name
      subsub(sb, "%d", ""+off);                    // %d ==> field offset, only for Unsafe
      subsub(sb, "%u", utype(ctf.getSignature())); // %u ==> unsafe type name

    }
    if( mimpl != null )         // default auto-gen serializer?
      sb.append(trailer);
    String body = sb.toString();
    addMethod(body,icer_cc);
    return body;
  }

  // Add a gen'd method.  Politely print if there's an error during generation.
  private static void addMethod( String body, CtClass icer_cc ) throws CannotCompileException {
    try {
      icer_cc.addMethod(CtNewMethod.make(body,icer_cc));
    } catch( CannotCompileException ce ) {
      System.err.println("--- Compilation failure while compiling "+icer_cc.getName()+"\n"+body+"\n------\n"+ce);
      throw ce;
    } catch ( RuntimeException re ) { // NotFoundException is wrapped in RE
      System.err.println("--- Failure while compiling "+icer_cc.getName()+"\n"+body+"\n------\n"+re);
      throw re;
    }
  }

  static private final String[] FLDSZ1 = {
    "Z","1","2","2","4","4f","8","8d", // Primitives
    "Str","","Enum",                   // String, Freezable, Enum
    "Ser"                              // java.lang.Serializable
  };

  // Field types:
  // 0-7: primitives
  // 8,9, 10: String, Freezable, Enum
  // 11: Java serialized object (implements Serializable)
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
      if( argClass.subtypeOf(_enum) ) return 10;
      if( argClass.subtypeOf(_serialize) ) return 11; // Uses Java Serialization
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
    case 'C':
    case 'S': return "Char";
    case 'I': return "Int";
    case 'F': return "Float";
    case 'J': return "Long";
    case 'D': return "Double";
    case 'L':
    case '[': return "Object";
    }
    throw new RuntimeException("unsafe access to type "+sig);
  }

  // Replace the 1st '$' with '.'
  static private String dollarsub( String s ) {
    int idx = s.indexOf('$');
    return idx == -1 ? s : (s.substring(0,idx)+"."+s.substring(idx+1));
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

