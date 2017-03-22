from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from random import randint
from h2o.transforms.decomposition import H2OPCA

def pca_pubdev_4167_OOM():
  """
  This pyunit is written to make sure PCA works with customer data.  It is mainly used by customer to verify
  PCA operations and not to be used as a regular test since I do not want to expose customer data.
  """
  h2o.remove_all()
  transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"]   # make sure we check all tranforms
  transformN = transform_types[randint(0, len(transform_types)-1)]
  print("transform used on dataset is {0}.\n".format(transformN))

  training_data = h2o.import_file(path=pyunit_utils.locate("/Users/wendycwong/gitBackup/SDatasets/pubdev_4167_Avkash/m120K.tar"))  # Nidhi: import may not work

  gramSVDPCA = H2OPCA(k=training_data.ncols, transform=transformN)
  gramSVDPCA.train(x=list(range(0, training_data.ncols)), training_frame=training_data)

  powerSVDPCA = H2OPCA(k=training_data.ncols, transform=transformN, pca_method="Power")
  powerSVDPCA.train(x=list(range(0, training_data.ncols)), training_frame=training_data)

  # compare singular values and stuff between power and GramSVD methods
  print("@@@@@@  Comparing eigenvalues between GramSVD and Power...\n")
  pyunit_utils.assert_H2OTwoDimTable_equal(gramSVDPCA._model_json["output"]["importance"],
                                           powerSVDPCA._model_json["output"]["importance"],
                                           ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                           tolerance=1e-5, check_all=False)
  print("@@@@@@  Comparing eigenvectors between GramSVD and Power...\n")
  # compare singular vectors
  pyunit_utils.assert_H2OTwoDimTable_equal(gramSVDPCA._model_json["output"]["eigenvectors"],
                                           powerSVDPCA._model_json["output"]["eigenvectors"],
                                           powerSVDPCA._model_json["output"]["names"], tolerance=1e-1,
                                           check_sign=True)

if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_pubdev_4167_OOM)
else:
  pca_pubdev_4167_OOM()
