package hex.tree.xgboost.rabit.util;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import water.TestBase;
import water.util.Pair;

import java.util.*;

import static org.junit.Assert.*;

public class LinkMapTest extends TestBase {

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionOnNegativeRank() {
        LinkMap map = new LinkMap(1);
        map.getNeighbours(-1);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionOnRankExceedingWorkers() {
        LinkMap map = new LinkMap(1);
        map.getNeighbours(1);
    }

    @Test
    public void shouldReturn2NeighboursForRoot() {
        LinkMap map = new LinkMap(3);
        List<Integer> n = map.getNeighbours(0);
        assertTrue(!n.isEmpty());
        assertEquals(2, n.size());
        assertEquals(1, n.get(0).intValue());
        assertEquals(2, n.get(1).intValue());
    }

    @Test
    public void shouldReturn1NeighboursForRoot() {
        LinkMap map = new LinkMap(2);
        List<Integer> n = map.getNeighbours(0);
        assertTrue(!n.isEmpty());
        assertEquals(1, n.size());
        assertEquals(1, n.get(0).intValue());
    }

    @Test
    public void shouldReturn0NeighboursForRoot() {
        LinkMap map = new LinkMap(1);
        List<Integer> n = map.getNeighbours(0);
        assertTrue(n.isEmpty());
    }

    @Test
    public void shouldReturn1NeighboursForFirstChild() {
        LinkMap map = new LinkMap(2);
        List<Integer> n = map.getNeighbours(1);
        assertTrue(!n.isEmpty());
        assertEquals(0, n.get(0).intValue());
    }

    @Test
    public void shouldReturn2NeighboursForFirstChild() {
        LinkMap map = new LinkMap(4);
        List<Integer> n = map.getNeighbours(1);
        assertTrue(!n.isEmpty());
        assertEquals(0, n.get(0).intValue());
        assertEquals(3, n.get(1).intValue());
    }

    @Test
    public void shouldReturn3NeighboursForFirstChild() {
        LinkMap map = new LinkMap(5);
        List<Integer> n = map.getNeighbours(1);
        assertTrue(!n.isEmpty());
        assertEquals(0, n.get(0).intValue());
        assertEquals(3, n.get(1).intValue());
        assertEquals(4, n.get(2).intValue());
    }

    @Test
    public void shouldReturn1NeighboursForFourthChild() {
        LinkMap map = new LinkMap(5);
        List<Integer> n = map.getNeighbours(4);
        assertTrue(!n.isEmpty());
        assertEquals(1, n.get(0).intValue());
    }

    @Test
    public void shouldInitTreeMap() {
        LinkMap map = new LinkMap(5);
        Map<Integer, List<Integer>> treeMap = map.initTreeMap();
        assertTrue(!treeMap.isEmpty());
        assertEquals(5, treeMap.size());
        assertThat(treeMap.get(0), CoreMatchers.hasItems(1,2));
        assertThat(treeMap.get(1), CoreMatchers.hasItems(0,3,4));
        assertThat(treeMap.get(2), CoreMatchers.hasItems(0));
        assertThat(treeMap.get(3), CoreMatchers.hasItems(1));
        assertThat(treeMap.get(4), CoreMatchers.hasItems(1));
    }

    @Test
    public void shouldInitParentMap() {
        LinkMap map = new LinkMap(9);
        Map<Integer, Integer> parentMap = map.initParentMap();
        assertTrue(!parentMap.isEmpty());
        assertEquals(9, parentMap.size());
        assertEquals(parentMap.get(0).intValue(), -1);
        assertEquals(parentMap.get(1).intValue(), 0);
        assertEquals(parentMap.get(2).intValue(), 0);
        assertEquals(parentMap.get(3).intValue(), 1);
        assertEquals(parentMap.get(4).intValue(), 1);
        assertEquals(parentMap.get(5).intValue(), 2);
        assertEquals(parentMap.get(6).intValue(), 2);
        assertEquals(parentMap.get(7).intValue(), 3);
        assertEquals(parentMap.get(8).intValue(), 3);
    }

    @Test
    public void shouldConstructShareRing() {
        LinkMap map = new LinkMap(5);

        List<Integer> dfs = map.constructShareRing(map.initTreeMap(), map.initParentMap(),0);
        List<Integer> rootList =new LinkedList<Integer>() {
            {
                this.add(0);
                this.add(1);
                this.add(3);
                this.add(4);
                this.add(2);
            }
        };
        assertEquals(dfs, rootList);

        dfs = map.constructShareRing(map.initTreeMap(), map.initParentMap(),1);
        List<Integer> firstList =new LinkedList<Integer>() {
            {
                this.add(1);
                this.add(3);
                this.add(4);
            }
        };
        assertEquals(dfs, firstList);

        dfs = map.constructShareRing(map.initTreeMap(), map.initParentMap(),2);
        assertThat(dfs, CoreMatchers.hasItems(2));

        dfs = map.constructShareRing(map.initTreeMap(), map.initParentMap(),3);
        assertThat(dfs, CoreMatchers.hasItems(3));

        dfs = map.constructShareRing(map.initTreeMap(), map.initParentMap(),4);
        assertThat(dfs, CoreMatchers.hasItems(4));
    }

    @Test
    public void shouldConstructShareMap() {
        LinkMap map = new LinkMap(5);
        Map<Integer, Pair<Integer, Integer>> shareMap = map.constructRingMap(map.initTreeMap(), map.initParentMap());

        assertEquals(shareMap.get(0), new Pair<>(2,1));
        assertEquals(shareMap.get(1), new Pair<>(0,3));
        assertEquals(shareMap.get(2), new Pair<>(4,0));
        assertEquals(shareMap.get(3), new Pair<>(1,4));
        assertEquals(shareMap.get(4), new Pair<>(3,2));
    }

    @Test
    public void shouldConstructLinkMap() {
        LinkMap map = new LinkMap(7);
        Map<Integer, Integer> expectedParent = new HashMap<Integer, Integer>() {{
           this.put(0,-1);
           this.put(1,0);
           this.put(2,1);
           this.put(3,1);
           this.put(4,6);
           this.put(5,6);
           this.put(6,0);
        }};
        assertEquals(expectedParent, map.parentMap);

        Map<Integer, List<Integer>> expectedTreeMap = new HashMap<Integer, List<Integer>>() {{
            this.put(0, Arrays.asList(1,6));
            this.put(1, Arrays.asList(0,2,3));
            this.put(2, Collections.singletonList(1));
            this.put(3, Collections.singletonList(1));
            this.put(4, Collections.singletonList(6));
            this.put(5, Collections.singletonList(6));
            this.put(6, Arrays.asList(0,5,4));
        }};
        assertEquals(expectedTreeMap, map.treeMap);

        Map<Integer, Pair<Integer, Integer>> expectedRing = new HashMap<Integer, Pair<Integer, Integer>>() {{
           this.put(0, new Pair<Integer, Integer>(6,1));
           this.put(1, new Pair<Integer, Integer>(0,2));
           this.put(2, new Pair<Integer, Integer>(1,3));
           this.put(3, new Pair<Integer, Integer>(2,4));
           this.put(4, new Pair<Integer, Integer>(3,5));
           this.put(5, new Pair<Integer, Integer>(4,6));
           this.put(6, new Pair<Integer, Integer>(5,0));
        }};
        assertEquals(expectedRing, map.ringMap);
    }

}
