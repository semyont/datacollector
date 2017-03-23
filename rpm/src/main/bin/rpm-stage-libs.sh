#!/bin/bash
#
#
# Licensed under the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#

# for debugging, it will prints commands and their arguments as they are executed.
set -x

DIST=$1
TARGET=$2
VERSION=$3
RPM_ALL_DIST=streamsets-datacollector-${VERSION}-all-rpms

cd ${TARGET} || exit

if [ ! -d "${RPM_ALL_DIST}" ];
then
  mkdir ${RPM_ALL_DIST}
fi

# copy all stage-lib rpms to ${RPM_ALL_DIST}
for STAGE_DIR in ${DIST}/*
do
  STAGE_LIB=${STAGE_DIR}/target/rpm
  STAGE_NAME=$(basename ${STAGE_DIR})
  RPM="rpm"
  if [ -d "${STAGE_LIB}" ] && [ "${STAGE_NAME}" != "${RPM}" ];
  then
    echo "Processing stage library: ${STAGE_NAME}"
    cp -Rf ${STAGE_LIB}/*/RPMS/noarch/streamsets-datacollector-*.noarch.rpm ${RPM_ALL_DIST}
  fi
done

# copy core rpm to ${RPM_ALL_DIST}
cp -Rf ${TARGET}/streamsets-datacollector-streamsets-datacollector/RPMS/noarch/streamsets-datacollector-*.noarch.rpm ${RPM_ALL_DIST}

# additional step to compress all stage-libs into tar file
tar -czf ${RPM_ALL_DIST}/streamsets-datacollector-${VERSION}-all-rpms.tgz ${RPM_ALL_DIST}/streamsets-datacollector-*.noarch.rpm
