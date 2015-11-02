package water.fvec;


import hex.Interaction;
import jsr166y.CountedCompleter;
import water.*;
import water.util.IcedHashMap;
import water.util.IcedLong;

import java.util.*;

/**
 * Helper to create interaction features between categorical columns
 */
public class CreateInteractions extends H2O.H2OCountedCompleter {

  public CreateInteractions(Interaction ci, Key job) { super(null); _job = job; _ci = ci; }

  final private Interaction _ci;

  static final private int _missing = Integer.MIN_VALUE; //marker for missing factor level
  static final private String _other = "other"; // name for lost factor levels

  private Frame _target;
  final private Key _job;

  private Map<Long, Long> _sortedMap = null;

  private static Map<Long, Long> mySort(Map<IcedLong, IcedLong> unsortMap) {
    List<Map.Entry<IcedLong, IcedLong>> list = new LinkedList<>(unsortMap.entrySet());
    // Sorting the list based on values
    Collections.sort(list, new Comparator<Map.Entry<IcedLong, IcedLong>>() {
      public int compare(Map.Entry<IcedLong, IcedLong> o1, Map.Entry<IcedLong, IcedLong> o2) {
        return ((Long)o2.getValue()._val).compareTo(o1.getValue()._val);
      }
    });
    // Maintaining insertion order with the help of LinkedList
    Map sortedMap = new LinkedHashMap<>();
    for (Map.Entry<IcedLong, IcedLong> entry : list) {
      sortedMap.put(entry.getKey()._val, entry.getValue()._val);
    }
    return sortedMap;
  }

  // Create a combined domain from the categorical values that map to domain A and domain B
  // Both categorical integers are combined into a long = (int,int), and the unsortedMap keeps the occurrence count for each pair-wise interaction
  protected String[] makeDomain(Map<IcedLong, IcedLong> unsortedMap, String[] dA, String[] dB) {
    String[] _domain;
//    Log.info("Collected hash table");
//    Log.info(java.util.Arrays.deepToString(unsortedMap.entrySet().toArray()));

//    Log.info("Interaction between " + dA.length + " and " + dB.length + " factor levels => " +
//            ((long)dA.length * dB.length) + " possible factors.");

    _sortedMap = mySort(unsortedMap);

    // create domain of the most frequent unique factors
    long factorCount = 0;
//    Log.info("Found " + _sortedMap.size() + " unique interaction factors (out of " + ((long)dA.length * (long)dB.length) + ").");
    _domain = new String[_sortedMap.size()]; //TODO: use ArrayList here, then convert to array
    Iterator it2 = _sortedMap.entrySet().iterator();
    int d = 0;
    while (it2.hasNext()) {
      Map.Entry kv = (Map.Entry)it2.next();
      final long ab = (Long)kv.getKey();
      final long count = (Long)kv.getValue();
      if (factorCount < _ci._max_factors && count >= _ci._min_occurrence) {
        factorCount++;
        // extract the two original factor categoricals
        String feature = "";
        if (dA != dB) {
          int a = (int)(ab >> 32);
          final String fA = a != _missing ? dA[a] : "NA";
          feature = fA + "_";
        }
        int b = (int) ab;
        String fB = b != _missing ? dB[b] : "NA";
        feature += fB;

//        Log.info("Adding interaction feature " + feature + ", occurrence count: " + count);
//        Log.info("Total number of interaction factors so far: " + factorCount);
        _domain[d++] = feature;
      } else break;
    }
    if (d < _sortedMap.size()) {
//      Log.info("Truncated map to " + _sortedMap.size() + " elements.");
      String[] copy = new String[d+1];
      System.arraycopy(_domain, 0, copy, 0, d);
      copy[d] = _other;
      _domain = copy;

      Map tm = new LinkedHashMap<>();
      it2 = _sortedMap.entrySet().iterator();
      while (--d >= 0) {
        Map.Entry kv = (Map.Entry) it2.next();
        tm.put(kv.getKey(), kv.getValue());
      }
      _sortedMap = tm;
    }
//    Log.info("Created domain: " + Arrays.deepToString(_domain));
    return _domain;
  }

