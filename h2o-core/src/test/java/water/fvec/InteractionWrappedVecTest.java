package water.fvec;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;

import java.util.Arrays;

public class InteractionWrappedVecTest extends TestUtil {
  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  @Test public void testIris() { // basic "can i construct the vec" test
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    try {

      // interact species and sepal len -- all levels (expanded length is 3)
      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(0)._key, fr.vec(4)._key);
      Assert.assertTrue(interactionVec.expandedLength()==3);
      interactionVec.remove();


      // interact species and sepal len -- not all factor levels
      interactionVec =  new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, false,true, false, fr.vec(0)._key, fr.vec(4)._key);
      Assert.assertTrue(interactionVec.expandedLength()==2); // dropped first level
      interactionVec.remove();

      // interact 2 numeric cols: sepal_len sepal_wid
      interactionVec =  new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(0)._key, fr.vec(1)._key);
      Assert.assertTrue(interactionVec.expandedLength()==1);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  // test interacting two enum columns
  @Test public void testTwoEnum() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    int FAKEMAXFORTEST=1000;
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(8)._key, fr.vec(16)._key);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false);
      cid.doAll(fr.vec(8),fr.vec(16));

      // sorted according to occurence Greatest -> Least
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vec(8).domain(), fr.vec(16).domain());

      String modeDomain = domain[0];
      Arrays.sort(domain); // want to compare with interactionVec, so String sort them
      System.out.println(modeDomain);

      Assert.assertArrayEquals(interactionVec.domain(), domain);

      Assert.assertTrue(interactionVec.expandedLength()==domain.length);
      interactionVec.remove();

      // don't include all cat levels
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, false, true, false, fr.vec(8)._key, fr.vec(16)._key);
      Assert.assertTrue(interactionVec.expandedLength()==286);

      System.out.println(interactionVec.mode());
      System.out.println(interactionVec.domain()[interactionVec.mode()]);
      System.out.println(Arrays.toString(interactionVec.getBins()));

      Assert.assertTrue(modeDomain.equals(interactionVec.domain()[interactionVec.mode()]));

    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  // test with enum restrictions
  @Test public void testEnumLimits() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;

    int FAKEMAXFORTEST=1000;

    String[] A = new String[]{"US", "UA", "WN", "HP"};
    String[] B = new String[]{"PIT", "DEN"};
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, A, B, true, true, false, fr.vec(8)._key, fr.vec(16)._key);

      int[] a = new int[A.length];
      int[] b = new int[B.length];
      int idx=0;
      for(String s:A) a[idx++]= Arrays.asList(fr.vec(8).domain()).indexOf(s);
      idx=0;
      for(String s:B) b[idx++]= Arrays.asList(fr.vec(16).domain()).indexOf(s);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false,a,b);
      cid.doAll(fr.vec(8), fr.vec(16));
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vec(8).domain(), fr.vec(16).domain());
      Arrays.sort(domain);
      Assert.assertArrayEquals(interactionVec.domain(), domain);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }


  Frame makeFrame(long rows) {
    CreateFrame cf = new CreateFrame();
    cf.rows = rows;
    cf.cols = 10;
    cf.categorical_fraction = 0.7;
    cf.integer_fraction = 0.1;
    cf.missing_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = false;
    cf.seed = 1234;
    return cf.execImpl().get();
  }


  @Test public void testMultiChk1() {  //previous tests, but multichk
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    try {

      fr = makeFrame(1<<20);
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(0)._key, fr.vec(2)._key);
      Assert.assertTrue(interactionVec.expandedLength()==5);
      interactionVec.remove();


      interactionVec =  new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, false, true, false, fr.vec(1)._key, fr.vec(4)._key);
      Assert.assertTrue(interactionVec.expandedLength()==4); // dropped first level
      interactionVec.remove();

      interactionVec =  new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(0)._key, fr.vec(1)._key);
      Assert.assertTrue(interactionVec.expandedLength()==1);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  @Test public void testMultiChk2() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    int FAKEMAXFORTEST=1000;
    try {
      fr = makeFrame(1 << 20);
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, true, true, false, fr.vec(2)._key, fr.vec(4)._key);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false);
      cid.doAll(fr.vec(2),fr.vec(4));

      // sorted according to occurence Greatest -> Least
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vec(2).domain(), fr.vec(4).domain());

      String modeDomain = domain[0];
      Arrays.sort(domain); // want to compare with interactionVec, so String sort them
      System.out.println(modeDomain);

      Assert.assertArrayEquals(interactionVec.domain(), domain);

      Assert.assertTrue(interactionVec.expandedLength()==domain.length);
      interactionVec.remove();

      // don't include all cat levels
      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, null, null, false, true, false, fr.vec(2)._key, fr.vec(4)._key);
      Assert.assertTrue(interactionVec.expandedLength()==16);

      System.out.println(interactionVec.mode());
      System.out.println(interactionVec.domain()[interactionVec.mode()]);
      System.out.println(Arrays.toString(interactionVec.getBins()));

      Assert.assertTrue(modeDomain.equals(interactionVec.domain()[interactionVec.mode()]));

    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  @Test public void testMultiChk3() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;

    int FAKEMAXFORTEST=1000;

    String[] A;
    String[] B;
    try {
      fr = makeFrame(1 << 20);
      String[] fullA=fr.vec(3).domain();
      String[] fullB=fr.vec(8).domain();
      A = new String[]{fullA[0],fullA[3],fullA[4] };
      B = new String[]{fullB[1],fullB[0]};

      interactionVec = new InteractionWrappedVec(fr.anyVec().group().addVec(), fr.anyVec()._rowLayout, A, B, true, true, false, fr.vec(3)._key, fr.vec(8)._key);

      int[] a = new int[A.length];
      int[] b = new int[B.length];
      int idx=0;
      for(String s:A) a[idx++]= Arrays.asList(fullA).indexOf(s);
      idx=0;
      for(String s:B) b[idx++]= Arrays.asList(fullB).indexOf(s);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false,a,b);
      cid.doAll(fr.vec(3), fr.vec(8));
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fullA, fullB);
      Arrays.sort(domain);
      Assert.assertArrayEquals(interactionVec.domain(), domain);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }
}
