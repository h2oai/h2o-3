#! /bin/bash -x

clouded=false
while [ "$clouded" != true ]
do
	sleep 1
	cloud_size=$( curl 'http://192.168.224.2:80/3/Cloud' | jq '.cloud_size' )
  if [ "$cloud_size" == 2 ]
  then
	  echo "H2O Cluster size is ${cloud_size}"
    clouded=true
  fi
done

if [ "$clouded" = true ]
then
	exit 0
else
	exit 1
fi
