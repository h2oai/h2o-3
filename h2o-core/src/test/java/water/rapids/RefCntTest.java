package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
  @Test @Ignore
  public void testBasic() {
    Session session = new Session();
    Frame crimes = parse_test_file(Key.make("crimes.hex"),"smalldata/chicago/chicagoCrimes10k.csv.zip");
    Vec.VectorGroup vg = crimes.anyVec().group();

    // Expect to compute and update crimes.hex "Date" column in-place, but the
    // result is called py_1.  Exactly 1 new vector is made (result of as.Date)
    int key1 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertTrue(crimes.vec("Date").isString());
    Frame py_1 = session.exec("(tmp= py_1 (:= chicagoCrimes10k.hex (as.Date (cols_py chicagoCrimes10k.hex \"Date\") \"%m/%d/%Y %I:%M:%S %p\") 2 []))").getFrame();
    Assert.assertEquals(py_1._key,Key.make("py_1")); // Returns the py_1 frame directly
    Assert.assertTrue(crimes.vec("Date").isNumeric());
    Assert.assertTrue(crimes.vec("Date").mean() > 1300000000L); // msec since epoch is generally >1.3b msec
    int key2 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key1+1,key2); // Exactly 1 new vector is made: as.Date

    // Remove original hex key - even though most columns are shared.  Note
    // that this remove is only valid when done in the session context -
    // otherwise the sharing can't be tracked.  Since most columns are shared,
    // the DKV key should be removed, but NOT most data.
    session.exec("(rm crimes.hex)"); crimes = null;
    for( Vec vec : py_1.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key_tmp = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key2,key_tmp); // No New Vectors, and VecGroup never rolls backwards

    // Both append, and nuke a dead temp, in one expression
    session.exec("(, (tmp= py_2 (append py_1 (day (cols_py py_1 \"Date\")) \"Day\")) (rm py_1))"); py_1 = null;
    Frame py_2 = (Frame)DKV.getGet(Key.make("py_2"));
    for( Vec vec : py_2.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key3 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key2+1,key3); // Exactly 1 new vector
    
    // Start a series of computations that append columns
    Frame py_3 = session.exec("(tmp= py_3 (append py_2 (month (cols_py py_2 \"Date\")) \"Month\"))").getFrame();
    for( Vec vec : py_3.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key4 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key3+1,key4); // Exactly 1 new vector

    // This one does 2 computations to append 1 column, also does an over-write
    // instead of append.
    py_2 = null;
    Frame py_4 = session.exec("(, (rm py_2) (tmp= py_4 (:= py_3 (+ (year (cols_py py_3 \"Date\")) 1900) 17 [])))").getFrame();
    for( Vec vec : py_4.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key5 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key4 + 1, key5); // Exactly 1 new vector, despite two ops: "year" and "+1900".
    
    py_3 = null;
    Frame py_5 = session.exec("(, (rm py_3) (tmp= py_5 (append py_4 (week (cols_py py_4 \"Date\")) \"WeekNum\")))").getFrame();
    for( Vec vec : py_5.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key6 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key5 + 1, key5); // Exactly 1 new vector

    py_4 = null;
    Frame py_6 = session.exec("(,  (rm py_4) (tmp= py_6 (append py_5 (dayOfWeek (cols_py py_5 \"Date\")) \"WeekDay\")))").getFrame();
    for( Vec vec : py_6.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key7 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key6 + 1, key7); // Exactly 1 new vector

    py_5 = null;
    Frame py_7 = session.exec("(, (rm py_5) (tmp= py_7 (append py_6 (hour (cols_py py_6 \"Date\")) \"HourOfDay\")))").getFrame();
    for( Vec vec : py_7.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key8 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key7 + 1, key8); // Exactly 1 new vector

    // A more involved expression; lots of internal temps
    py_6 = null;
    Frame py_8 = session.exec("(, (rm py_6) (tmp= py_8 (append py_7 (| (== (cols_py py_7 \"WeekDay\") \"Sun\") (== (cols_py py_7 \"WeekDay\") \"Sat\")) \"Weekend\")))").getFrame();
    for( Vec vec : py_8.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key9 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key8 + 1, key9); // Exactly 1 new vector, despite lots of internal vecs

    // A more involved expression; lots of internal temps
    py_7 = null;
    Frame py_9 = session.exec("(, (rm py_7) (tmp= py_9 (append py_8 (cut (cols_py py_8 \"Month\") [0 2 5 7 10 12] [\"Winter\" \"Spring\" \"Summer\" \"Autumn\" \"Winter\"] FALSE TRUE 3) \"Season\")))").getFrame();
    for( Vec vec : py_9.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    int key10 = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key9 + 1, key10); // Exactly 1 new vector, despite lots of internal vecs

    // Drop a column
    py_8 = null;
    Frame py_10 = session.exec("(, (rm py_8) (tmp= py_10 (cols py_9 -3)))").getFrame();
    for( Vec vec : py_10.vecs() ) vec.mean(); // Verify we can compute rollups on all cols; will crash if some cols are deleted
    key_tmp = DKV.<Vec.VectorGroup>getGet(vg._key).len(); // Pull latest value from DKV (no caching allowed)
    Assert.assertEquals(key9,key_tmp);  // No new vectors

    // End the session; freeing all resources
    session.end();

    // NO FINALLY FRAME DELETES HERE PLEASE...
    // Session ending should clean up; if it does not we need to detect the leak
  }
}
