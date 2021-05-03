import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile
import subprocess
from subprocess import STDOUT,PIPE
def glm_mojo_all_interaction_test_large():
    seed = 12345
    bigCat = pyunit_utils.random_dataset_enums_only(10000, 1, factorL=30, misFrac=0.00, randSeed=seed)
    bitCat2 = pyunit_utils.random_dataset_enums_only(10000, 1, factorL=20, misFrac=0.00, randSeed=seed)
    smallCats = pyunit_utils.random_dataset_enums_only(10000, 4, factorL=5, misFrac=0.00, randSeed=seed)
    numerics = pyunit_utils.random_dataset_real_only(10000, 5, realR=100, misFrac=0.01, randSeed=seed)
    dataframe = numerics.cbind(smallCats.cbind(bitCat2.cbind(bigCat)))
    dataframe.set_names(["response","n1","n2","n3","n4","c1","c2","c3","c4","c5","c6"])
    xcols = ["n1","n2","n3","n4","c1","c2","c3","c4","c5","c6"]
    interaction_pairs = [("c1", "n1"), ("c5", "n2"), ("c1", "c2"), ("c3", "c5"), ("n3", "n4")]
    params = {'family':"gaussian", 'lambda_search':False, 'interaction_pairs':interaction_pairs, 'standardize':False}
    TMPDIR = tempfile.mkdtemp()
    glmGaussianModel = pyunit_utils.build_save_model_generic(params, xcols, dataframe, "response", "glm", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(glmGaussianModel._id)
    splitFrame = dataframe.split_frame(ratios=[0.001], seed=seed)
    pred_h2o = glmGaussianModel.predict(splitFrame[0])
    h2o.download_csv(splitFrame[0], os.path.join(TMPDIR, 'in.csv'))
    pred_mojo = mojo_predict(TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)
def mojo_predict(tmpdir, mojoname):
    newTest = h2o.import_file(os.path.join(tmpdir, 'in.csv'), header=1)   # Make sure h2o and mojo use same in.csv
    # load mojo and have it do predict
    outFileName = os.path.join(tmpdir, 'out_mojo.csv')
    mojoZip = os.path.join(tmpdir, mojoname) + ".zip"
    genJarDir = str.split(os.path.realpath("__file__"),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # locate directory of genmodel.jar
    java_cmd = ["java", "-ea", "-cp", os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar"),
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", os.path.join(tmpdir, 'in.csv'), "--output",
                outFileName, "--mojo", mojoZip, "--decimal"]
    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    files = os.listdir(tmpdir)
    print("listing files {1} in directory {0}".format(tmpdir, files))
    outfile = os.path.join(tmpdir, 'out_mojo.csv')
    if not os.path.exists(outfile) or os.stat(outfile).st_size == 0:
        print("MOJO SCORING FAILED:")
        print("--------------------")
        print(o.decode("utf-8"))
    print("***** importing file {0}".format(outfile))
    pred_mojo = h2o.import_file(outfile, header=1)  # load mojo prediction in 
    return pred_mojo
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_all_interaction_test_large)
else:
    glm_mojo_all_interaction_test_large()
