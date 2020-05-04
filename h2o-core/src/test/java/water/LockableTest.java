package water;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class LockableTest {

  @Rule
  public transient ExpectedException ee = ExpectedException.none();
  
  @Test
  public void write_lock_to_read_lock() {
    Key<Job> jobKey = Key.make();
    Key<TstLockable> key = Key.make();
    try {
      TstLockable lockable = new TstLockable(key);

      lockable.write_lock(jobKey);
      lockable.update(jobKey);

      assertNotNull(DKV.getGet(key));

      lockable.write_lock_to_read_lock(jobKey);

      // deleting will not work
      ee.expectMessage("is already in use");
      lockable.delete();
    } finally {
      DKV.remove(key);
    }
  }

  @Test
  public void write_lock_to_read_lock_fail() {
    Key<Job> jobKey = Key.make();
    Key<TstLockable> key = Key.make();
    try {
      TstLockable lockable = new TstLockable(key);
      DKV.put(lockable);

      // cannot convert to read lock if write lock doesn't exist
      ee.expectMessage("not write-locked");
      lockable.write_lock_to_read_lock(jobKey);
    } finally {
      DKV.remove(key);
    }
  }

  private static class TstLockable extends Lockable<TstLockable> {
    private TstLockable(Key<TstLockable> key) {
      super(key);
    }
  }

}
