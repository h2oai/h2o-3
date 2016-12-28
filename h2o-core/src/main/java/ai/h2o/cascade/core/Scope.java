package ai.h2o.cascade.core;

import ai.h2o.cascade.CascadeSession;
import ai.h2o.cascade.vals.Val;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical scope for the Cascade execution environment.
 *
 * <p>Each scope contains a symbol table (an id->value map), and a reference
 * to the parent scope. The job of a scope is to keep track of what variables
 * are currently defined, and what their values are. If some variable is not
 * found in the current scope, we will search the parent scope.
 *
 * <p>Cascade has a single global scope bound to a session. Additionally,
 * new scopes may be opened by certain commands, such as {@code for},
 * {@code def} and {@code scope}.
 *
 * <hr/>
 *
 * <p>In addition to resolving variables, scope also provides a mechanism for
 * copy-on-write (COW) optimizations. This mechanism is not automatic, and
 * requires a certain amount of cooperation from the authors of stdlib
 * functions. In particular,
 * <ul>
 *   <li>Any function that intends to reuse a vec from some other frame, should
 *       register the newly created frame with
 *       {@code #registerReuse(WorkFrame, SliceList)}.</li>
 * </ul>
 *
 */
public class Scope {
  private Scope parent;
  private CascadeSession session;
  private Map<String, Val> symbolTable;


  public Scope(CascadeSession sess, Scope parentScope) {
    session = sess;
    parent = parentScope;
    symbolTable = new HashMap<>(5);
    vecCopyCounts = new HashMap<>(16);
  }


  public CascadeSession session() {
    return session;
  }


  public Val lookupVariable(String id) {
    if (symbolTable.containsKey(id))
      return symbolTable.get(id);
    if (parent != null)
      return parent.lookupVariable(id);
    throw new IllegalArgumentException("Name lookup of " + id + " failed");
  }


  public void importFromLibrary(ICascadeLibrary lib) {
    symbolTable.putAll(lib.members());
  }


  public void addVariable(String name, Val value) {
    symbolTable.put(name, value);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // COW optimizations
  //--------------------------------------------------------------------------------------------------------------------
  private Map<Vec, Integer> vecCopyCounts;

  /**
   * I, the caller of this function, do hereby proclaim that I have created
   * a new {@link WorkFrame} {@code frame}, and in doing so I have used vecs from
   * other {@link Frame}s or {@code WorkFrame}s, as given in the {@code columns}
   * list. I shall hereby be held harmless from any data loss that may result
   * from my future use of the new {@code frame}, provided such use is
   * restricted to read access only.
   *
   * @param frame
   * @param columns
   */
  public void registerReuse(WorkFrame frame, SliceList columns) {
    if (columns == null)
      columns = new SliceList(0, frame.nCols());
    if (!frame.isStoned())
      throw new RuntimeException("Direct reuse of ghost WorkFrame is not supported");
    Frame f = frame.getStoneFrame();
    SliceList.Iterator iter = columns.iter();
    while (iter.hasNext()) {
      Vec v = f.vec((int) iter.nextPrim());
      Integer count = vecCopyCounts.get(v);
      vecCopyCounts.put(v, (count == null? 0 : count) + 1);
    }
  }

  public void intendToModify(WorkFrame frame, SliceList columns) {

  }


}
