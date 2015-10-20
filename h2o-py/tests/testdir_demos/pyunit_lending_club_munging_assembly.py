


from h2o.assembly import *
from h2o.transforms.preprocessing import *

def lending_club_munging_assembly():

  small_test = [pyunit_utils.locate("bigdata/laptop/lending-club/LoanStats3a.csv")]

  # lending-club munging assembly
  print "Import and Parse data"

  col_names = ['id', 'member_id', 'loan_amnt', 'funded_amnt', 'funded_amnt_inv', 'term',
               'int_rate', 'installment', 'grade', 'sub_grade', 'emp_title', 'emp_length',
               'home_ownership', 'annual_inc', 'verification_status', 'issue_d', 'loan_status',
               'pymnt_plan', 'url', 'desc', 'purpose', 'title', 'zip_code', 'addr_state', 'dti',
               'delinq_2yrs', 'earliest_cr_line', 'inq_last_6mths', 'mths_since_last_delinq',
               'mths_since_last_record', 'open_acc', 'pub_rec', 'revol_bal', 'revol_util',
               'total_acc', 'initial_list_status', 'out_prncp', 'out_prncp_inv',
               'total_pymnt', 'total_pymnt_inv', 'total_rec_prncp', 'total_rec_int',
               'total_rec_late_fee', 'recoveries', 'collection_recovery_fee', 'last_pymnt_d',
               'last_pymnt_amnt', 'next_pymnt_d', 'last_credit_pull_d',
               'collections_12_mths_ex_med', 'mths_since_last_major_derog', 'policy_code']
  col_types = ['Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Enum', 'Enum', 'Numeric',
               'Enum', 'Enum', 'Enum', 'Enum', 'Enum', 'Numeric', 'Enum', 'Enum', 'Enum', 'Enum',
               'String', 'Enum', 'Enum', 'Enum', 'Enum', 'Enum', 'Numeric', 'Numeric', 'Enum',
               'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Enum', 'Numeric',
               'Enum', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric', 'Numeric',
               'Numeric', 'Numeric', 'Enum', 'Numeric', 'Enum', 'Enum', 'Numeric', 'Enum', 'Numeric']

  types = dict(zip(col_names,col_types))
  types["int_rate"]   = "String"
  types["revol_util"] = "String"
  types["emp_length"] = "String"

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
  assembly.to_pojo("LendingClubMungingDemo") #, path="/Users/spencer/Desktop/munging_pojo/lending_club_demo", get_jar=True)

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


lending_club_munging_assembly()
