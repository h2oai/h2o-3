from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



from h2o.assembly import *
from h2o.transforms.preprocessing import *

def lending_club_munging_assembly():

  small_test = [pyunit_utils.locate("bigdata/laptop/lending-club/LoanStats3a.csv")]

  # lending-club munging assembly
  print("Import and Parse data")

  types = {"int_rate":"String", "revol_util":"String", "emp_length":"String", "earliest_cr_line":"String", "issue_d":"String", "last_credit_pull_d":"Factor"}

  data = h2o.import_file(path=small_test, col_types=types)
  data[["int_rate","revol_util","emp_length"]].show()

  assembly = H2OAssembly(
    steps=[
      # munge int_rate column in place
      # strip %, trim ws, convert to double
      ("intrate_rm_junk_char", H2OColOp(op=H2OFrame.gsub,      col="int_rate", inplace=True, pattern="%", replacement="")),  # strip %
      ("intrate_trim_ws",      H2OColOp(op=H2OFrame.trim,      col="int_rate", inplace=True)),                               # trim ws
      ("intrate_as_numeric",   H2OColOp(op=H2OFrame.asnumeric, col="int_rate", inplace=True)),                               # string -> double

      # munge the revol_util in the same way as the int_rate column
      ("revol_rm_junk_char", H2OColOp(op=H2OFrame.gsub,      col="revol_util", inplace=True, pattern="%", replacement="")),  # strip %
      ("revol_trim_ws",      H2OColOp(op=H2OFrame.trim,      col="revol_util", inplace=True)),                               # trim ws
      ("revol_as_numeric",   H2OColOp(op=H2OFrame.asnumeric, col="revol_util", inplace=True)),                               # string -> double

      # munge earliest_cr_line column (mm-YYYY format)
      # split into Month and Year columns
      ("earliest_cr_line_split", H2OColOp(H2OFrame.strsplit, col="earliest_cr_line", inplace=False, new_col_name=["earliest_cr_line_Month","earliest_cr_line_Year"], pattern="-")),  # split on '-'
      ("earliest_cr_line_Year_as_numeric", H2OColOp(op=H2OFrame.asnumeric, col="earliest_cr_line_Year", inplace=True)),                                                              # string -> double

      # munge issue_d column in same way as earliest_cr_line column
      ("issue_date_split", H2OColOp(op=H2OFrame.strsplit, col="issue_d", inplace=False, new_col_name=["issue_d_Month", "issue_d_Year"], pattern="-")),                               # split on '-'
      ("issue_d_Year_as_numeric", H2OColOp(op=H2OFrame.asnumeric, col="issue_d_Year", inplace=True)),                                                                                # string -> double

      # do some munging of the emp_length column
      ("emp_length_rm_years",  H2OColOp(op=H2OFrame.gsub, col="emp_length", inplace=True, pattern="([ ]*+[a-zA-Z].*)|(n/a)", replacement="")),  # remove " year" and " years", also translate n/a to ""
      ("emp_length_trim",      H2OColOp(op=H2OFrame.trim, col="emp_length", inplace=True)),                                                     # trim all the WS off
      ("emp_length_lt1_point5",H2OColOp(op=H2OFrame.gsub, col="emp_length", inplace=True, pattern="< 1",    replacement="0.5")),                # translate < 1 => 0.5
      ("emp_length_10plus",    H2OColOp(op=H2OFrame.gsub, col="emp_length", inplace=True, pattern="10\\+",    replacement="10")),               # translate 10+ to 10
      ("emp_length_as_numeric",H2OColOp(op=H2OFrame.asnumeric, col="emp_length", inplace=True)),                                                # string -> double

      # compute credit length
      ("credit_length", H2OBinaryOp(op=H2OAssembly.minus, col="issue_d_Year",inplace=False, new_col_name="longest_credit_length",right=H2OCol("earliest_cr_line_Year")))

    ])

  res = assembly.fit(data)
  res.show()
  assembly.to_pojo("LendingClubMungingDemo")#, path="/Users/spencer/Desktop/munging_pojo/lending_club_demo", get_jar=True)

  y="int_rate"
  x=["loan_amnt", "longest_credit_length", "revol_util", "emp_length",
     "home_ownership", "annual_inc", "purpose", "addr_state", "dti",
     "delinq_2yrs", "total_acc", "verification_status", "term"]

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  model = H2OGradientBoostingEstimator(model_id="InterestRateModel",
                                       score_each_iteration=False,
                                       ntrees=100,
                                       max_depth=5,
                                       learn_rate=0.05)

  model.train(x=x, y=y, training_frame=data)


  model.download_pojo() # path="/Users/spencer/Desktop/munging_pojo/lending_club_demo"


  # Java API Usage:
  #  LendingClubMungingDemo munger = new LendingClubMungingDemo();   // instantiate a new munging pojo
  #  RowData row = myRowDataBuilder(<<tuple of data from stream>>);  // fill in a RowData object (just a wrapper on HashMap, from hex.genmodel)
  #  row = munger.fit(row);                                          // call fit on the row, and return the mutated row (easy!)

if __name__ == "__main__":
    pyunit_utils.standalone_test(lending_club_munging_assembly)
else:
    lending_club_munging_assembly()
