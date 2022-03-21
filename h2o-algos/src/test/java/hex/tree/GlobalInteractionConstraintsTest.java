package hex.tree;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.IcedInt;


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
        
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(0).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(1).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(2).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2),new IcedInt(3)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(3).toArray(), new IcedInt[]{new IcedInt(2),new IcedInt(3)});

        Assert.assertArrayEquals(ics.getAllAllowedColumnIndices().toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2),new IcedInt(3)});
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

        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(0).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(1).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(2).toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2), new IcedInt(3)});
        Assert.assertArrayEquals(ics.getAllowedInteractionForIndex(3).toArray(), new IcedInt[]{new IcedInt(2), new IcedInt(3)});

        Assert.assertArrayEquals(ics.getAllAllowedColumnIndices().toArray(), new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2), new IcedInt(3)});
    }
}
