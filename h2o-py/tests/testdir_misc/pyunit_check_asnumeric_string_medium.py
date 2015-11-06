import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pyunit_asnumeric_string():

  small_test = "bigdata/laptop/lending-club/LoanStats3a.csv"

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
  assert data['int_rate'].gsub('%','').trim().asnumeric().isna().sum() == 3


if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_asnumeric_string)
else:
  pyunit_asnumeric_string()