package water.util;

import water.H2O;

import java.util.*;

public class ModelUtils {
  public static String[] createConfusionMatrixHeader( long xs[], String ds[] ) {
    String ss[] = new String[xs.length]; // the same length
    for( int i=0; i<ds.length; i++ )
      if( xs[i] >= 0 || (ds[i] != null && ds[i].length() > 0) && !Integer.toString(i).equals(ds[i]) )
        ss[i] = ds[i];
    if( ds.length == xs.length-1 && xs[xs.length-1] > 0 )
      ss[xs.length-1] = "NA";
    return ss;
  }

  /** List of default thresholds */
  public static float[] DEFAULT_THRESHOLDS = new float [] {  0.00f,
    0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f, 0.07f, 0.08f, 0.09f, 0.10f,
    0.11f, 0.12f, 0.13f, 0.14f, 0.15f, 0.16f, 0.17f, 0.18f, 0.19f, 0.20f,
    0.21f, 0.22f, 0.23f, 0.24f, 0.25f, 0.26f, 0.27f, 0.28f, 0.29f, 0.30f,
    0.31f, 0.32f, 0.33f, 0.34f, 0.35f, 0.36f, 0.37f, 0.38f, 0.39f, 0.40f,
    0.41f, 0.42f, 0.43f, 0.44f, 0.45f, 0.46f, 0.47f, 0.48f, 0.49f, 0.50f,
    0.51f, 0.52f, 0.53f, 0.54f, 0.55f, 0.56f, 0.57f, 0.58f, 0.59f, 0.60f,
    0.61f, 0.62f, 0.63f, 0.64f, 0.65f, 0.66f, 0.67f, 0.68f, 0.69f, 0.70f,
    0.71f, 0.72f, 0.73f, 0.74f, 0.75f, 0.76f, 0.77f, 0.78f, 0.79f, 0.80f,
    0.81f, 0.82f, 0.83f, 0.84f, 0.85f, 0.86f, 0.87f, 0.88f, 0.89f, 0.90f,
    0.91f, 0.92f, 0.93f, 0.94f, 0.95f, 0.96f, 0.97f, 0.98f, 0.99f, 1.00f
  };

