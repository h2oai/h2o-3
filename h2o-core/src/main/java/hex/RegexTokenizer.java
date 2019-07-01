package hex;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;

/**
 * RegexTokenizer splits rows of a given Frame into delimited sequences of tokens using a regular expression.
 * The output structure is suitable for use in the word2vec algorithm.
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * final RegexTokenizer tokenizer = new RegexTokenizer.Builder()
 *   .setRegex("[,;]")
 *   .setMinLength(2)
 *   .setToLowercase(true)
 *   .create();
 * final Frame tokens = tokenizer.transform(inputFrame);
 * }
 * </pre>
 */
public class RegexTokenizer extends MRTask<RegexTokenizer> {
  private final String _regex;
  private final boolean _toLowercase;
  private final int _minLength;

  public RegexTokenizer(String regex) {
    this(regex, false, 0);
  }

  private RegexTokenizer(String regex, boolean toLowercase, int minLength) {
    _regex = regex;
    _toLowercase = toLowercase;
    _minLength = minLength;
  }

  @Override
  public void map(Chunk[] cs, NewChunk nc) {
    BufferedString tmpStr = new BufferedString();
    for (int row = 0; row < cs[0]._len; row++) {
      for (Chunk chk : cs) {
        if (chk.isNA(row)) 
          continue; // input NAs are skipped
        String str = chk.atStr(tmpStr, row).toString();
        if (_toLowercase) {
          str = str.toLowerCase();
        }
        String[] ss = str.split(_regex);
        for (String s : ss) {
          if (s.length() >= _minLength) {
            nc.addStr(s);
          }
        }
      }
      nc.addNA(); // sequences of tokens are delimited by NAs 
    }
  }

  /**
   * Tokenizes a given Frame
   * @param input Input Frame is expected to only contain String columns. Each row of the Frame represents a logical
   *              sentence. The sentence can span one or more cells of the row.
   * @return Frame made of a single String column where original sentences are split into tokens and delimited by NAs.
   */
  public Frame transform(Frame input) {
    return doAll(Vec.T_STR, input).outputFrame();
  }
  
  public static class Builder {
    private String _regex;
    private boolean _toLowercase;
    private int _minLength;

    public Builder setRegex(String regex) {
      _regex = regex;
      return this;
    }

    public Builder setToLowercase(boolean toLowercase) {
      _toLowercase = toLowercase;
      return this;
    }

    public Builder setMinLength(int minLength) {
      _minLength = minLength;
      return this;
    }

    public RegexTokenizer create() {
      return new RegexTokenizer(_regex, _toLowercase, _minLength);
    }
  }

}
