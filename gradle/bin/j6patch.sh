#!/usr/bin/env bash

if [ ! -f "$1" ]; then
cat <<EOF 
  File name is required!"
  Syntax is
    $0 <class file>

EOF
exit 1
fi

FILENAME=$1
[ $DEBUG ] && echo "Converting $FILENAME"
printf "\x00\x00\x00\x32" | dd of=$FILENAME seek=4 bs=1 count=4 conv=notrunc 2> /dev/null
KLAZZNAME="$(echo $1 | sed -e "s/.class$//")"
[ $DEBUG ] && echo "Verifying class $KLAZZNAME"
# Disable Javapp since we are using AnimalSniffer
#$JAVA_6_HOME/bin/javap $KLAZZNAME > /dev/null  || ( echo "Verification failed: $KLAZZNAME"; exit -1 )

