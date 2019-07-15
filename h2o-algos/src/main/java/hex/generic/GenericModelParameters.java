package hex.generic;

import hex.Model;
import water.Key;
import water.fvec.Frame;

public class GenericModelParameters extends Model.Parameters {

    /**
     * Path of the file with embedded model
     */
    public String _path;
    
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
