package water.gradle.plugins
import org.gradle.api.*
import org.gradle.api.plugins.*
import groovy.xml.MarkupBuilder
import java.util.regex.Matcher

apply plugin: 'manageClouds'

dependencies {
    compile gradleApi()
    compile localGroovy()
}

class manageClouds implements Plugin<Project> {
    def eol = System.getProperty('line.separator')

    void apply(Project project) {
        project.task('manageClouds') << {
            exec{
                workingDir 'build'
                commandLine 'java', '-jar', 'h2o.jar'
                ignoreExitValue true
            }
        }

    }
}