  //public static void printConfusionMatrix(StringBuilder sb, long[][] cm, String[] domain, boolean html) {
  //  if (cm == null || domain == null) return;
  //  for (long[] aCm : cm) assert (cm.length == aCm.length);
  //  if (html) DocGen.HTML.arrayHead(sb);
  //  // Sum up predicted & actuals
  //  long acts [] = new long[cm   .length];
  //  long preds[] = new long[cm[0].length];
  //  for( int a=0; a<cm.length; a++ ) {
  //    long sum=0;
  //    for( int p=0; p<cm[a].length; p++ ) {
  //      sum += cm[a][p];
  //      preds[p] += cm[a][p];
  //    }
  //    acts[a] = sum;
  //  }
  //  String adomain[] = createConfusionMatrixHeader(acts , domain);
  //  String pdomain[] = createConfusionMatrixHeader(preds, domain);
  //  assert adomain.length == pdomain.length : "The confusion matrix should have the same length for both directions.";
  //
  //  String fmt = "";
  //  String fmtS = "";
  //
  //  // Header
  //  if (html) {
  //    sb.append("<tr class='warning' style='min-width:60px'>");
  //    sb.append("<th>&darr; Actual / Predicted &rarr;</th>");
  //    for (String aPdomain : pdomain)
  //      if (aPdomain != null)
  //        sb.append("<th style='min-width:60px'>").append(aPdomain).append("</th>");
  //    sb.append("<th>Error</th>");
  //    sb.append("</tr>");
  //  } else {
  //    // determine max length of each space-padded field
  //    int maxlen = 0;
  //    for( String s : pdomain ) if( s != null ) maxlen = Math.max(maxlen, s.length());
  //    long lsum = 0;
  //    for( int a=0; a<cm.length; a++ ) {
  //      if( adomain[a] == null ) continue;
  //      for( int p=0; p<pdomain.length; p++ ) { if( pdomain[p] == null ) continue; lsum += cm[a][p]; }
  //    }
  //    maxlen = Math.max(8, Math.max(maxlen, String.valueOf(lsum).length()) + 2);
  //    fmt  = "%" + maxlen + "d";
  //    fmtS = "%" + maxlen + "s";
  //    sb.append(String.format(fmtS, "Act/Prd"));
  //    for( String s : pdomain ) if( s != null ) sb.append(String.format(fmtS, s));
  //    sb.append("   " + String.format(fmtS, "Error\n"));
  //  }
  //
  //  // Main CM Body
  //  long terr=0;
  //  for( int a=0; a<cm.length; a++ ) {
  //    if( adomain[a] == null ) continue;
  //    if (html) {
  //      sb.append("<tr style='min-width:60px'>");
  //      sb.append("<th style='min-width:60px'>").append(adomain[a]).append("</th>");
  //    } else {
  //      sb.append(String.format(fmtS,adomain[a]));
  //    }
  //    long correct=0;
  //    for( int p=0; p<pdomain.length; p++ ) {
  //      if( pdomain[p] == null ) continue;
  //      boolean onDiag = adomain[a].equals(pdomain[p]);
  //      if( onDiag ) correct = cm[a][p];
  //      String id = "";
  //      if (html) {
  //        sb.append(onDiag ? "<td style='min-width: 60px; background-color:LightGreen' "+id+">":"<td style='min-width: 60px;'"+id+">").append(String.format("%,d", cm[a][p])).append("</td>");
  //      } else {
  //        sb.append(String.format(fmt,cm[a][p]));
  //      }
  //    }
  //    long err = acts[a]-correct;
  //    terr += err;
  //    if (html) {
  //      sb.append(String.format("<th  style='min-width: 60px;'>%.05f = %,d / %,d</th></tr>", (double)err/acts[a], err, acts[a]));
  //    } else {
  //      sb.append("   " + String.format("%.05f = %,d / %d\n", (double)err/acts[a], err, acts[a]));
  //    }
  //  }
  //
  //  // Last row of CM
  //  if (html) {
  //    sb.append("<tr style='min-width:60px'><th>Totals</th>");
  //  } else {
  //    sb.append(String.format(fmtS, "Totals"));
  //  }
  //  for( int p=0; p<pdomain.length; p++ ) {
  //    if( pdomain[p] == null ) continue;
  //    if (html) {
  //      sb.append("<td style='min-width:60px'>").append(String.format("%,d", preds[p])).append("</td>");
  //    } else {
  //      sb.append(String.format(fmt, preds[p]));
  //    }
  //  }
  //  long nrows = 0;
  //  for (long n : acts) nrows += n;
  //
  //  if (html) {
  //    sb.append(String.format("<th style='min-width:60px'>%.05f = %,d / %,d</th></tr>", (float)terr/nrows, terr, nrows));
  //    DocGen.HTML.arrayTail(sb);
  //  } else {
  //    sb.append("   " + String.format("%.05f = %,d / %,d\n", (float)terr/nrows, terr, nrows));
  //  }
  //}


