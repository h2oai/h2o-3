package water.gradle.plugins
import org.gradle.api.*
import org.gradle.api.plugins.*
import groovy.xml.MarkupBuilder
import java.util.regex.Matcher
import java.util.regex.Pattern

apply plugin: 'manageLocalClouds'

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        compile gradleApi()
        compile localGroovy()
        classpath 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.1'
    }
}
//
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT

class manageLocalClouds implements Plugin<Project> {
    def eol = System.getProperty('line.separator')
    def jarName = 'h2o.jar'
    def jpsPidPattern = ~/\s*(\d+)\s+h2o.jar\s*/

    def createClouds = {project, config ->
        def jar = [project.parent.buildDir.canonicalPath,jarName].join(System.getProperty('file.separator'))
        def mem = config.cloud.mem.toString()
        def name = config.cloud.name
        def port = config.cloud.port.toString()
        def cmd = "-Xmx${mem} -ea -jar ${jar} -name ${name} -baseport ${port}"
        project.logger.warn cmd
        (1..config.cloud.size).each{
            project.ant.exec(
                executable: 'java',
                dir: project.buildDir.canonicalPath,
                spawn: true)
                    {arg(line: cmd)}
        }
    }

    def waitOnClouds = {project, config ->
        project.ant.exec(executable: 'jps', dir: project.buildDir.canonicalPath, outputproperty: 'cmdOut')
        def pids = []
        project.ant.properties.cmdOut.split('\n').each{
            Matcher m = (it =~ jpsPidPattern)
            if ( m.matches() ){
                pids << m[0][1]
            }
        }
        project.ext.set('cloudsPid', pids.join(','))
        def http = new HTTPBuilder('http://localhost:54321')
        def cloudIsUp = false
        def foundError = false
        while ( !cloudIsUp && !foundError){
            http.request(GET, TEXT) {req->
                uri.path = "/"
                response.success = { resp, reader->
                    cloudIsUp = true
                    assert resp.status == 200
                }
                response.'404' = {resp ->
                    foundError = true
                    project.logger.error " ---------------- Unable to start cloud. ---------------------"
                }
            }
        }
        project.logger.info " *** Cloud at http://localhost:54321 is up and running ***"
        project.logger.warn "PID: " + project.ext.cloudsPid.toString()

    }

    void apply(Project project) {
        project.task('manageLocalClouds') << {
            def config = new ConfigSlurper().parse(
                    (new File(project.projectDir.canonicalPath + "/src/test/R/clouds/localCloud.groovy")).toURI().toURL())
            createClouds(project, config)
            waitOnClouds(project, config)
        }

    }
}