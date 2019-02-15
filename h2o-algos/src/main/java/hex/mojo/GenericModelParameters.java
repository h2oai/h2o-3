package hex.mojo;

import hex.Model;
import water.Key;
import water.fvec.Frame;

public class GenericModelParameters extends Model.Parameters {
    
    public Key<Frame> _mojo_key;
    
    @Override
    public String algoName() {
        return "Generic";
    }

    @Override
    public String fullName() {
        return "Generic Model";
    }

    @Override
    public String javaName() {
        return GenericModel.class.getName();
    }

    @Override
    public long progressUnits() {
        return 100;
    }
}
