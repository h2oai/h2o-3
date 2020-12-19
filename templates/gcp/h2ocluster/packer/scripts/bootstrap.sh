#!/usr/bin/env bash
set -o xtrace

# install dependencies
unzip -v || { echo "Installing Unzip"; sudo yum install -y unzip; }
java -version || { echo "Installing Java JDK"; sudo yum install -y java-1.8.0-openjdk-devel; }

H2O_URL="${1}"
echo "${H2O_URL}" > /tmp/h2o_url

H2O_ZIP_FILE=$(basename ${H2O_URL})
echo "${H2O_ZIP_FILE}" > /tmp/h2o_filename
H2O_VERSION=${H2O_ZIP_FILE%.zip}
echo "${H2O_VERSION}" > /tmp/h2o_version
H2O_HOME_DIR="/opt/h2oai/${H2O_VERSION}"
echo "${H2O_HOME_DIR}" > /tmp/h2o_home_dir

# Start installing H2O
sudo mkdir -p /opt/h2oai
# if flag file h2o_installed is not found install H2O
if [[ ! -f /opt/h2oai/h2o_installed ]]; then
    curl "${H2O_URL}" -o "/tmp/${H2O_ZIP_FILE}"
    sudo unzip -d /opt/h2oai "/tmp/${H2O_ZIP_FILE}"
    sudo touch /opt/h2oai/h2o_installed 
fi

# Install Simba BQ JDBC drivers
if [[ ! -f "/opt/h2oai/${H2O_VERSION}/jdbc/release-notes.txt" ]]; then
  sudo mkdir -p "/opt/h2oai/${H2O_VERSION}/jdbc"
  curl https://storage.googleapis.com/simba-bq-release/jdbc/SimbaJDBCDriverforGoogleBigQuery42_1.2.12.1015.zip -o /tmp/jdbc.zip
  sudo unzip -d "/opt/h2oai/${H2O_VERSION}/jdbc" /tmp/jdbc.zip
fi

sudo mv /tmp/h2ocluster-sa-key.json "/opt/h2oai/${H2O_VERSION}/h2ocluster-sa-key.json" 

