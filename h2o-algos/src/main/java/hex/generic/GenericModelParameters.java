package hex.generic;

import hex.Model;
import water.Key;
import water.fvec.Frame;

public class GenericModelParameters extends Model.Parameters {

    /**
     * Path of the file with embedded model
     */
    public String _path;

    /**
     * Key to the file with embedded model
     */
    public Key<Frame> _model_key;
    
    @Override
    public String algoName() {
        return "Generic";
    }

    @Override
    public String fullName() {
        return "MOJO Model";
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
