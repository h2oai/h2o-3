package water.build.plugins

/**
 * Utilities for Scala cross compilation.
 */
class ScalaUtils {

    static getScalaVersionSuffix(String version) {
        return "_${getScalaBaseVersion(version)}"
    }

    /* Scala base version for Scala < 2.10 is the same as version number (minor releases are not
       binary compatible). For Scala >= 2.10 the major version represents compatibility group.
     */
    static getScalaBaseVersion(String version) {
        return version.startsWith("2.10.") ? "2.10"
                                           : version.startsWith("2.11") ? "2.11"
                                                                        : version
    }
}
