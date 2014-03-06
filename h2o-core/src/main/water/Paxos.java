package water;

import java.util.HashMap;

public class Paxos { 
  public static boolean _cloudLocked;
  public static HashMap<H2ONode.H2Okey,H2ONode> PROPOSED = new HashMap();
  public static void doHeartbeat(H2ONode node) { }
}
