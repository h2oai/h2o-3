#!/bin/bash
cd testdir_single_jvm
ls -1 test*py > n0
sed -i 's!^!./n0.doit !' n0
sed -i 's!$! $*!' n0
sed -i 's!\(.*cloud.*\)!# \1!' ./n0
sed -i 's!\(.*with_a_browser.*\)!# \1!' ./n0
chmod +x ./n0

cd ../testdir_multi_jvm
ls -1 test*py > n0
sed -i 's!^!./n0.doit !' n0
sed -i 's!$! $*!' n0
sed -i 's!\(.*cloud.*\)!# \1!' ./n0
sed -i 's!\(.*with_a_browser.*\)!# \1!' ./n0
chmod +x ./n0

# cd ../testdir_hosts
# ls -1 test*py > n0
# sed -i 's!^!./n0.doit !' n0
# sed -i 's!$! $*!' n0
# sed -i 's!\(.*cloud.*\)!# \1!' ./n0
# sed -i 's!\(.*with_a_browser.*\)!# \1!' ./n0
# 
# cd ../testdir_0xdata_only
# ls -1 test*py > n0
# sed -i 's!^!./n0.doit !' n0
# sed -i 's!$! $*!' n0
# sed -i 's!\(.*cloud.*\)!# \1!' ./n0
# sed -i 's!\(.*with_a_browser.*\)!# \1!' ./n0
# 
# cp ../testdir_multi_jvm/n0 n1
# sed -i 's!n0!n1!' ./n1
