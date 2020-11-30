package hex.tree.xgboost.rabit.util;

import water.util.Pair;

import java.util.*;

/**
 * Java implementation of ai.h2o.xgboost4j.scala.rabit.util.LinkMap
 *
 * Naming left for consistency. In reality this is a simple binary tree data structure, which is used for communication
 * between Rabit workers.
 *
 */
public class LinkMap {
    private int numWorkers;
    public Map<Integer, List<Integer>> treeMap = new LinkedHashMap<>();
    public Map<Integer, Integer> parentMap = new LinkedHashMap<>();
    public Map<Integer, Pair<Integer, Integer>> ringMap = new LinkedHashMap<>();

    public LinkMap(int numWorkers) {
        this.numWorkers = numWorkers;

        Map<Integer, List<Integer>> treeMap_ = initTreeMap();
        Map<Integer, Integer> parentMap_ = initParentMap();
        Map<Integer, Pair<Integer, Integer>> ringMap_ = constructRingMap(treeMap_, parentMap_);

        Map<Integer, Integer> rMap_ = new LinkedHashMap<>(numWorkers - 1);
        rMap_.put(0, 0);
        int k = 0;
        for(int i = 0; i < numWorkers - 1; i++) {
            int kNext = ringMap_.get(k)._2();
            k = kNext;
            rMap_.put(kNext, (i + 1));
        }

        for (Map.Entry<Integer, Pair<Integer, Integer>> kv : ringMap_.entrySet()) {
            this.ringMap.put(
                    rMap_.get(kv.getKey()),
                    new Pair<>(rMap_.get(kv.getValue()._1()), rMap_.get(kv.getValue()._2()))
            );
        }

        for (Map.Entry<Integer, List<Integer>> kv : treeMap_.entrySet()) {
            List<Integer> mapped = new ArrayList<>(kv.getValue().size());
            for(Integer v : kv.getValue()) {
                mapped.add(rMap_.get(v));
            }
            treeMap.put(
                    rMap_.get(kv.getKey()),
                    mapped
            );
        }

        for (Map.Entry<Integer, Integer> kv : parentMap_.entrySet()) {
            if(kv.getKey() == 0) {
                parentMap.put(rMap_.get(kv.getKey()), -1);
            } else {
                parentMap.put(rMap_.get(kv.getKey()), rMap_.get(kv.getValue()));
            }
        }
    }

    /**
     * Generates a mapping node -> neighbours(node)
     */
    Map<Integer, List<Integer>> initTreeMap() {
        Map<Integer, List<Integer>> treeMap = new LinkedHashMap<>(numWorkers);
        for(int r = 0; r < numWorkers; r++) {
            treeMap.put(r, getNeighbours(r));
        }
        return treeMap;
    }

    /**
     * Generates a mapping node -> parent (parent of root is -1)
     */
    Map<Integer, Integer> initParentMap() {
        Map<Integer, Integer> parentMap = new LinkedHashMap<>(numWorkers);
        for(int r = 0; r < numWorkers; r++) {
            parentMap.put(r, ((r + 1) / 2 - 1) );
        }
        return parentMap;
    }

    /**
     * Returns a list containing existing neighbours of a node, this includes at most 3 nodes: parent, left and right child.
     */
    List<Integer> getNeighbours(int rank) {
        if(rank < 0) {
            throw new IllegalStateException("Rank should be non negative");
        }

        if(rank >= numWorkers) {
            throw new IllegalStateException("Rank ["+rank+"] too high for the number of workers ["+numWorkers+"]");
        }

        rank += 1;
        List<Integer> neighbour = new ArrayList<>();

        if(rank > 1) {
            neighbour.add(rank / 2 - 1);
        }
        if(rank * 2 - 1 < numWorkers) {
            neighbour.add(rank * 2 - 1);
        }
        if(rank * 2 < numWorkers) {
            neighbour.add(rank * 2);
        }

        return neighbour;
    }

    /**
     * Returns a DFS (root, DFS(left_child), DFS(right)child) order from root with given rank.
     */
    List<Integer> constructShareRing(Map<Integer, List<Integer>> treeMap,
                                            Map<Integer, Integer> parentMap,
                                            int rank) {
        Set<Integer> connectionSet = new LinkedHashSet<>(treeMap.get(rank));
        connectionSet.remove(parentMap.get(rank));
        if(connectionSet.isEmpty()) {
            return Collections.singletonList(rank);
        } else {
            List<Integer> ringSeq = new LinkedList<>();
            ringSeq.add(rank);
            int cnt = 0;
            for(Integer n : connectionSet) {
                List<Integer> vConnSeq = constructShareRing(treeMap, parentMap, n);
                cnt++;
                if(connectionSet.size() == cnt) {
                    Collections.reverse(vConnSeq);
                }
                ringSeq.addAll(vConnSeq);
            }
            return ringSeq;
        }

    }

    /**
     * Returns for each node with "rank" the previous and next node in DFS order. For the root the "previous"
     * entry will be the last element, which will create a ring type structure.
     */
    Map<Integer, Pair<Integer, Integer>> constructRingMap(Map<Integer, List<Integer>> treeMap,
                                                                  Map<Integer, Integer> parentMap) {
        assert parentMap.get(0) == -1;

        List<Integer> sharedRing = constructShareRing(treeMap, parentMap, 0);
        assert sharedRing.size() == treeMap.size();

        Map<Integer, Pair<Integer, Integer>> ringMap = new LinkedHashMap<>(numWorkers);
        for(int r = 0; r < numWorkers; r++) {
            int rPrev = (r + numWorkers - 1) % numWorkers;
            int rNext = (r + 1) % numWorkers;
            ringMap.put(sharedRing.get(r), new Pair<>(sharedRing.get(rPrev), sharedRing.get(rNext)));
        }
        return ringMap;
    }
}
