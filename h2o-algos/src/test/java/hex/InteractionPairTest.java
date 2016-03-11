package hex;

import org.junit.Test;

/**
 * Created by spencer on 3/5/16.
 */
public class InteractionPairTest {

  @Test public void testPairs() {
    String i1 = "1:2,4,5\n2:7,47,9";
    String i2 = "1[Dallas,New York]:4[Blue,Chartreuse],901[Charmander,Koffing,Newt Gingrich]\n1[San Francisco,Portland,Austin]:999[Blue Cheese,Zorpo Ecstatic Thrift Protocol]";
    DataInfo.InteractionPair[] pairs1 = DataInfo.InteractionPair.read(i1);
    DataInfo.InteractionPair[] pairs2 = DataInfo.InteractionPair.read(i2);

    for(DataInfo.InteractionPair ip: pairs1)
      System.out.println(ip);

    for(DataInfo.InteractionPair ip: pairs2)
      System.out.println(ip);
  }

  @Test public void testGeneratePairs() {
    DataInfo.InteractionPair[] pairs = DataInfo.InteractionPair.generatePairwiseInteractions(0,10);
    for(DataInfo.InteractionPair ip: pairs)
      System.out.println(ip);

    DataInfo.InteractionPair[] pairs2 = DataInfo.InteractionPair.generatePairwiseInteractions(40,99);
    for(DataInfo.InteractionPair ip: pairs2)
      System.out.println(ip);

    DataInfo.InteractionPair[] pairs3 = DataInfo.InteractionPair.generatePairwiseInteractions(0,8,2,99,90);
    for(DataInfo.InteractionPair ip: pairs3)
      System.out.println(ip);
  }
}
