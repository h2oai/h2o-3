package water.rapids;

import water.rapids.ast.AstPrimitive;

import java.util.*;

/**
 * PrimService manages access to non-core Rapid primitives.
 * This includes algorithm specific rapids & 3rd party rapids.
 */
class PrimsService {

  static PrimsService INSTANCE = new PrimsService();

  private final ServiceLoader<AstPrimitive> _loader;

  private PrimsService() {
    _loader = ServiceLoader.load(AstPrimitive.class);
  }

  /**
   * Locates all available non-core primitives of the Rapid language.
   * @return list of Rapid primitives
   */
  synchronized List<AstPrimitive> getAllPrims() {
    List<AstPrimitive> prims = new ArrayList<>();
    for (AstPrimitive prim : _loader) {
      prims.add(prim);
    }
    return prims;
  }

}
