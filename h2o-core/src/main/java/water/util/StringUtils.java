package water.util;

import water.parser.BufferedString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * String manipulation utilities.
 */
public class StringUtils {

  /**
   * Print exception stack trace into a string.
   *
   * @param t  an exception
   * @return string containing pretty printed exception
   */
  public static String toString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * Convenience function to test whether a string is empty.
   * @param s String to test
   * @return True if the string is either null or empty, false otherwise
   */
  public static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }
  public static boolean isNullOrEmpty(BufferedString s) {
    return s == null || s.length() == 0;
  }

  /**
   * Expand ~ to user.home
   * @param path that can (but doesn't have to) contain a tilde (~)
   * @return expanded path
   */
  public static String expandPath(String path) {
    return path.replaceFirst("^~", System.getProperty("user.home"));
  }

  public static String cleanString(String s) {
    //Tokenization/string cleaning for all datasets except for SST.
    //        Original taken from https://github.com/yoonkim/CNN_sentence/blob/master/process_data.py
    String string = s;
    string = string.replaceAll("[^A-Za-z0-9(),!?\\'\\`]", " ");
    string = string.replaceAll("'s", " 's");
    string = string.replaceAll("'ve", " 've");
    string = string.replaceAll("n't", " n't");
    string = string.replaceAll("'re", " 're");
    string = string.replaceAll("'d", " 'd");
    string = string.replaceAll("'ll", " 'll");
    string = string.replaceAll(",", " , ");
    string = string.replaceAll("!", " ! ");
    string = string.replaceAll("\\(", " ( ");
    string = string.replaceAll("\\)", " ) ");
    string = string.replaceAll("\\?", " ? ");
    string = string.replaceAll("\\s{2,}", " ");
    return string.trim().toLowerCase();
  }

  public static String[] tokenize(String text) {
    // System.out.println(cleanString(text));
    return cleanString(text).split(" ");
  }

  public static int[] tokensToArray(String[] tokens, int padToLength, Map<String, Integer> dict) {
    assert(dict!=null);
    int len = tokens.length;
    int pad = padToLength - len;
    int[] data = new int[padToLength];
    int ix = 0;
    for (String t : tokens) {
      Integer val = dict.get(t);
      int index;
      if (val == null) {
        index = dict.size();
        dict.put(t, index);
      }
      else {
        index = val;
      }
      data[ix] = index;
      ix += 1;
    }
    for (int i = 0; i < pad; i++) {
      int index = dict.get(PADDING_SYMBOL);
      data[ix] = index;
      ix += 1;
    }
    return data;
  }

  public static String PADDING_SYMBOL = "</s>";

  public static ArrayList<int[]> texts2array(ArrayList<String> texts) {
    int maxlen = 0;
    int index = 0;
    Map<String, Integer> dict = new HashMap<>();
    dict.put(PADDING_SYMBOL, index);
    index += 1;
    for (String text : texts) {
      String[] tokens = tokenize(text);
      for (String token : tokens) {
        if (!dict.containsKey(token)) {
          dict.put(token, index);
          index += 1;
        }
      }
      int len = tokens.length;
      if (len > maxlen) maxlen = len;
    }
//    System.out.println(dict);
//    System.out.println("maxlen " + maxlen);
//    System.out.println("dict size " + dict.size());

    ArrayList<int[]> array = new ArrayList<>();
    for (String text: texts) {
      int[] data = tokensToArray(tokenize(text), maxlen, dict);
      //   System.out.println(text);
      //   System.out.println(Arrays.toString(data));
      array.add(data);
    }
    return array;
  }

}
