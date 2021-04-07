#!/usr/env/python
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def hive_save_frame():
    connection_url = "jdbc:hive2://localhost:10000/default"
    connection_url_nodb = "jdbc:hive2://localhost:10000/"
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    use_token = os.getenv('KRB_USE_TOKEN', 'false').lower() == 'true'
    if krb_enabled:
        if use_token:
            connection_url += ";auth=delegationToken"
            connection_url_nodb += ";auth=delegationToken"
        else:
            connection_url += ";principal=%s" % os.getenv('HIVE_PRINCIPAL', 'hive/localhost@H2O.AI')
            connection_url_nodb += ";principal=%s" % os.getenv('HIVE_PRINCIPAL', 'hive/localhost@H2O.AI')
        
    username = "hive"
    password = ""

    print("import data")
    prostate_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))

    print("save as csv, managed, custom tmp")
    prostate_hex.save_to_hive(connection_url, "prostate_hex_py_csv", tmp_path="/tmp")
    prostate_hive = h2o.import_sql_table(connection_url, "prostate_hex_py_csv", username, password, fetch_mode="SINGLE")
    pyunit_utils.compare_frames_local(prostate_hex, prostate_hive, prob=1)

    print("save as parquet, managed, db in table name")
    prostate_hex.save_to_hive(connection_url_nodb, "default.prostate_hex_py_parquet", format="parquet")
    prostate_hive = h2o.import_sql_table(connection_url_nodb, "default.prostate_hex_py_parquet", username, password, fetch_mode="SINGLE")
    pyunit_utils.compare_frames_local(prostate_hex, prostate_hive, prob=1)

    print("save as parquet, external")
    prostate_hex.save_to_hive(connection_url, "prostate_hex_py_parquet_ext", format="parquet", table_path="/user/hive/ext/prostate_hex_py_parquet")
    prostate_hive = h2o.import_sql_table(connection_url, "prostate_hex_py_parquet_ext", username, password, fetch_mode="SINGLE")
    pyunit_utils.compare_frames_local(prostate_hex, prostate_hive, prob=1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_save_frame)
else:
    hive_save_frame()
