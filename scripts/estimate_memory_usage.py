#!/usr/bin/python3

import argparse
import math


def check_nonnegative(value):
    value_as_int = int(value)
    if value_as_int < 0:
        raise argparse.ArgumentTypeError("%s is an invalid value. Only non-negative integers allowed." % value)
    return value_as_int

parser = argparse.ArgumentParser(description='Estimate memory needed to run H2O for a given dataset. Prints memory estimate in GB to standard output.')
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

BASE_MEM_REQUIREMENT_MB = 32
SAFETY_FACTOR = 4
BYTES_IN_MB = 1024 * 1024
BYTES_IN_GB = 1024 * BYTES_IN_MB

unknown_cols = args.ncols - known_cols

unknown_size = 8
unknown_requirement = unknown_cols * args.nrows * unknown_size

num_size = 8
num_requirement = args.n_num_cols * args.nrows * num_size

string_size = 128
string_requirement = string_size * args.n_string_cols* args.nrows

uuid_size = 16
uuid_requirement = uuid_size * args.n_uuid_cols * args.nrows

cat_size = 2
cat_requirement = cat_size * args.n_cat_cols * args.nrows

time_size = 8
time_requirement = time_size * args.n_time_cols * args.nrows

data_requirement = unknown_requirement + num_requirement + string_requirement + uuid_requirement + cat_requirement + time_requirement

mem_req = (BASE_MEM_REQUIREMENT_MB * BYTES_IN_MB + data_requirement) * SAFETY_FACTOR / BYTES_IN_GB

mem_req_rounded = math.ceil(mem_req)

print(mem_req_rounded)

