package hex.tree;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScoreBuildHistogram2Test {

    @Test
    public void testSharedPoolWorkAllocator() {
        final int workAmount = 42;
        ScoreBuildHistogram2.WorkAllocator allocator = new ScoreBuildHistogram2.SharedPoolWorkAllocator(workAmount);
        assertEquals(42, allocator.getMaxId(-1));
        for (int i = 0; i < workAmount; i++) { 
            assertEquals(i, allocator.allocateWork(0));
        }
    }

    @Test
    public void testRangeWorkAllocator_small() {
        final int workAmount = 7;
        ScoreBuildHistogram2.WorkAllocator smallWorkAllocator = new ScoreBuildHistogram2.RangeWorkAllocator(workAmount, 42);
        int sumWork = 0;
        for (int i = 0; i < 42; i++) {
            assertEquals("workerId=" + i, i < 7 ? i + 1 : 7, smallWorkAllocator.getMaxId(i));
            int workStart = smallWorkAllocator.allocateWork(i);
            assertEquals("workerId=" + i, i, workStart);
            if (workStart < smallWorkAllocator.getMaxId(i)) {
                sumWork += smallWorkAllocator.getMaxId(i) - workStart;
            }
        }
        assertEquals(workAmount, sumWork);
    }

    @Test
    public void testRangeWorkAllocator_exact() {
        final int workAmount = 10;
        ScoreBuildHistogram2.WorkAllocator exactWorkAllocator = new ScoreBuildHistogram2.RangeWorkAllocator(workAmount, 5);
        int workDone = 0;
        for (int i = 0; i < 5; i++) {
            assertEquals("workerId=" + i, (i + 1) * 2, exactWorkAllocator.getMaxId(i));
            int workStart = exactWorkAllocator.allocateWork(i);
            assertEquals("workerId=" + i, i * 2, workStart);
            for (int w = workStart; w < exactWorkAllocator.getMaxId(i); w = exactWorkAllocator.allocateWork(i)) {
                workDone++;
            }
        }
        assertEquals(workAmount, workDone);
    }

    @Test
    public void testRangeWorkAllocator() {
        final int workAmount = 10;
        ScoreBuildHistogram2.WorkAllocator workAllocator = new ScoreBuildHistogram2.RangeWorkAllocator(workAmount, 3);
        int workDone = 0;
        for (int i = 0; i < 3; i++) {
            assertEquals("workerId=" + i, i < 2 ? (i + 1) * 4 : workAmount, workAllocator.getMaxId(i));
            int workStart = workAllocator.allocateWork(i);
            assertEquals("workerId=" + i, i * 4, workStart);
            for (int w = workStart; w < workAllocator.getMaxId(i); w = workAllocator.allocateWork(i)) {
                workDone++;
            }
        }
        assertEquals(workAmount, workDone);
    }

}
