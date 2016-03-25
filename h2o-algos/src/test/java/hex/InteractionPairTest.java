package hex;

import org.junit.Test;

/**
 * Created by spencer on 3/5/16.
 */
public class InteractionPairTest {

  @Test public void testPairs() {
    String i1 = "1:2,4,5\n2:7,47,9";
    String i2 = "1[Dallas,New York]:4[Blue,Chartreuse],901[Charmander,Koffing,Newt Gingrich]\n1[San Francisco,Portland,Austin]:999[Blue Cheese,Zorpo Ecstatic Thrift Protocol]";
    Model.InteractionPair[] pairs1 = Model.InteractionPair.read(i1);
    Model.InteractionPair[] pairs2 = Model.InteractionPair.read(i2);

    for(Model.InteractionPair ip: pairs1)
      System.out.println(ip);

    for(Model.InteractionPair ip: pairs2)
      System.out.println(ip);
  }

  @Test public void testGeneratePairs() {
    Model.InteractionPair[] pairs = Model.InteractionPair.generatePairwiseInteractions(0,10);
    for(Model.InteractionPair ip: pairs)
      System.out.println(ip);

    Model.InteractionPair[] pairs2 = Model.InteractionPair.generatePairwiseInteractions(40,99);
    for(Model.InteractionPair ip: pairs2)
      System.out.println(ip);

    Model.InteractionPair[] pairs3 = Model.InteractionPair.generatePairwiseInteractionsFromList(0,8,2,99,90);
    for(Model.InteractionPair ip: pairs3)
      System.out.println(ip);
  }
}
