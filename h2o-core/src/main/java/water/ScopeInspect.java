package water;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;
import java.util.function.Predicate;

/**
 * Some utility functions useful for debugging when trying to find origin of leaked keys.
 */
public class ScopeInspect {
  
  public static String toString(Scope scope) {
    return toString(scope, false, false, false, (k) -> true);
  }
  
  public static String toString(Scope scope, boolean hierarchy, boolean outOfScope, boolean subKeys, Predicate<Key> keyFilter) {
    StringBuilder sb = new StringBuilder();
    Set<Key> scoped = new HashSet<>();
    sb.append("Scope").append(hierarchy ? " hierarchy" : "").append("\n");
    List<Scope.Level> levels = scope.levels();
    for (int i=levels.size()-1; i >= 0; i--) {
      Set<Key> ks = new TreeSet<>(levels.get(i)._keys);
      Set<Key> pks = new TreeSet<>(levels.get(i)._protectedKeys);
      Map<Key, Scope.TrackingInfo> tracks = levels.get(i)._trackingInfo;
      scoped.addAll(ks);
      scoped.addAll(pks);
      if (hierarchy) indent(sb, 1).append("level ").append(i).append(": \n");
      indent(sb, 2).append("tracking ").append(ks.size()).append(" keys:\n");
      for (Key k : ks) {
        String desc = tracks.containsKey(k) ? tracks.get(k)._source : null;
        appendKey(sb, k, 3, desc, subKeys, keyFilter);
      }
      indent(sb, 2).append("protecting ").append(pks.size()).append(" keys:\n");
      for (Key k : pks) {
        String desc = tracks.containsKey(k) ? tracks.get(k)._source : null;
        appendKey(sb, k, 3, desc, subKeys, keyFilter);
      }
      if (!hierarchy) break;
    }
    if (outOfScope) {
      Set<Key> unscoped = new TreeSet<>(new KeysCollector().doAllNodes().keys());
      unscoped.removeAll(scoped);
      sb.append("Keys out of scope:\n");
      for (Key k : unscoped) {
        appendKey(sb, k, 1, null, subKeys, keyFilter);
      }
    }
    return sb.toString();
  }
  
  public static String keysToString(String header, Key... keys) {
    StringBuilder sb = new StringBuilder(header).append(":\n");
    for (Key key : keys) {
      appendKey(sb, key, 1, null, true, (k) -> true);
    }
    return sb.toString();
  }
  
  private static class KeysCollector extends MRTask<KeysCollector> {

    Key[] _collectedKeys;

    @Override
    protected void setupLocal() {
      _collectedKeys = H2O.localKeySet().toArray(new Key[0]);
    }

    @Override
    public void reduce(KeysCollector mrt) {
      Set<Key> ks = keys();
      ks.addAll(mrt.keys());
      _collectedKeys = ks.toArray(new Key[0]);
    }

    public Set<Key> keys() {
      return new HashSet<>(Arrays.asList(_collectedKeys));
    }
  }
  
   private static StringBuilder indent(StringBuilder sb, int numIndent) {
     final int indent = 2;
     for (int i=0; i<numIndent*indent; i++) sb.append(" ");
     return sb;
   }
  
  private static StringBuilder appendKey(StringBuilder sb, Key key, int numIndent, String desc, boolean subKeys, Predicate<Key> keyFilter) {
    if (!keyFilter.test(key)) return sb;
    indent(sb, numIndent).append(key).append(" [").append(key.valueClass()).append(desc == null ? "" : ", "+desc).append("]").append("\n");
    if (!subKeys) return sb;
    if (key.isVec()) {
      Vec v = DKV.getGet(key);
      if (v != null) {
        appendKey(sb, v.rollupStatsKey(), numIndent+1, "rollupstats", false, keyFilter);
        for (int i=0; i<v.nChunks(); i++) {
          appendKey(sb, v.chunkKey(i), numIndent+1, "chunk", false, keyFilter);
        }
      }
    } else if (key.isChunkKey()) {
      appendKey(sb, key.getVecKey(), numIndent+1, "from vec", false, keyFilter);
    } else {
      Value v = DKV.get(key);
      if (v != null && v.isFrame()) {
        Frame fr = v.get();
        if (fr != null) {
          for (int i=0; i<fr.keys().length; i++) {
            Key<Vec> vk = fr.keys()[i];
            appendKey(sb, vk, numIndent+1, "vec_"+i, true, keyFilter);
          }
        }
      }
    }
    return sb;
  }

  public static String dataKeysToString() {
    return toString(Scope.current(), true, true, true, (k) -> {
      boolean ok = k.isVec() || k.isChunkKey();
      if (ok) return true;
      Value v = DKV.get(k);
      if (v != null) return v.isFrame();
      return false;
    });
  }

}
