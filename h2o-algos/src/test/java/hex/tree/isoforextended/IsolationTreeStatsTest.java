package hex.tree.isoforextended;

import hex.tree.isoforextended.isolationtree.IsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTreeStats;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IsolationTreeStatsTest {

    @Mock
    private IsolationTree isolationTree1;

    @Mock
    private IsolationTree isolationTree2;

    @Mock
    private IsolationTree isolationTree3;

    @Mock
    private IsolationTree isolationTree4;

    @Test
    public void testIsolationTreeStatsSmoke() {
        when(isolationTree1.getIsolatedPoints()).thenReturn(1);
        when(isolationTree1.getNotIsolatedPoints()).thenReturn(2);
        when(isolationTree1.getZeroSplits()).thenReturn(5);
        when(isolationTree1.getLeaves()).thenReturn(5);
        when(isolationTree1.getDepth()).thenReturn(4);

        when(isolationTree2.getIsolatedPoints()).thenReturn(0);
        when(isolationTree2.getNotIsolatedPoints()).thenReturn(0);
        when(isolationTree2.getZeroSplits()).thenReturn(0);
        when(isolationTree2.getLeaves()).thenReturn(5);
        when(isolationTree2.getDepth()).thenReturn(4);

        when(isolationTree3.getIsolatedPoints()).thenReturn(1);
        when(isolationTree3.getNotIsolatedPoints()).thenReturn(14);
        when(isolationTree3.getZeroSplits()).thenReturn(1);
        when(isolationTree3.getLeaves()).thenReturn(8);
        when(isolationTree3.getDepth()).thenReturn(4);

        when(isolationTree4.getIsolatedPoints()).thenReturn(5);
        when(isolationTree4.getNotIsolatedPoints()).thenReturn(11);
        when(isolationTree4.getZeroSplits()).thenReturn(5);
        when(isolationTree4.getLeaves()).thenReturn(7);
        when(isolationTree4.getDepth()).thenReturn(4);

        IsolationTreeStats treeStats = new IsolationTreeStats();
        treeStats.updateBy(isolationTree1);
        treeStats.updateBy(isolationTree2);
        treeStats.updateBy(isolationTree3);
        treeStats.updateBy(isolationTree4);
        checkTreeStats(treeStats);

        IsolationTreeStats treeStatsDifferentOrder = new IsolationTreeStats();
        treeStatsDifferentOrder.updateBy(isolationTree4);
        treeStatsDifferentOrder.updateBy(isolationTree3);
        treeStatsDifferentOrder.updateBy(isolationTree2);
        treeStatsDifferentOrder.updateBy(isolationTree1);
        checkTreeStats(treeStatsDifferentOrder);
    }

    private void checkTreeStats(IsolationTreeStats treeStats) {
        assertEquals(0, treeStats._minIsolated);
        assertEquals(5, treeStats._maxIsolated);
        assertEquals(0, treeStats._minNotIsolated);
        assertEquals(14, treeStats._maxNotIsolated);
        assertEquals(0, treeStats._minZeroSplits);
        assertEquals(5, treeStats._maxZeroSplits);
        assertEquals(5, treeStats._minLeaves);
        assertEquals(8, treeStats._maxLeaves);
        assertEquals(4, treeStats._minDepth);
        assertEquals(4, treeStats._maxDepth);
        assertEquals(4, treeStats._numTrees);
        assertEquals(1.75, treeStats._meanIsolated,0);
        assertEquals(6.75, treeStats._meanNotIsolated,0);
        assertEquals(2.75, treeStats._meanZeroSplits,0);
        assertEquals(6.25, treeStats._meanLeaves,0);
        assertEquals(4, treeStats._meanDepth,0);
    }

    @Test
    public void testFirstExtremeWins() {
        when(isolationTree1.getIsolatedPoints()).thenReturn(1);
        when(isolationTree1.getNotIsolatedPoints()).thenReturn(1);
        when(isolationTree1.getZeroSplits()).thenReturn(1);
        when(isolationTree1.getLeaves()).thenReturn(1);
        when(isolationTree1.getDepth()).thenReturn(1);

        when(isolationTree2.getIsolatedPoints()).thenReturn(12);
        when(isolationTree2.getNotIsolatedPoints()).thenReturn(12);
        when(isolationTree2.getZeroSplits()).thenReturn(12);
        when(isolationTree2.getLeaves()).thenReturn(12);
        when(isolationTree2.getDepth()).thenReturn(12);

        when(isolationTree3.getIsolatedPoints()).thenReturn(2);
        when(isolationTree3.getNotIsolatedPoints()).thenReturn(2);
        when(isolationTree3.getZeroSplits()).thenReturn(2);
        when(isolationTree3.getLeaves()).thenReturn(2);
        when(isolationTree3.getDepth()).thenReturn(2);

        IsolationTreeStats treeStats = new IsolationTreeStats();
        treeStats.updateBy(isolationTree1);
        treeStats.updateBy(isolationTree2);
        treeStats.updateBy(isolationTree3);
        assertEquals(1, treeStats._minIsolated);
        assertEquals(12, treeStats._maxIsolated);
        assertEquals(1, treeStats._minNotIsolated);
        assertEquals(12, treeStats._maxNotIsolated);
        assertEquals(1, treeStats._minZeroSplits);
        assertEquals(12, treeStats._maxZeroSplits);
        assertEquals(1, treeStats._minLeaves);
        assertEquals(12, treeStats._maxLeaves);
        assertEquals(1, treeStats._minDepth);
        assertEquals(12, treeStats._maxDepth);
        assertEquals(3, treeStats._numTrees);
        assertEquals(5, treeStats._meanIsolated,0);
        assertEquals(5, treeStats._meanNotIsolated,1e-3);
        assertEquals(5, treeStats._meanZeroSplits,0);
        assertEquals(5, treeStats._meanLeaves,1e-3);
        assertEquals(5, treeStats._meanDepth,0);
    }

    @Test
    public void testZeroIsValidMinimum() {
        when(isolationTree1.getIsolatedPoints()).thenReturn(0);
        when(isolationTree1.getNotIsolatedPoints()).thenReturn(0);
        when(isolationTree1.getZeroSplits()).thenReturn(0);
        when(isolationTree1.getLeaves()).thenReturn(0);
        when(isolationTree1.getDepth()).thenReturn(0);

        when(isolationTree2.getIsolatedPoints()).thenReturn(12);
        when(isolationTree2.getNotIsolatedPoints()).thenReturn(12);
        when(isolationTree2.getZeroSplits()).thenReturn(12);
        when(isolationTree2.getLeaves()).thenReturn(12);
        when(isolationTree2.getDepth()).thenReturn(12);

        when(isolationTree3.getIsolatedPoints()).thenReturn(1);
        when(isolationTree3.getNotIsolatedPoints()).thenReturn(1);
        when(isolationTree3.getZeroSplits()).thenReturn(1);
        when(isolationTree3.getLeaves()).thenReturn(1);
        when(isolationTree3.getDepth()).thenReturn(1);

        IsolationTreeStats treeStats = new IsolationTreeStats();
        treeStats.updateBy(isolationTree1);
        treeStats.updateBy(isolationTree1);
        treeStats.updateBy(isolationTree2);
        treeStats.updateBy(isolationTree3);
        assertEquals(0, treeStats._minIsolated);
        assertEquals(12, treeStats._maxIsolated);
        assertEquals(0, treeStats._minNotIsolated);
        assertEquals(12, treeStats._maxNotIsolated);
        assertEquals(0, treeStats._minZeroSplits);
        assertEquals(12, treeStats._maxZeroSplits);
        assertEquals(0, treeStats._minLeaves);
        assertEquals(12, treeStats._maxLeaves);
        assertEquals(0, treeStats._minDepth);
        assertEquals(12, treeStats._maxDepth);
        assertEquals(4, treeStats._numTrees);
        assertEquals(3.25, treeStats._meanIsolated,0);
        assertEquals(3.25, treeStats._meanNotIsolated,1e-3);
        assertEquals(3.25, treeStats._meanZeroSplits,0);
        assertEquals(3.25, treeStats._meanLeaves,1e-3);
        assertEquals(3.25, treeStats._meanDepth,0);
    }
}