  private ArrayList<int[]> interactions() {
    ArrayList<int[]> al = new ArrayList<>();
    if (!_ci._pairwise || _ci._factors.length < 3) {
      al.add(_ci._factors);
    } else {
      // pair-wise
      for (int i = 0; i < _ci._factors.length; ++i) {
        for (int j = i + 1; j < _ci._factors.length; ++j) {
          al.add(new int[]{_ci._factors[i], _ci._factors[j]});
        }
      }
    }
    return al;
  }

  public int work() {
    ArrayList<int[]> al = interactions();
    int work=0;
    for (int l=0; l<al.size(); ++l) {
      int[] factors = al.get(l);
      int start = factors.length == 1 ? 0 : 1;
      for (int i = start; i < factors.length; ++i) {
        work++;
      }
    }
    return work;
  }

  @Override
  public void compute2() {
    DKV.remove(_ci.dest());

    Frame source_frame = DKV.getGet(_ci._source_frame);

    ArrayList<int[]> al = interactions();
    for (int l=0; l<al.size(); ++l) {
      int[] factors = al.get(l);
      int idx1 = factors[0];
      Vec tmp = null;
      int start = factors.length == 1 ? 0 : 1;
      Frame _out = null;
      for (int i = start; i < factors.length; ++i) {
        String name;
        int idx2 = factors[i];
        if (i > 1) {
          idx1 = _out.find(tmp);
          assert idx1 >= 0;
          name = _out._names[idx1] + "_" + source_frame._names[idx2];
        } else {
          name = source_frame._names[idx1] + "_" + source_frame._names[idx2];
        }
//      Log.info("Combining columns " + idx1 + " and " + idx2);
        final Vec A = i > 1 ? _out.vecs()[idx1] : source_frame.vecs()[idx1];
        final Vec B = source_frame.vecs()[idx2];

        // Pass 1: compute unique domains of all interaction features
        createInteractionDomain pass1 = new createInteractionDomain(idx1 == idx2).doAll(A, B);

        // Create a new Vec based on the domain
        final Vec vec = source_frame.anyVec().makeZero(makeDomain(pass1._unsortedMap, A.domain(), B.domain()));
        if (i > 1) {
          _out.add(name, vec);
        } else {
          assert(_out == null);
          _out = new Frame(new String[]{name}, new Vec[]{vec});
        }
        final Vec C = _out.lastVec();

        // Create array of categorical pairs, in the same (sorted) order as in the _domain map -> for linear lookup
        // Note: "other" is not mapped in keys, so keys.length can be 1 less than domain.length
        long[] keys = new long[_sortedMap.size()];
        int pos = 0;
        for (long k : _sortedMap.keySet()) {
          keys[pos++] = k;
        }
        assert (C.domain().length == keys.length || C.domain().length == keys.length + 1); // domain might contain _other

        // Pass 2: fill Vec values
        new fillInteractionCategoricals(idx1 == idx2, keys).doAll(A, B, C);
        tmp = C;

        // remove temporary vec
        if (i > 1) {
          final int idx = _out.vecs().length - 2; //second-last vec
//        Log.info("Removing column " + _out._names[idx]);
          _out.remove(idx).remove();
        }
        _ci.update(1);
      }
      if (_target == null) {
        _target = new Frame(_ci.dest(), _out.names(), _out.vecs());
        _target.delete_and_lock(_job);
      } else {
        _target.add(_out);
      }
    }
    tryComplete();
  }

  @Override
  public void onCompletion(CountedCompleter caller) {
    _target.update(_job);
    _target.unlock(_job);
    ((Job)DKV.getGet(_job)).done();
  }

  // Create interaction domain
  private static class createInteractionDomain extends MRTask<createInteractionDomain> {
    // INPUT
    final private boolean _same;

    // OUTPUT
    private IcedHashMap<IcedLong, IcedLong> _unsortedMap = null;

    public createInteractionDomain(boolean same) { _same = same; }

