#!/bin/bash -ex

# This script adds the kerberos only configs to relevant hadoop config files. For example,
# if there are core-site.xml and core-site.xml.part files in ${HADOOP_CONF_DIR}, the content of
# core-site.xml.part is appended to core-site.xml. The process is following:
#   1. remove the closing configuration tag from core-site.xml
#   2. append content of core-site.xml.part to core-site.xml
#   3. append the closing configuration tag to core-site.xml

cd ${HADOOP_CONF_DIR}
for conf in $(ls *.xml); do
    if [ -f "${conf}.part" ]; then
        echo "Putting ${conf}.part to ${conf}"
        echo "Remove the </configuration>"
        sed -i 's/<\/configuration>//g' ${conf}
        echo "Append content of ${conf}.part"
        cat ${conf}.part >> ${conf}
        echo "Append </configuration>"
        echo -e "\n</configuration>" >> ${conf}
        cat ${conf}
        rm ${conf}.part
    else
        echo "File ${conf}.part does not exist"
    fi
done
sed -i "s|SUBST_HADOOP_CONF_DIR|${HADOOP_CONF_DIR}|g" ${HADOOP_CONF_DIR}/*.xml
