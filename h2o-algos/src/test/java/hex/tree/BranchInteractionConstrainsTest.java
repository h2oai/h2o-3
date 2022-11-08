package hex.tree;

import hex.tree.isoforextended.ExtendedIsolationForestTest;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.IcedHashSet;
import water.util.IcedInt;
import water.util.Log;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class BranchInteractionConstrainsTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(BranchInteractionConstrainsTest.class);

    @Test
    public void testIsAllowedIndex() {
        IcedHashSet<IcedInt> allowed = new IcedHashSet<>();
        allowed.add(new IcedInt(0));
        allowed.add(new IcedInt(1));
        allowed.add(new IcedInt(2));
        allowed.add(new IcedInt(3));

        BranchInteractionConstraints bic = new BranchInteractionConstraints(allowed);

        assertTrue(bic.isAllowedIndex(0));
        assertTrue(bic.isAllowedIndex(1));
        assertTrue(bic.isAllowedIndex(2));
        assertTrue(bic.isAllowedIndex(3));
        assertFalse(bic.isAllowedIndex(4));
        assertFalse(bic.isAllowedIndex(-23));
        assertFalse(bic.isAllowedIndex(55));
    }

    @Test
    public void testIsAllowedIndexFromGlobalInteractionSimulateRealUsage() {
        String[][] interactions = {{"A", "B", "C"}, {"C", "D"}};
        String[] names = {"A", "B", "C", "D", "E"};

        GlobalInteractionConstraints ics = new GlobalInteractionConstraints(interactions, names);
        IcedHashSet<IcedInt> allowed = ics.getAllAllowedColumnIndices();

        Log.info("allowed indices ", Arrays.toString(allowed.toArray()));

        BranchInteractionConstraints bic = new BranchInteractionConstraints(allowed);

        assertTrue(bic.isAllowedIndex(0));
        assertTrue(bic.isAllowedIndex(1));
        assertTrue(bic.isAllowedIndex(2));
        assertTrue(bic.isAllowedIndex(3));
        assertFalse(bic.isAllowedIndex(4));
    }

    @Test
    public void testIntersection() {
        IcedHashSet<IcedInt> allowed1 = new IcedHashSet<>();
        allowed1.add(new IcedInt(0));
        allowed1.add(new IcedInt(1));
        allowed1.add(new IcedInt(2));
        allowed1.add(new IcedInt(3));

        BranchInteractionConstraints bic = new BranchInteractionConstraints(allowed1);

        IcedHashSet<IcedInt> allowed2 = new IcedHashSet<>();
        allowed2.add(new IcedInt(5));
        allowed2.add(new IcedInt(1));
        allowed2.add(new IcedInt(2));
        allowed2.add(new IcedInt(4));

        IcedHashSet<IcedInt> intersection = bic.intersection(allowed2);
        assertArrayEquals("Not correct intersection", new IcedInt[]{new IcedInt(1), new IcedInt(2)}, intersection.toArray());
        intersection = bic.intersection(allowed1);
        assertArrayEquals("Not correct intersection", new IcedInt[]{new IcedInt(0), new IcedInt(1), new IcedInt(2), new IcedInt(3)}, intersection.toArray());

        IcedHashSet<IcedInt> allowed3 = new IcedHashSet<>();
        allowed3.add(new IcedInt(5));

        intersection = bic.intersection(allowed3);
        assertNotNull(intersection);
        assertTrue(intersection.isEmpty());
    }

}
