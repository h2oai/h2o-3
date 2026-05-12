#!/bin/sh

test_dir=$1
image=$2

test_dir_full_path=$(pwd)/"$test_dir"

mkdir -p "${test_dir}"/model
mkdir -p "${test_dir}"/output

#rm "${test_dir}"/checkpoints/*
rm "${test_dir}"/model/*
rm "${test_dir}"/output/*

docker run -v "$test_dir_full_path":/opt/ml --rm "${image}" train
