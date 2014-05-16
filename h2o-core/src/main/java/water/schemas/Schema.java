package water.schemas;

import water.Iced;
import water.H2O;

/** Base Schema Class
 *  
 *  All Schemas inherit from here.  Schemas have a State section (broken into
 *  Input fields and Output fields) and an Adapter section to fill the State to
 *  and from URLs and JSON.  The base Adapter logic is here, and will by
 *  default copy same-named fields to and from Schemas to concrete Iced objects.
 */
public abstract class Schema<I extends Iced,S extends Schema<I,S>> extends Iced {

  public final int getVersion() {
    throw H2O.unimpl();
  }

  public static <I1 extends Iced, S1 extends Schema<I1,S1>> S1 makeSchema(I1 ice) {
    //S1 golden = registry1.get(ice.getClass());
    //S1 foo = golden.clone();
    // foo.override_locally()
    //return foo.fill(ice);
    throw H2O.unimpl();
  }

  public I makeIced(S schema) {
    //I golden = registry2.get(S.getClass());
    //return schema.fillInto(golden.clone());
    throw H2O.unimpl();
  }

}
