package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.ArrayUtils;

import java.io.File;
import java.util.Arrays;

public class RapidsTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void bigSlice() {
    // check that large slices do something sane
    String tree = "(rows %a.hex [0:2147483647])";
    checkTree(tree);
  }

  @Test public void test1() {
    // Checking `hex + 5`
    String tree = "(+ %a.hex #5)";
    checkTree(tree);
  }

  @Test public void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ %a.hex (+ #5 #10))";
    checkTree(tree);
  }

  @Test public void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ %a.hex #5) (* #1 %a.hex)) (* #15 (/ #23 %a.hex)))";
    checkTree(tree);
  }

  @Test public void test4() {
    //Checking `hex == 5`, <=, >=, <, >, !=
    String tree = "(== %a.hex #5)";
    checkTree(tree);
    tree = "(<= %a.hex #5)";
    checkTree(tree);
    tree = "(>= %a.hex #1.25132)";
    checkTree(tree);
    tree = "(< %a.hex #112.341e-5)";
    checkTree(tree);
    tree = "(> %a.hex #0.0123)";
    checkTree(tree);
    tree = "(!= %a.hex #0)";
    checkTree(tree);
  }
  @Test public void test4_throws() {
    String tree = "(== %a.hex (cols a.hex [1 2]))";
    checkTree(tree,true);
  }
  
  @Test public void test5() {
    // Checking `hex && hex`, ||, &, |
    String tree = "(&& %a.hex %a.hex)";
    checkTree(tree);
    tree = "(|| %a.hex %a.hex)";
    checkTree(tree);
    tree = "(& %a.hex %a.hex)";
    checkTree(tree);
    tree = "(| %a.hex %a.hex)";
    checkTree(tree);
  }

  @Test public void test6() {
    // Checking `hex[,1]`
    String tree = "(cols %a.hex [0])";
    checkTree(tree);
    // Checking `hex[1,5]`
    tree = "(rows (cols %a.hex [0]) [5])";
    checkTree(tree);
    // Checking `hex[c(1:5,7,9),6]`
    tree = "(cols (rows %a.hex [0:4 6 7]) [0])";
    checkTree(tree);
    // Checking `hex[c(8,1,1,7),1]`
    tree = "(rows a.hex [8 1 1 7])";
    checkTree(tree);
  }

  @Test public void testRowAssign() {
    String tree;
    // Assign column 3 over column 0
    tree = "(= %a.hex (cols %a.hex [3]) 0 [0:150])";
    checkTree(tree);

    // Assign 17 over column 0
    tree = "(= %a.hex 17 [0] [0:150])";
    checkTree(tree);

    // Assign 17 over column 0, row 5
    tree = "(= %a.hex 17 [0] [5])";
    checkTree(tree);

    // Append 17
    tree = "(= %a.hex 17 [4] [0:150])";
    checkTree(tree);
  }

  @Test public void testFun() {
    // Compute 3*3; single variable defined in function body
    String tree = "({var1 . (* var1 var1)} 3)";
    checkTree(tree);
    // Unknown var2
    tree = "({var1 . (* var1 var2)} 3)";
    checkTree(tree,true);
    // Compute 3* a.hex[0,0]
    tree = "({var1 . (* var1 (rows %a.hex [0]))} 3)";
    checkTree(tree);

    // Some more horrible functions.  Drop the passed function and return a 3
    tree = "({fun . 3} {y . (* y y)})";
    checkTree(tree);
    // Apply a 3 to the passed function
    tree = "({fun . (fun 3)} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the ID function
    tree = "({fun . fun} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-3 function
    tree = "({fun . (fun (fun 3))} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-x function
    tree = "({fun x . (fun (fun x))} {y . (* y y)} 3)";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function
    tree = " ({fun . {x . (fun (fun x))}} {y . (* y y)})   ";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function, and apply it
    tree = "(({fun . {x . (fun (fun x))}} {y . (* y y)}) 3)";
    checkTree(tree);
  }

  @Test public void testCBind() {
    String tree = "(cbind 1 2)";
    checkTree(tree);

    tree = "(cbind 1 a.hex 2)";
    checkTree(tree);
    
    tree = "(cbind a.hex (cols a.hex 0) 2)";
    checkTree(tree);
  }

  @Test public void testRBind() {
    String tree = "(rbind 1 2)";
    checkTree(tree);

    //tree = "(rbind a.hex 1 2)";
    //checkTree(tree);
  }

  @Test public void testApply() {
    // Sum, reduction.  1 row result
    String tree = "(apply a.hex 2 {x . (sum x)})";
    checkTree(tree);

    // Return ID column results.  Shared data result.
    tree = "(apply a.hex 2 {x . x})";
    checkTree(tree);

    // Return column results, new data result.
    tree = "(apply a.hex 2 abs)";
    checkTree(tree);

    // Return two results
    tree = "(apply a.hex 2 {x . (rbind (sumNA x) (sum x))})";
    checkTree(tree);
  }

  @Test public void testRowApply() {
    String tree = "(apply a.hex 1 sum)";
    checkTree(tree);

    tree = "(apply a.hex 1 max)";
    checkTree(tree);

    tree = "(apply a.hex 1 {x . (sum x)})";
    checkTree(tree);

    tree = "(apply a.hex 1 {x . (sum (* x x))})";
    checkTree(tree);
  }

  @Test public void testMath() {
    for( String s : new String[] {"abs", "cos", "sin", "acos", "ceiling", "floor", "cosh", "exp", "log", "sqrt", "tan", "tanh"} )
      checkTree("("+s+" a.hex)");
  }

  @Test public void testVariance() {
    // Checking variance: scalar
    String tree = "({x . (var x x \"everything\")} (rows a.hex [0]))";
    checkTree(tree);

    tree = "({x . (var x x \"everything\")} a.hex)";
    checkTree(tree);

    tree = "(table (trunc (cols a.hex 1)))";
    checkTree(tree);

    tree = "(table (cols a.hex 1))";
    checkTree(tree);

    tree = "(table (cols a.hex 1) (cols a.hex 2))";
    checkTree(tree);
  }

  private void checkTree(String tree) { checkTree(tree,false); }
  private void checkTree(String tree, boolean expectThrow) {
    //Frame r = frame(new double[][]{{-1},{1},{2},{3},{4},{5},{6},{254}});
    //Key ahex = Key.make("a.hex");
    //Frame fr = new Frame(ahex, null, new Vec[]{r.remove(0)});
    //r.delete();
    //DKV.put(ahex, fr);
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris/iris_wheader.csv");
    fr.remove(4).remove();
    try {
      Val val = Exec.exec(tree);
      Assert.assertFalse(expectThrow);
      System.out.println(val.toString());
      if( val instanceof ValFrame ) {
        Frame fr2= ((ValFrame)val)._fr;
        System.out.println(fr2.vec(0));
        fr2.remove();
      }
    } catch( IllegalArgumentException iae ) {
      if( !expectThrow ) throw iae;
    } finally {
      fr.delete();
    }
  }

  @Test public void testMerge() {
    Frame l=null,r=null,f=null;
    try {
      l = ArrayUtils.frame("name" ,vec(ar("Cliff","Arno","Tomas","Spencer"),ari(0,1,2,3)));
      l.    add("age"  ,vec(ar(">dirt" ,"middle","middle","young'n"),ari(0,1,2,3)));
      l = new Frame(l);
      DKV.put(l);
      System.out.println(l);
      r = ArrayUtils.frame("name" ,vec(ar("Arno","Tomas","Michael","Cliff"),ari(0,1,2,3)));
      r.    add("skill",vec(ar("science","linearmath","sparkling","hacker"),ari(0,1,2,3)));
      r = new Frame(r);
      DKV.put(r);
      System.out.println(r);
      String x = String.format("(merge %s %s #1 #0 )",l._key,r._key);
      Val res = Exec.exec(x);
      f = res.getFrame();
      System.out.println(f);
    } finally {
      if( f != null ) f.delete();
      if( r != null ) r.delete();
      if( l != null ) l.delete();
    }
  }


  @Test public void testQuantile() {
    Frame f = null;
    try {
      Frame fr = ArrayUtils.frame(ard(ard(1.223292e-02),
                                      ard(1.635312e-25),
                                      ard(1.601522e-11),
                                      ard(8.452298e-10),
                                      ard(2.643733e-10),
                                      ard(2.671520e-06),
                                      ard(1.165381e-06),
                                      ard(7.193265e-10),
                                      ard(3.383532e-04),
                                      ard(2.561221e-05)));
      double[] probs = new double[]{0.001, 0.005, .01, .02, .05, .10, .50, .8883, .90, .99};
      String x = String.format("(quantile %%%s %s \"interpolate\")", fr._key, Arrays.toString(probs));
      Val val = Exec.exec(x);
      fr.delete();
      f = val.getFrame();
      Assert.assertEquals(2,f.numCols());
      // Expected values computed as golden values from R's quantile call
      double[] exp = ard(1.4413698000016206E-13, 7.206849000001562E-13, 1.4413698000001489E-12, 2.882739600000134E-12, 7.20684900000009E-12,
                         1.4413698000000017E-11, 5.831131148999999E-07, 3.3669567275300000E-04, 0.00152780988        , 0.011162408988      );
      for( int i=0; i<exp.length; i++ )
        Assert.assertTrue( "expected "+exp[i]+" got "+f.vec(1).at(i), water.util.MathUtils.compare(exp[i],f.vec(1).at(i),1e-6,1e-6) );
    } finally {
      if( f != null ) f.delete();
    }
  }

  static Frame exec_str( String str, String id ) {
    Val val = Exec.exec(str);
    switch( val.type() ) {
    case Val.FRM:
      Frame fr = val.getFrame();
      Key k = Key.make(id);
      // Smart delete any prior top-level result
      Iced i = DKV.getGet(k);
      if( i instanceof Lockable) ((Lockable)i).delete();
      else if( i instanceof Keyed ) ((Keyed)i).remove();
      else if( i != null ) throw new IllegalArgumentException("Attempting to overright an unexpected key");
      DKV.put(fr = new Frame(k,fr._names,fr.vecs()));
      System.out.println(fr);
      checkSaneFrame();
      return fr;
    case Val.NUM:
      System.out.println("num= "+val.getNum());
      assert id==null;
      checkSaneFrame();
      return null;
    case Val.STR:
      System.out.println("str= "+val.getStr());
      assert id==null;
      checkSaneFrame();
      return null;
    default:
      throw water.H2O.fail();
    }
  }

  static void checkSaneFrame() {  assert checkSaneFrame_impl(); }
  static boolean checkSaneFrame_impl() {
    for( Key k : H2O.localKeySet() ) {
      Value val = H2O.raw_get(k);
      if( val.isFrame() ) {
        Frame fr = val.get();
        Vec vecs[] = fr.vecs();
        for( int i=0; i<vecs.length; i++ ) {
          Vec v = vecs[i];
          if( DKV.get(v._key) == null ) {
            System.err.println("Frame "+fr._key+" in the DKV, is missing Vec "+v._key+", name="+fr._names[i]);
            return false;
          }
        }
      }
    }
    return true;
  }

  @Test public void testChicago() {
    Frame weather=null, crimes=null, census=null;
    String oldtz = Exec.exec("(getTimeZone)").getStr();
    try {
      weather = parse_test_file(Key.make("weather.hex"),"smalldata/chicago/chicagoAllWeather.csv");
      crimes  = parse_test_file(Key.make( "crimes.hex"),"smalldata/chicago/chicagoCrimes10k.csv.zip");
      String fname = "smalldata/chicago/chicagoCensus.csv";
      File f = find_test_file(fname);
      assert f != null && f.exists():" file not found: " + fname;
      NFSFileVec nfs = NFSFileVec.make(f);
      ParseSetup ps = ParseSetup.guessSetup(new Key[]{nfs._key}, false, 1);
      ps.getColumnTypes()[1] = Vec.T_ENUM;
      census = ParseDataset.parse(Key.make( "census.hex"), new Key[]{nfs._key}, true, ps);

      census = exec_str("(colnames= census.hex [0 1 2 3 4 5 6 7 8] [\"Community.Area.Number\" \"COMMUNITY.AREA.NAME\" \"PERCENT.OF.HOUSING.CROWDED\" \"PERCENT.HOUSEHOLDS.BELOW.POVERTY\" \"PERCENT.AGED.16..UNEMPLOYED\" \"PERCENT.AGED.25..WITHOUT.HIGH.SCHOOL.DIPLOMA\" \"PERCENT.AGED.UNDER.18.OR.OVER.64\" \"PER.CAPITA.INCOME.\" \"HARDSHIP.INDEX\"])", "census.hex");

      crimes = exec_str("(colnames= crimes.hex [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21] [\"ID\" \"Case.Number\" \"Date\" \"Block\" \"IUCR\" \"Primary.Type\" \"Description\" \"Location.Description\" \"Arrest\" \"Domestic\" \"Beat\" \"District\" \"Ward\" \"Community.Area\" \"FBI.Code\" \"X.Coordinate\" \"Y.Coordinate\" \"Year\" \"Updated.On\" \"Latitude\" \"Longitude\" \"Location\"])", "crimes.hex");

      exec_str("(setTimeZone \"Etc/UTC\")", null);

      crimes = exec_str("(colnames= (= crimes.hex (tmp= unary_op_6 (day (tmp= nary_op_5 (as.Date (cols crimes.hex [2]) \"%m/%d/%Y %I:%M:%S %p\")))) [22] [0:9999]) 22 \"Day\")", "crimes.hex");

      crimes = exec_str("(colnames= (= crimes.hex (tmp= binary_op_31 (+ (tmp= unary_op_7 (month nary_op_5)) #1)) [23] [0:9999]) 23 \"Month\")", "crimes.hex");
      
      Keyed.remove(Key.make("nary_op_30"));

      crimes = exec_str("(colnames= (= crimes.hex (tmp= binary_op_32 (+ (tmp= binary_op_9 (- (tmp= unary_op_8 (year nary_op_5)) #1900)) #1900)) [17] [0:9999]) 17 \"Year\")", "crimes.hex");

      crimes = exec_str("(colnames= (= crimes.hex (tmp= unary_op_10 (week nary_op_5)) [24] [0:9999]) 24 \"WeekNum\")", "crimes.hex");

      Keyed.remove(Key.make("binary_op_32"));
      Keyed.remove(Key.make("binary_op_31"));
      Keyed.remove(Key.make("unary_op_8"));
      checkSaneFrame();

      crimes = exec_str("(colnames= (= crimes.hex (tmp= unary_op_11 (dayOfWeek nary_op_5)) [25] [0:9999]) 25 \"WeekDay\")", "crimes.hex");
      Keyed.remove(Key.make("nfs:\\C:\\Users\\cliffc\\Desktop\\h2o-3\\smalldata\\chicago\\chicagoCrimes10k.csv.zip"));

      crimes = exec_str("(colnames= (= crimes.hex (tmp= unary_op_12 (hour nary_op_5)) [26] [0:9999]) 26 \"HourOfDay\")", "crimes.hex");

      crimes = exec_str("(colnames= (= crimes.hex (tmp= nary_op_16 (ifelse (tmp= binary_op_15 (| (tmp= binary_op_13 (== unary_op_11 \"Sun\")) (tmp= binary_op_14 (== unary_op_11 \"Sat\")))) 1 0)) [27] [0:9999]) 27 \"Weekend\")", "crimes.hex");

      // Season is incorrectly assigned in the original chicago demo; picks up the Weekend flag
      crimes = exec_str("(colnames= (= crimes.hex nary_op_16 [28] [0:9999]) 28 \"Season\")", "crimes.hex");

      // Standard "head of 10 rows" pattern for printing
      Frame subset_33 = exec_str("(rows crimes.hex [0:10])", "subset_33");
      Keyed.remove(Key.make("subset_33"));

      Keyed.remove(Key.make("subset_33"));
      Keyed.remove(Key.make("unary_op_29"));
      Keyed.remove(Key.make("nary_op_28"));
      Keyed.remove(Key.make("nary_op_27"));
      Keyed.remove(Key.make("nary_op_26"));
      Keyed.remove(Key.make("binary_op_25"));
      Keyed.remove(Key.make("binary_op_24"));
      Keyed.remove(Key.make("binary_op_23"));
      Keyed.remove(Key.make("binary_op_22"));
      Keyed.remove(Key.make("binary_op_21"));
      Keyed.remove(Key.make("binary_op_20"));
      Keyed.remove(Key.make("binary_op_19"));
      Keyed.remove(Key.make("binary_op_18"));
      Keyed.remove(Key.make("binary_op_17"));
      Keyed.remove(Key.make("nary_op_16"));
      Keyed.remove(Key.make("binary_op_15"));
      Keyed.remove(Key.make("binary_op_14"));
      Keyed.remove(Key.make("binary_op_13"));
      Keyed.remove(Key.make("unary_op_12"));
      Keyed.remove(Key.make("unary_op_11"));
      Keyed.remove(Key.make("unary_op_10"));
      Keyed.remove(Key.make("binary_op_9"));
      Keyed.remove(Key.make("unary_op_8"));
      Keyed.remove(Key.make("unary_op_7"));
      Keyed.remove(Key.make("unary_op_6"));
      Keyed.remove(Key.make("nary_op_5"));
      checkSaneFrame();

      // Standard "head of 10 rows" pattern for printing
      Frame subset_34 = exec_str("(rows crimes.hex [0:10])", "subset_34");
      Keyed.remove(Key.make("subset_34"));

      census = exec_str("(colnames= census.hex [0 1 2 3 4 5 6 7 8] [\"Community.Area\" \"COMMUNITY.AREA.NAME\" \"PERCENT.OF.HOUSING.CROWDED\" \"PERCENT.HOUSEHOLDS.BELOW.POVERTY\" \"PERCENT.AGED.16..UNEMPLOYED\" \"PERCENT.AGED.25..WITHOUT.HIGH.SCHOOL.DIPLOMA\" \"PERCENT.AGED.UNDER.18.OR.OVER.64\" \"PER.CAPITA.INCOME.\" \"HARDSHIP.INDEX\"])", "census.hex");
      Keyed.remove(Key.make("subset_34"));

      Frame subset_35 = exec_str("(cols  crimes.hex [-3])", "subset_35");
      Frame subset_36 = exec_str("(cols weather.hex [-1])", "subset_36");

      subset_36 = exec_str("(colnames= subset_36 [0 1 2 3 4 5] [\"Month\" \"Day\" \"Year\" \"maxTemp\" \"meanTemp\" \"minTemp\"])", "subset_36");

      crimes.remove();
      weather.remove();

      // nary_op_37 = merge( X Y ); Vecs in X & nary_op_37 shared
      Frame nary_op_37 = exec_str("(merge subset_35 census.hex TRUE FALSE)", "nary_op_37");

      // nary_op_38 = merge( nary_op_37 subset_36); Vecs in nary_op_38 and nary_pop_37 and X shared
      Frame subset_41 = exec_str("(rows (tmp= nary_op_38 (merge nary_op_37 subset_36 TRUE FALSE)) (tmp= binary_op_40 (<= (tmp= nary_op_39 (h2o.runif nary_op_38 30792152736.5179)) #0.8)))", "subset_41");

      // Standard "head of 10 rows" pattern for printing
      Frame subset_44 = exec_str("(rows subset_41 [0:10])", "subset_44");
      Keyed.remove(Key.make("subset_44"));
      Keyed.remove(Key.make("subset_44"));
      Keyed.remove(Key.make("binary_op_40"));
      Keyed.remove(Key.make("nary_op_37"));

      Frame subset_43 = exec_str("(rows nary_op_38 (tmp= binary_op_42 (> nary_op_39 #0.8)))", "subset_43");

      // Chicago demo continues on past, but this is all I've captured for now

      checkSaneFrame();

    } finally {
      Exec.exec("(setTimeZone \""+oldtz+"\")"); // Restore time zone (which is global, and will affect following tests)
      if( weather != null ) weather.remove();
      if( crimes  != null ) crimes .remove();
      if( census  != null ) census .remove();

      for( String s : new String[]{"nary_op_5", "unary_op_6", "unary_op_7", "unary_op_8", "binary_op_9",
                                   "unary_op_10", "unary_op_11", "unary_op_12", "binary_op_13",
                                   "binary_op_14", "binary_op_15", "nary_op_16", "binary_op_17",
                                   "binary_op_18", "binary_op_19", "binary_op_20", "binary_op_21",
                                   "binary_op_22", "binary_op_23", "binary_op_24", "binary_op_25",
                                   "nary_op_26", "nary_op_27", "nary_op_28", "unary_op_29", "binary_op_30",
                                   "binary_op_31", "binary_op_32", "subset_33", "subset_34", "subset_35",
                                   "subset_36", "nary_op_37", "nary_op_38", "nary_op_39", "binary_op_40",
                                   "subset_41", "binary_op_42", "subset_43", "subset_44", } )
        Keyed.remove(Key.make(s));
    }
  }

}
