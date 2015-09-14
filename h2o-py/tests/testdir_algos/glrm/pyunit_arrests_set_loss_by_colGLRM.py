import sys
sys.path.insert(1, "../../../")
import h2o, tests

def glrm_set_loss_by_col():
    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(h2o.locate("smalldata/pca_test/USArrests.csv"))
    arrestsH2O.describe()
    
    print "H2O GLRM with loss by column = L1, Quadratic, Quadratic, Huber"
    glrm_h2o = h2o.glrm(x=arrestsH2O, k=3, loss="Quadratic", loss_by_col=["L1","Huber"], loss_by_col_idx=[0,3], regularization_x="None", regularization_y="None")
    glrm_h2o.show()

if __name__ == "__main__":
    tests.run_test(sys.argv, glrm_set_loss_by_col)