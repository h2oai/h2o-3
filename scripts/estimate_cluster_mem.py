#!/usr/bin/python3

import argparse

import h2o

from h2o import estimate_cluster_mem


def check_nonnegative(value):
    value_as_int = int(value)
    if value_as_int < 0:
        raise argparse.ArgumentTypeError("%s is an invalid value. Only non-negative integers allowed." % value)
    return value_as_int


parser = argparse.ArgumentParser(
    description='Estimate memory needed to run H2O for a given dataset. Prints memory estimate in GB to standard output.')
parser.add_argument('--nrows', dest='nrows', required=True, type=check_nonnegative,
                    help='count of rows in the dataset (required)')
parser.add_argument('--ncols', dest='ncols', required=True, type=check_nonnegative,
                    help='count of columns of all types in the dataset (required)')
parser.add_argument('--n-num-cols', dest='n_num_cols', required=False, type=check_nonnegative, default=0,
                    help='count of numeric collumns in the dataset')
parser.add_argument('--n-string-cols', dest='n_string_cols', required=False, type=check_nonnegative, default=0,
                    help='count of string collumns in the dataset')
parser.add_argument('--n-uuid-cols', dest='n_uuid_cols', required=False, type=check_nonnegative, default=0,
                    help='count of UUID collumns in the dataset')
parser.add_argument('--n-cat-cols', dest='n_cat_cols', required=False, type=check_nonnegative, default=0,
                    help='count of cathegorical collumns in the dataset')
parser.add_argument('--n-time-cols', dest='n_time_cols', required=False, type=check_nonnegative, default=0,
                    help='count of time collumns in the dataset')

args = parser.parse_args()

known_cols = args.n_num_cols + args.n_string_cols + args.n_uuid_cols + args.n_cat_cols + args.n_time_cols

if (args.ncols < known_cols):
    print("count of all columns can not be lower then sum of counts of different types of columns")
    print()
    parser.print_help()
    exit(2)

mem_req_rounded = h2o.estimate_cluster_mem(ncols=args.ncols
                                         , num_cols=args.n_num_cols
                                         , string_cols=args.n_string_cols
                                         , cat_cols=args.n_cat_cols
                                         , uuid_cols=args.n_uuid_cols
                                         , time_cols=args.n_time_cols
                                         , nrows=args.nrows)

print(mem_req_rounded)
