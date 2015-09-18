import sys
sys.path.insert(1, "../../")
import h2o, tests
from h2o import H2OAssembly
from h2o.transforms.preprocessing import *
from h2o import H2OFrame

def assembly_demo():
  fr = h2o.import_file(h2o.locate("smalldata/iris/iris_wheader.csv"))                                                                # import data
  assembly = H2OAssembly(steps=[("col_select",      H2OColSelect(["sepal_len", "petal_len", "class"])),                              # col selection
                                ("cos_sep_len",     H2OColOp(fun=H2OFrame.cos, col="sepal_len", inplace=True)),                      # math operation
                                ("str_cnt_species", H2OColOp(fun=H2OFrame.countmatches, col="class", inplace=False, pattern="s"))])  # string operation

  result = assembly.fit(fr)  # fit the assembly
  result.show()              # show the result of the fit
  # print assembly.to_pojo(pojo_name="GeneratedH2OMungingPojo_001")   # export to pojo
  #
  #



  # java api usage:
  #
  #   String rawRow = framework.nextTuple();
  #   H2OMungingPOJO munger = new GeneratedH2OMungingPojo_001();
  #   EasyPredictModelWrapper model = new EasyPredictModelWrapper(new GeneratedH2OGbmPojo_001());
  #
  #   RowData row = new RowData();
  #   row.fill(rawRow);
  #   row = munger.transform(row);
  #   BinomialModelPrediction pred = model.predictBinomial(row);
  #   // Use prediction!


if __name__ == "__main__":
  tests.run_test(sys.argv, assembly_demo)