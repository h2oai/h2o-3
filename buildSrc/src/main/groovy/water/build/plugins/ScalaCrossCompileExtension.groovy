/*
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package water.build.plugins

import org.gradle.api.internal.DefaultDomainObjectSet

/**
 * Gradle build extension for Scala cross compilation definition.
 *
 * Note: Motivated by a build approach used by photon-ml.
 */
public class ScalaCrossCompileExtension {

  private String defaultScalaVersion

  private boolean buildDefaultOnly = false

  def targetVersions = new DefaultDomainObjectSet(String)

  def projectsToCrossBuild = new DefaultDomainObjectSet(String)

  void targetVersions(String... versions) {
    targetVersions.addAll(versions as List)
  }


  void defaultScalaVersion(String defaultScalaVersion) {
    defaultScalaVersion = defaultScalaVersion
  }

  void buildDefaultOnly(boolean buildDefaultOnly) {
    buildDefaultOnly = buildDefaultOnly
  }

  String getDefaultScalaVersion() {
    return defaultScalaVersion
  }

  boolean getBuildDefaultOnly() {
    return buildDefaultOnly
  }

  void include(String projectName) {
    projectsToCrossBuild.add(projectName)
  }
}