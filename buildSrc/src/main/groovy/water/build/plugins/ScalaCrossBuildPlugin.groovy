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

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import static ScalaUtils.getScalaVersionSuffix
import static water.build.plugins.ScalaUtils.getScalaBaseVersion

/**
 * This plugin provides Scala cross-build capability, it creates multiple projects with different scala version suffixes
 * that share the same module directory.
 *
 * Note: based on photon-ml code
 */
class ScalaCrossBuildPlugin implements Plugin<Settings> {

  private static def includeSuffixedProject(settings, module, scalaVersion, mapping) {
    def path = module + getScalaVersionSuffix(scalaVersion)
    settings.include(path)

    def project = settings.findProject(path.startsWith(":") ? path : ":" + path)
    project.projectDir = new File(project.projectDir.parent, module.split(':').last())
    mapping.put(project.name, scalaVersion)
  }

  void apply(Settings settings) {

    def scalaCrossCompile = settings.extensions.create('scalaCrossCompile', ScalaCrossCompileExtension)//, settings.startParameter.projectProperties)
    def projectMapping = new HashMap<String, String>() // project name to version mapping

    scalaCrossCompile.projectsToCrossBuild.all { module ->
      if (scalaCrossCompile.buildDefaultOnly) {
        includeSuffixedProject(settings, module, scalaCrossCompile.defaultScalaVersion, projectMapping)
      } else {
        scalaCrossCompile.targetVersions.each { v ->
          includeSuffixedProject(settings, module, v, projectMapping)
        }
      }
    }
    // Modify the projects to produce right build directory and
    // propagate scala version
    // FIXME: introduce extension to simplify passing of scala version
    settings.gradle.projectsLoaded { g ->
      g.rootProject.subprojects { subproject ->
        // It is cross compiled project
        if (projectMapping.containsKey(subproject.name))  {
          def projectScalaVersion = projectMapping[subproject.name]
          subproject.ext.scalaBaseVersion = getScalaBaseVersion(projectScalaVersion)
          subproject.ext.scalaVersion = projectScalaVersion
          subproject.buildDir = "${subproject.buildDir}/${subproject.name}"
        }
      }
    }
  }
}