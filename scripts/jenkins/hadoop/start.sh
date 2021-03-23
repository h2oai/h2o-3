#!/usr/bin/env bash

set -ex

while (( "$#" )); do
  case "$1" in
    --cluster-name)
      clusterName=$2
      shift 2
      ;;
    --clouding-dir)
      cloudingDir=$2
      shift 2
      ;;
    --notify-file)
      notifyFile=$2
      shift 2
      ;;
    --driver-log-file)
      driverLogFile=$2
      shift 2
      ;;
    --hadoop-version)
      hadoopVersion=$2
      shift 2
      ;;
    --job-name)
      jobName=$2
      shift 2
      ;;
    --nodes)
      nodes=$2
      shift 2
      ;;
    --xmx)
      xmx=$2
      shift 2
      ;;
    --extra-mem)
      extraMem=$2
      shift 2
      ;;
    --context-path)
      contextPathArgs="-context_path $2"
      shift 2
      ;;
    --auto-recovery-dir)
      autoRecoveryDir=$2
      shift 2
      ;;
    --auto-recovery-cleanup)
      autoRecoveryCleanup=yes
      shift
      ;;
    --use-external-xgb)
      useExternalXGBoost=yes
      shift
      ;;
    --enable-login)
      enableLogin=yes
      shift
      ;;
    --proxy)
      proxy=yes
      shift
      ;;
    --disown)
      disown=yes
      shift
      ;;
    -*|--*=) # unsupported flags
      echo "Error: Unsupported flag $1" >&2
      exit 1
      ;;
  esac
done

if [ "${useExternalXGBoost}" = "yes" ]; then 
  xgbArgs="-use_external_xgboost"
fi
if [ "${enableLogin}" = "yes" ]; then
  echo "jenkins:${clusterName}" >> ${clusterName}.realm.properties
  loginArgs="-hash_login -login_conf ${clusterName}.realm.properties"
fi
if [ "${proxy}" = "yes" ]; then 
  proxyArgs="-proxy"
fi
if [ "${disown}" = "yes" ]; then 
  disownArgs="-disown"
fi
if [ "${autoRecoveryDir}" != "" ]; then
  if [ "${autoRecoveryCleanup}" = "yes" ]; then
    hdfs dfs -rm -r -f ${autoRecoveryDir}
  fi
  autoRecoveryArgs="-auto_recovery_dir ${autoRecoveryDir}"
fi

rm -fv ${notifyFile} ${driverLogFile}
hdfs dfs -rm -r -f ${cloudingDir}
hadoop jar h2o-hadoop-*/h2o-${hadoopVersion}-assembly/build/libs/h2odriver.jar \
    -jobname ${jobName} -ea \
    -clouding_method filesystem -clouding_dir ${cloudingDir} \
    -n ${nodes} -mapperXmx ${xmx} -baseport 54445 -timeout 720 \
    ${contextPathArgs} ${loginArgs} ${xgbArgs} \
    ${autoRecoveryArgs} ${disownArgs} \
    -notify ${notifyFile} ${proxyArgs} \
    > ${driverLogFile} 2>&1 &
for i in $(seq 30); do
  if [ -f "${notifyFile}" ]; then
    echo "H2O started on $(cat ${notifyFile})"
    break
  fi
  echo "Waiting for H2O to come up ($i)..."
  sleep 20
done
if [ ! -f "${notifyFile}" ]; then
  echo 'H2O failed to start!'
  cat ${driverLogFile}
  exit 1
fi
