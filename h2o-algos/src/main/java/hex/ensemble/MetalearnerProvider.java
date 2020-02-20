package hex.ensemble;

import hex.Model.Parameters;

public interface MetalearnerProvider<M extends Metalearner> {

    String getName();

    M newInstance();
}
