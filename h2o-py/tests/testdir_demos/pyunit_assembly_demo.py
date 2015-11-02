import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



from h2o import H2OAssembly
from h2o.transforms.preprocessing import *
from h2o import H2OFrame

def assembly_demo():
  fr = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"), col_types=["numeric","numeric","numeric","numeric","string"])  # import data
  assembly = H2OAssembly(steps=[("col_select",      H2OColSelect(["sepal_len", "petal_len", "class"])),                                # col selection
                                ("cos_sep_len",     H2OColOp(op=H2OFrame.cos, col="sepal_len", inplace=True)),                         # math operation
                                ("str_cnt_species", H2OColOp(op=H2OFrame.countmatches, col="class", inplace=False, pattern="s"))])     # string operation

  result = assembly.fit(fr)  # fit the assembly
  result.show()              # show the result of the fit

  assembly.to_pojo("MungingPojoDemo") #, path="/Users/spencer/Desktop/munging_pojo")  # export POJO


  # java api usage:
  #
  #   String rawRow = framework.nextTuple();
  #   H2OMungingPOJO munger = new GeneratedH2OMungingPojo_001();
  #   EasyPredictModelWrapper model = new EasyPredictModelWrapper(new GeneratedH2OGbmPojo_001());
  #
  #   RowData row = new RowData();
  #   row.fill(rawRow);
  #   row = munger.fit(row);
  #   BinomialModelPrediction pred = model.predictBinomial(row);
  #   // Use prediction!




if __name__ == "__main__":
    pyunit_utils.standalone_test(assembly_demo)
else:
    assembly_demo()
