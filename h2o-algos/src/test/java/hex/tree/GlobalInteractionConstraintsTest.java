package hex.tree;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class GlobalInteractionConstraintsTest extends TestUtil {
    
    @Test
    public void testCreateObject(){
        String[][] interactions = {{"A", "B", "C"}, {"C", "D"}};
        String[] names = {"A", "B", "C", "D", "E"};
        GlobalInteractionConstraints ics = new GlobalInteractionConstraints(interactions, names);
        
        Assert.assertTrue(ics.allowedInteractionContainsColumn(0));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(1));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(2));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(3));
        Assert.assertFalse(ics.allowedInteractionContainsColumn(4));
        
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(0).toArray(), new Integer[]{0, 1, 2});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(1).toArray(), new Integer[]{0, 1, 2});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(2).toArray(), new Integer[]{0, 1, 2, 3});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(3).toArray(), new Integer[]{2, 3});

        Assert.assertArrayEquals(ics.getAllAllowedColumnIndices().toArray(), new Integer[]{0, 1, 2, 3});
    }

    @Test
    public void testCreateObjectColumnTwice(){
        String[][] interactions = {{"A", "B", "C", "C"}, {"C", "D"}};
        String[] names = {"A", "B", "C", "D", "E"};
        GlobalInteractionConstraints ics = new GlobalInteractionConstraints(interactions, names);

        Assert.assertTrue(ics.allowedInteractionContainsColumn(0));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(1));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(2));
        Assert.assertTrue(ics.allowedInteractionContainsColumn(3));
        Assert.assertFalse(ics.allowedInteractionContainsColumn(4));

        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(0).toArray(), new Integer[]{0, 1, 2});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(1).toArray(), new Integer[]{0, 1, 2});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(2).toArray(), new Integer[]{0, 1, 2, 3});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(3).toArray(), new Integer[]{2, 3});

        Assert.assertArrayEquals(ics.getAllAllowedColumnIndices().toArray(), new Integer[]{0, 1, 2, 3});
    }
}