  /**
   * Create labels from per-class probabilities with pseudo-random tie-breaking, if needed.
   * @param numK Number of top probabilities to make labels for
   * @param preds Predictions (first element is ignored here: placeholder for a label)
   * @param data Data to break ties (typically, the test set data for this row)
   * @return Array of predicted labels
   */
  public static int[] getPredictions( int numK, float[] preds, double data[] ) {
    assert(numK <= preds.length-1);
    int[] labels = new int[numK];
    // create a sorted mapping from probability to label(s)
    TreeMap<Float, List<Integer> > prob_idx = new TreeMap<>(new Comparator<Float>() {
      @Override
      public int compare(Float o1, Float o2) {
        if (o1 > o2) return -1;
        if (o2 > o1) return 1;
        return 0;
      }
    });
    for (int i = 1; i < preds.length; ++i) {
      final Float prob = preds[i];
      final int label = i-1;
      assert(prob >= 0 && prob <= 1);
      if (prob_idx.containsKey(prob)) {
        prob_idx.get(prob).add(label); //add all ties
      } else {
        // add prob to top K probs only if either:
        // 1) don't have K probs yet
        // 2) prob is greater than the smallest prob in the store -> evict the smallest
        if (prob_idx.size() < numK || prob > prob_idx.lastKey()) {
          List<Integer> li = new LinkedList<>();
          li.add(label);
          prob_idx.put(prob, li);
        }
        // keep size small, only need the best numK probabilities (max-heap)
        if (prob_idx.size()>numK) {
          prob_idx.remove(prob_idx.lastKey());
        }
      }
    }
    assert(!prob_idx.isEmpty());
    assert(prob_idx.size() <= numK); //have at most numK probabilities, maybe less if there are ties

    int i = 0; //which label we are filling in
    while (i < numK && !prob_idx.isEmpty()) {
      final Map.Entry p_id = prob_idx.firstEntry();
      final Float prob = (Float)p_id.getKey(); //max prob.
      final List<Integer> indices = (List<Integer>)p_id.getValue(); //potential candidate labels if there are ties
      if (i + indices.size() <= numK) for (Integer id : indices) labels[i++] = id;
      else {
        // Tie-breaking logic: pick numK-i classes (indices) from the list of indices.
        // if data == null, then pick the first numK-i indices, otherwise break ties pseudo-randomly.
        while (i<numK) {
          assert(!indices.isEmpty());
          long hash = 0;
          if( data != null )
            for( double d : data ) hash ^= Double.doubleToRawLongBits(d+i) >> 6; // drop 6 least significant bits of mantissa (layout of long is: 1b sign, 11b exp, 52b mantissa)
          labels[i++] = indices.remove((int)(Math.abs(hash)%indices.size()));
        }
        assert(i==numK);
      }
      prob_idx.remove(prob);
    }
    assert(i==numK);
    return labels;
  }

  public static int getPrediction(float[] preds, int row) {
    int best=1, tieCnt=0;   // Best class; count of ties
    for( int c=2; c<preds.length; c++) {
      if( preds[best] < preds[c] ) {
        best = c;               // take the max index
        tieCnt=0;               // No ties
      } else if (preds[best] == preds[c]) {
        tieCnt++;               // Ties
      }
    }
    if( tieCnt==0 ) return best-1; // Return zero-based best class
    // Tie-breaking logic
    float res = preds[best];    // One of the tied best results
    int idx = row%(tieCnt+1);   // Which of the ties we'd like to keep
    for( best=1; best<preds.length; best++)
      if( res == preds[best] && --idx < 0 )
        return best-1;          // Return best
    throw H2O.fail();           // Should Not Reach Here
  }

  /**
   *  Utility function to get a best prediction from an array of class
   *  prediction distribution.  It returns index of max value if predicted
   *  values are unique.  In the case of tie, the implementation solve it in
   *  pseudo-random way.
   *  @param preds an array of prediction distribution.  Length of arrays is equal to a number of classes+1.
   *  @return the best prediction (index of class, zero-based)
   */
  public static int getPrediction( float[] preds, double data[] ) {
    int best=1, tieCnt=0;   // Best class; count of ties
    for( int c=2; c<preds.length; c++) {
      if( preds[best] < preds[c] ) {
        best = c;               // take the max index
        tieCnt=0;               // No ties
      } else if (preds[best] == preds[c]) {
        tieCnt++;               // Ties
      }
    }
    if( tieCnt==0 ) return best-1; // Return zero-based best class
    // Tie-breaking logic
    float res = preds[best];    // One of the tied best results
    long hash = 0;              // hash for tie-breaking
    if( data != null )
      for( double d : data ) hash ^= Double.doubleToRawLongBits(d) >> 6; // drop 6 least significants bits of mantisa (layout of long is: 1b sign, 11b exp, 52b mantisa)
    int idx = (int)hash%(tieCnt+1);  // Which of the ties we'd like to keep
    for( best=1; best<preds.length; best++)
      if( res == preds[best] && --idx < 0 )
        return best-1;          // Return best
    throw H2O.fail();           // Should Not Reach Here
  }
}
