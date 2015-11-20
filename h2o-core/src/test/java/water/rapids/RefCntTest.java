package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;

public class RefCntTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testNoTest() { /*defeat junit complaining about no tests in file*/ }

  // Test basic Copy-On-Write optimization is working, by witnessing that the
  // correct (small) number of real vec copies are made, despite many virtual
  // copies being made.
  @Test
  public void testBasic() {
    Session session = new Session();
    Frame crimes = parse_test_file(Key.make("chicagoCrimes10k.hex"),"smalldata/chicago/chicagoCrimes10k.csv.zip");
    Vec.VectorGroup vg = crimes.anyVec().group();

    // Expect to compute and update crimes.hex "Date" column in-place, but the
    // result is called py_1.  Exactly 1 new vector is made (result of as.Date)
    int key1 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertTrue(crimes.vec("Date").isString());
    Exec.exec("(tmp= py_1 (:= chicagoCrimes10k.hex (as.Date (cols_py chicagoCrimes10k.hex \"Date\") \"%m/%d/%Y %I:%M:%S %p\") 2 []))",session);
    Assert.assertTrue(crimes.vec("Date").isString());// User named frame is unchanged
    Frame py_1 = DKV.getGet(Key.make("py_1"));
    Assert.assertTrue(py_1.vec("Date").isNumeric()); // tmp= py_1 holds the changed column
    Assert.assertTrue(py_1.vec("Date").mean() > 1300000000L); // msec since epoch is generally >1.3b msec
    int key2 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key1+1,key2); // Exactly 1 new vector is made: as.Date

    // Remove original hex key - even though most columns are shared.  Note
    // that this remove is only valid when done in the session context -
    // otherwise the sharing can't be tracked.  Since most columns are shared,
    // the DKV key should be removed, but NOT most data.
    Exec.exec("(rm chicagoCrimes10k.hex)",session); crimes = null;
    for( Vec vec : py_1.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key_tmp = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key2,key_tmp); // No New Vectors, and VecGroup never rolls backwards

    // Both append, and nuke a dead temp, in one expression
    Exec.exec("(, (tmp= py_2 (append py_1 (day (cols_py py_1 \"Date\")) \"Day\")) (rm py_1))",session); py_1 = null;
    Frame py_2 = DKV.getGet(Key.make("py_2"));
    for( Vec vec : py_2.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key3 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key2+1,key3); // Exactly 1 new vector
    
    // Start a series of computations that append columns
    Exec.exec("(tmp= py_3 (append py_2 (month (cols_py py_2 \"Date\")) \"Month\"))",session);
    Frame py_3 = DKV.getGet(Key.make("py_3"));
    for( Vec vec : py_3.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key4 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key3+1,key4); // Exactly 1 new vector

    // This one does 2 computations to append 1 column, also does an over-write
    // instead of append.
    Exec.exec("(, (rm py_2) (tmp= py_4 (:= py_3 (+ (year (cols_py py_3 \"Date\")) 1900) 17 [])))",session);
    Frame py_4 = DKV.getGet(Key.make("py_4"));   py_2 = null;
    for( Vec vec : py_4.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key5 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key4 + 2, key5); // Exactly 2 new vector, for two ops: "year" and "+1900".
    
    Exec.exec("(, (rm py_3) (tmp= py_5 (append py_4 (week (cols_py py_4 \"Date\")) \"WeekNum\")))",session);
    Frame py_5 = DKV.getGet(Key.make("py_5"));  py_3 = null;
    for( Vec vec : py_5.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key6 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key5 + 1, key6); // Exactly 1 new vector

    Exec.exec("(,  (rm py_4) (tmp= py_6 (append py_5 (dayOfWeek (cols_py py_5 \"Date\")) \"WeekDay\")))",session);
    Frame py_6 = DKV.getGet(Key.make("py_6"));  py_4 = null;
    for( Vec vec : py_6.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key7 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key6 + 1, key7); // Exactly 1 new vector

    Exec.exec("(, (rm py_5) (tmp= py_7 (append py_6 (hour (cols_py py_6 \"Date\")) \"HourOfDay\")))",session);
    Frame py_7 = DKV.getGet(Key.make("py_7"));  py_5 = null;
    for( Vec vec : py_7.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key8 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key7 + 1, key8); // Exactly 1 new vector

    // A more involved expression; lots of internal temps
    Exec.exec("(, (rm py_6) (tmp= py_8 (append py_7 (| (== (cols_py py_7 \"WeekDay\") \"Sun\") (== (cols_py py_7 \"WeekDay\") \"Sat\")) \"Weekend\")))",session);
    Frame py_8 = DKV.getGet(Key.make("py_8"));  py_6 = null;
    for( Vec vec : py_8.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key9 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key8 + 3, key9); // Exactly 3 new vectors, one for each of {==, ==, |}

    // A more involved expression; lots of internal temps
    Exec.exec("(, (rm py_7) (tmp= py_9 (append py_8 (cut (cols_py py_8 \"Month\") [0 2 5 7 10 12] [\"Winter\" \"Spring\" \"Summer\" \"Autumn\" \"Winter\"] FALSE TRUE 3) \"Season\")))",session);
    Frame py_9 = DKV.getGet(Key.make("py_9"));  py_7 = null;
    for( Vec vec : py_9.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key10 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key9 + 1, key10); // Exactly 1 new vector, despite lots of internal vecs

    // Drop a column
    Exec.exec("(, (rm py_8) (tmp= py_10 (cols py_9 -3)))",session);
    Frame py_10 = DKV.getGet(Key.make("py_10"));  py_8 = null;
    for( Vec vec : py_10.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    key_tmp = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key10,key_tmp);  // No new vectors

    // End the session; freeing all resources
    session.end(null);

    // NO FINALLY FRAME DELETES HERE PLEASE...
    // Session ending should clean up; if it does not we need to detect the leak
  }
}
