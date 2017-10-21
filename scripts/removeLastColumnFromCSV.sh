#! /bin/bash

CSV_DIR="../bigdata/laptop/jira"
CSV_BASENAME="${1:-re0.wc.arff}"
CSV_INPUT_FILE="$CSV_DIR/$CSV_BASENAME.txt"
CSV_OUTPUT_FILE="$CSV_DIR/$CSV_BASENAME.csv"

awk 'BEGIN { FS=","; OFS="," }  {print $NF}' $CSV_INPUT_FILE  | less
awk 'BEGIN { FS=","; OFS="," } NF{NF--};1' <$CSV_INPUT_FILE >$CSV_OUTPUT_FILE
awk 'BEGIN { FS=","; OFS="," }  {print $NF}' $CSV_OUTPUT_FILE | less
