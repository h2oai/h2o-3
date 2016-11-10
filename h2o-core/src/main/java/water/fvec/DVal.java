package water.fvec;

import water.parser.BufferedString;

/**
 * Created by tomas on 10/22/16.
 */
public class DVal {
  public boolean _missing;

  public enum type {N, D, S, U}

  type _t;
  public long _m;
  public int _e;
  public double _d;
  public BufferedString _str = new BufferedString();
}
