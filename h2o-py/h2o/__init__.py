# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`h2o` -- module for using H2O services.

"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.h2o import (connect, init, api, connection,
                     lazy_import, upload_file, import_file, import_sql_table, import_sql_select, import_hive_table,
                     parse_setup, parse_raw, assign, deep_copy, get_model, get_grid, get_frame,
                     show_progress, no_progress, enable_expr_optimizations, is_expr_optimizations_enabled,
                     log_and_echo, remove, remove_all, rapids,
                     ls, frame, frames, create_frame,
                     download_pojo, download_csv, download_all_logs, save_model, download_model, upload_model, load_model,
                     export_file,
                     cluster_status, cluster_info, shutdown, network_test, cluster,
                     interaction, as_list,
                     get_timezone, set_timezone, list_timezones,
                     load_dataset, demo, make_metrics, flow, upload_custom_metric, upload_custom_distribution,
                     import_mojo, upload_mojo, print_mojo, load_grid, save_grid)
# We have substantial amount of code relying on h2o.H2OFrame to exist. Thus, we make this class available from
# root h2o module, without exporting it explicitly. In the future this import may be removed entirely, so that
# one would have to import it from h2o.frames.
from h2o.frame import H2OFrame  # NOQA
from h2o.utils.shared_utils import mojo_predict_csv, mojo_predict_pandas

import os
from codecs import open
here = os.path.abspath(os.path.dirname(__file__))

try:
    with open(os.path.join(here, 'buildinfo.txt'), encoding='utf-8') as f:
        __buildinfo__ = f.read()
except:
    __buildinfo__ = "unknown"

try:
    with open(os.path.join(here, 'version.txt'), encoding='utf-8') as f:
        __version__ = f.read()
except:
    __version__ = "0.0.local"

if (__version__.endswith("99999")):
    print(__buildinfo__)

__all__ = ("connect", "init", "api", "connection", "upload_file", "lazy_import", "import_file", "import_sql_table",
           "import_sql_select", "parse_setup", "parse_raw", "assign", "deep_copy", "get_model", "get_grid", "get_frame",
           "show_progress", "no_progress", "enable_expr_optimizations", "is_expr_optimizations_enabled", "log_and_echo",
           "remove", "remove_all", "rapids", "ls", "frame", "import_hive_table",
           "frames", "download_pojo", "download_csv", "download_all_logs", "save_model", "download_model", "upload_model", "load_model", "export_file",
           "cluster_status", "cluster_info", "shutdown", "create_frame", "interaction", "as_list", "network_test",
           "set_timezone", "get_timezone", "list_timezones", "demo", "make_metrics", "cluster", "load_dataset","flow",
           "upload_custom_metric", "upload_custom_distribution",  "mojo_predict_csv", "mojo_predict_pandas", "import_mojo", 
           "upload_mojo", "print_mojo", "load_grid", "save_grid")
