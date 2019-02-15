package hex.mojo;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.MojoModel;

public class GenericModelOutput extends Model.Output {
    
    private final ModelCategory _modelCategory;
    private final int _nfeatures;
    

    public GenericModelOutput(final MojoModel mojoModel) {
        _isSupervised = mojoModel._supervised;
        _domains = mojoModel._domains;
        _origDomains = mojoModel._domains;
        _hasOffset = mojoModel._offsetColumn != null;
        _hasWeights = false;
        _hasFold = false;
        _distribution = mojoModel._modelClassDistrib;
        _priorClassDist = mojoModel._priorClassDistrib;
        _names = mojoModel._names;
        
        _modelCategory = mojoModel._category;
        _nfeatures = mojoModel._nfeatures;
    }

    @Override
    public ModelCategory getModelCategory() {
        return _modelCategory;
    }

    @Override
    public int nfeatures() {
        return _nfeatures;
    }
}
