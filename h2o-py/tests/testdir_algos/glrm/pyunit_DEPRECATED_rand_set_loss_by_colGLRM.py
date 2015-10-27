import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random as rd

def glrm_set_loss_by_col_rand():
    NUM_LOSS = ["Quadratic", "Absolute", "Huber", "Poisson", "Periodic"]
    CAT_LOSS = ["Categorical", "Ordinal"]
    NUM_COLS = [1, 5, 6, 7]
    CAT_COLS = [0, 2, 3, 4]
    
    print "Importing prostate_cat.csv data..."
    prostateH2O = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"), na_strings = ["NA"]*8)
    prostateH2O.describe()
    
    # Fully specify every column's loss function (no need for loss_by_col_idx)
    loss_all = [rd.sample(NUM_LOSS, k=1)[0] if x in NUM_COLS else rd.sample(CAT_LOSS, k=1)[0] for x in xrange(0,8)]
    print "Run GLRM with loss_by_col = [" + ', '.join(loss_all) + "]"
    glrm_h2o = h2o.glrm(x=prostateH2O, k=5, loss_by_col=loss_all)
    glrm_h2o.show()
    
    # Randomly set columns and loss functions
    cat_size = rd.sample(xrange(1,5), 1)
    num_size = rd.sample(xrange(1,5), 1)
    cat_idx = np.random.choice(CAT_COLS, size=cat_size, replace=False)
    num_idx = np.random.choice(NUM_COLS, size=num_size, replace=False)
    loss_by_col_cat = np.random.choice(CAT_LOSS, size=cat_size, replace=True)
    loss_by_col_num = np.random.choice(NUM_LOSS, size=num_size, replace=True)
    
    loss_idx_all = cat_idx.tolist() + num_idx.tolist()
    loss_all = loss_by_col_cat.tolist() + loss_by_col_num.tolist()
    loss_combined = zip(loss_all, loss_idx_all)   # Permute losses and indices in same way for testing
    rd.shuffle(loss_combined)
    loss_all[:], loss_idx_all[:] = zip(*loss_combined)
    
    if(len(loss_all) < prostateH2O.ncol):
        try:
            h2o.glrm(x=prostateH2O, k=5, loss_by_col=loss_all)
            assert False, "Expected GLRM to throw error since column indices not specified"
        except:
            pass
    
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col_idx=loss_idx_all)
        assert False, "Expected GLRM to throw error since losses for columns not specified"
    except:
        pass
        
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col=["Absolute", "Ordinal", "Huber"], loss_by_col_idx = [1,2])
        assert False, "Expected GLRM to throw error since not all column indices specified"
    except:
        pass
        
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col=["Absolute", "Ordinal"], loss_by_col_idx=[1,2,5])
        assert False, "Expected GLRM to throw error since not all losses for columns specified"
    except:
        pass
    
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col="Absolute", loss_by_col_idx=8)
        assert False, "Expected GLRM to throw error since column index 8 is out of bounds (zero indexing)"
    except:
        pass
    
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col=rd.sample(NUM_LOSS,1), loss_by_col_idx=rd.sample(CAT_COLS,1))
        assert False, "Expected GLRM to throw error since numeric loss cannot apply to categorical column"
    except:
        pass
    
    try:
        h2o.glrm(x=prostateH2O, k=5, loss_by_col=rd.sample(CAT_LOSS,1), loss_by_col_idx=rd.sample(NUM_COLS,1))
        assert False, "Expected GLRM to throw error since categorical loss cannot apply to numeric column"
    except:
        pass
    
    print "Run GLRM with loss_by_col = [" + ', '.join(loss_all) + "] and loss_by_col_idx = [" + ', '.join([str(a) for a in loss_idx_all]) + "]"
    glrm_h2o = h2o.glrm(x=prostateH2O, k=5, loss_by_col=loss_all, loss_by_col_idx=loss_idx_all)
    glrm_h2o.show()
    


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_set_loss_by_col_rand)
else:
    glrm_set_loss_by_col_rand()
