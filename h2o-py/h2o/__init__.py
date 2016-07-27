# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`h2o` -- module for using H2O services.

(please add description).
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.h2o import (connect, init, api, connection,
                     lazy_import, upload_file, import_file, import_sql_table, import_sql_select,
                     parse_setup, parse_raw, assign, get_model, get_grid, get_frame,
                     show_progress, no_progress, log_and_echo, remove, remove_all, rapids,
                     ls, frame, frames, create_frame,
                     download_pojo, download_csv, download_all_logs, save_model, load_model, export_file,
                     cluster_status, cluster_info, shutdown, network_test,
                     interaction, as_list,
                     get_timezone, set_timezone, list_timezones,
                     demo, make_metrics)

__version__ = "SUBST_PROJECT_VERSION"

# __all__ = ['assembly', 'astfun', 'connection', 'cross_validation', 'display',
#            'expr', 'frame', 'group_by', 'h2o',
#            'job', 'two_dim_table', 'estimators', 'grid', 'model', 'transforms']

__all__ = ("connect", "init", "api", "connection", "upload_file", "lazy_import", "import_file", "import_sql_table",
           "import_sql_select", "parse_setup", "parse_raw", "assign", "get_model", "get_grid", "get_frame",
           "show_progress", "no_progress", "log_and_echo", "remove", "remove_all", "rapids", "ls", "frame",
           "frames", "download_pojo", "download_csv", "download_all_logs", "save_model", "load_model", "export_file",
           "cluster_status", "cluster_info", "shutdown", "create_frame", "interaction", "as_list", "network_test",
           "set_timezone", "get_timezone", "list_timezones", "demo", "make_metrics")
