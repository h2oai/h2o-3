package hex.ensemble;

import hex.Model.Parameters;

public interface MetalearnerProvider<M extends Metalearner, P extends Parameters> {

    String getName();

    P newParameters();

    M newInstance();
}
