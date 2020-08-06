package water;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class DKVSnapshotExampleTest {
    @Test
    public void histogramShowcase() {
        Scope.enter();
        try {
            final Cleaner.Histo before = Cleaner.Histo.current(true);
            final Frame frame = Scope.track(TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv"));
            final Cleaner.Histo after = Cleaner.Histo.current(true);

            System.out.println(after._total - before._total);
        } finally {
            Scope.exit(); // Not related, just clears anything tracked in this test
        }

    }

    @Test
    public void keySnapshotShowcase() {
        Scope.enter();
        try {
            final KeySnapshot beforeSnapshot = KeySnapshot.globalSnapshot();
            final Frame frame = Scope.track(TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv"));
            final KeySnapshot afterSnapshot = KeySnapshot.globalSnapshot();
            System.out.println(afterSnapshot.keys().length - beforeSnapshot.keys().length);
        } finally {
            Scope.exit(); // Not related, just clears anything tracked in this tests
        }

    }
}
