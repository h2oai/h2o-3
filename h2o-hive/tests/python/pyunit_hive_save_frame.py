#!/usr/env/python
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def hive_save_frame():
    connection_url = "jdbc:hive2://localhost:10000/default"
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    use_token = os.getenv('KRB_USE_TOKEN', 'false').lower() == 'true'
    if krb_enabled:
        if use_token:
            connection_url += ";auth=delegationToken"
        else:
            connection_url += ";principal=%s" % os.getenv('HIVE_PRINCIPAL', 'hive/localhost@H2O.AI')
        
    username = "hive"
    password = ""

    # read original
    prostate_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_cat_NA.csv"))

    # save to Hive
    prostate_hex.save_to_hive(connection_url, "prostate_hex_py")

    # read from Hive
    prostate_hive = h2o.import_sql_table(connection_url, "prostate_hex_py", username, password, fetch_mode="SINGLE")
    pyunit_utils.compare_frames_local(prostate_hex, prostate_hive, prob=1)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_save_frame)
else:
    hive_save_frame()