    @Override
    public void map(Chunk A, Chunk B) {
      _unsortedMap = new IcedHashMap<IcedLong, IcedLong>();
      // find unique interaction domain
      for (int r = 0; r < A._len; r++) {
        int a = A.isNA(r) ? _missing : (int)A.at8(r);
        long ab;
        if (!_same) {
          int b = B.isNA(r) ? _missing : (int)B.at8(r);

          // key: combine both ints into a long
          ab = ((long) a << 32) | (b & 0xFFFFFFFFL);
          assert a == (int) (ab >> 32);
          assert b == (int) ab;
        } else {
          if (a == _missing) continue;
          ab = (long)a;
        }

        // add key to hash map, and count occurrences (for pruning)
        IcedLong AB = new IcedLong(ab);
        if (_unsortedMap.containsKey(AB)) {
          _unsortedMap.get(AB)._val++;
        } else {
          _unsortedMap.put(AB, new IcedLong(1));
        }
      }
    }

    @Override
    public void reduce(createInteractionDomain mrt) {
      assert(mrt._unsortedMap != null);
      assert(_unsortedMap != null);
      for (Map.Entry<IcedLong,IcedLong> e : mrt._unsortedMap.entrySet()) {
        IcedLong x = _unsortedMap.get(e.getKey());
        if (x != null) {
          x._val+=e.getValue()._val;
        } else {
          _unsortedMap.put(e.getKey(), e.getValue());
        }
      }

      mrt._unsortedMap = null;
//    Log.info("Merged hash tables");
//    Log.info(java.util.Arrays.deepToString(_unsortedMap.entrySet().toArray()));
    }
  }

  // Fill interaction categoricals in last Vec in Frame
  private static class fillInteractionCategoricals extends MRTask<fillInteractionCategoricals> {
    // INPUT
    boolean _same;
    final long[] _keys; //minimum information to be sent over the wire
    transient private java.util.List<java.util.Map.Entry<Long,Integer>> _valToIndex; //node-local shared helper for binary search

    public fillInteractionCategoricals(boolean same, long[] keys) {
      _same = same; _keys = keys;
    }

    @Override
    protected void setupLocal() {
      // turn _keys into a sorted array of pairs
      _valToIndex = new java.util.ArrayList<>(); // map factor level (int,int) to domain index (long)
      for (int i=0;i<_keys.length;++i) {
        _valToIndex.add(new AbstractMap.SimpleEntry<>(_keys[i], i));
      }
      // sort by key (the factor level)
      Collections.sort(_valToIndex, new Comparator<Map.Entry<Long, Integer>>() {
        @Override public int compare(Map.Entry<Long, Integer> o1, Map.Entry<Long, Integer> o2) { return o1.getKey().compareTo(o2.getKey()); }
      });
    }

    @Override
    public void map(Chunk A, Chunk B, Chunk C) {
      // find unique interaction domain
      for (int r = 0; r < A._len; r++) {
        final int a = A.isNA(r) ? _missing : (int)A.at8(r);
        long ab;
        if (!_same) {
          final int b = B.isNA(r) ? _missing : (int) B.at8(r);
          ab = ((long) a << 32) | (b & 0xFFFFFFFFL); // key: combine both ints into a long
        } else {
          ab = (long)a;
        }

        if (_same && A.isNA(r)) {
          C.setNA(r);
        } else {
          // find _domain index for given factor level ab
          int level = -1;
          int pos = Collections.binarySearch(_valToIndex, new AbstractMap.SimpleEntry<Long,Integer>(ab,0), new Comparator<Map.Entry<Long, Integer>>() {
            @Override public int compare(Map.Entry<Long, Integer> o1, Map.Entry<Long, Integer> o2) { return o1.getKey().compareTo(o2.getKey()); }
          });
          if (pos >= 0) {
            level = _valToIndex.get(pos).getValue();
            assert _keys[level] == ab; //confirm that binary search in _valToIndex worked
          }
          if (level < 0) {
            for (int i=0; i<_keys.length; ++i) {
              assert (_keys[i] != ab);
            }
            level = _fr.lastVec().domain().length-1;
            assert _fr.lastVec().domain()[level].equals(_other);
          }
          C.set(r, level);
        }
      }
    }

  }
}