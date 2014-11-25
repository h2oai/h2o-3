package water.gradle.plugins
import org.gradle.api.*
import org.gradle.api.plugins.*
import groovy.xml.MarkupBuilder
import java.util.regex.Matcher

apply plugin: 'convertR2html'

dependencies {
    compile gradleApi()
    compile localGroovy()
}

class convertR2html implements Plugin<Project> {
    def eol = System.getProperty('line.separator')

    def styleDefinitions = {project, details->
        def writer = new StringWriter()
        new File(project.projectDir.canonicalPath + "/src/test/resources/testHtmlTemplates/style.txt").eachLine{
            writer << it << eol
        }
        writer.toString()
    }

    def scriptDefinitions = {project, details->
        def writer = new StringWriter()
        new File(project.projectDir.canonicalPath + "/src/test/resources/testHtmlTemplates/script.txt").eachLine{
            writer << it << eol
        }
        writer.toString()
    }

    def testsSummary = {
        def q = { "'" + it + "'"}
        def writer = new StringWriter()
        writer << "Show" << eol
        [0:"Summary", 1:"Failed", 2:"All"].each{k,v->
            def js = "javascript:showCase(${k.toString()})"
            writer << "<a href=${q(js)}>${v}</a>" << eol
        }
        writer.toString()
    }

    /**
     * creates the html file with details for each test context
     */
    def html4test = {project, fileName, details ->
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)
        def firstLines = [
        """<?xml version="1.0" encoding="UTF-8"?>""",
        """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">"""]
        xml.'html'('xmlns':'http://www.w3.org/1999/xhtml'){
            head{
                mkp.yieldUnescaped( styleDefinitions(project, details) )
            }
            body{
                mkp.yieldUnescaped(scriptDefinitions(project, details))

                div(class: 'heading'){
                    h1("Unit test report")
                    //TODO: add strong as in <p class='attribute'><strong>Start Time:</strong> 2014-11-14 12:19:45</p>
                    p(class: 'attribute', "Start Time: " + "2014-11-14 12:19:45" )
                    p(class: 'attribute', "Duration: " + "0:00:00.105188")
                    p(class: 'attribute', "Status: " + "Pass 3")
                    p(class: 'description')
                }

                p( id:'show_detail_line'){
                    a(href:'javascript:showCase(0)', "Summary")
                    a(href:'javascript:showCase(1)', "Failed")
                    a(href:'javascript:showCase(2)', "All")
                }
                table(id:'result_table'){
                    colgroup{
                        col(align:'left')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                    }
                    tr(id:'header_row'){
                        td("Test Group/Test Case")
                        td("Time")
                        td("Count")
                        td("Pass")
                        td("Fail")
                        td("Error")
                        td("View")
                    }
                    tr(class:'passClass'){
                        td("proboscis.case:MethodTest")
                        td("0.00")
                        td("3")
                        td("3")
                        td("0")
                        td("0")
                        td{
                            a(href:"javascript:showClassDetail('c1',3)", "Detail")
                        }
                    }
                    tr(id:'pt1.1', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testD')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id:'pt1.2', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testE')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id:'pt1.3', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testF')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id: 'total_row'){
                        td("Total")
                        td("0.00")
                        td("3")
                        td("3")
                        td("0")
                        td("0")
                        td( mkp.yieldUnescaped("&nbsp;") )
                    }
                }
                div(id: 'ending')
            }
        }
        def out = new File(fileName)
        out.write('')
        firstLines.each{ out << it << eol}
        out << writer.toString() << eol
    }

    def stopCloudsOn = {project->
        if ( null != project.ext.cloudsPid ){
            project.logger.warn "Stopping the following processes: " + project.ext.cloudsPid
            project.ext.cloudsPid.split(",").each{
                project.logger.info "kill -9 ${it}".execute().text
            }
        }
    }

    void apply(Project project){
        project.task('reportAcceptanceTests', dependsOn: 'prepareAcceptanceHtmlReports') << {
            new File(project.projectDir.canonicalPath + "/build/reports/site").mkdirs()

            stopCloudsOn(project)

            def folders = project.ext.R_test_folders as List<String>
            def testMarker = project.ext.Regex_test_marker as java.util.regex.Pattern
            logger.info " ** Looking into ${folders.toString()}"
            def pickResults = {from->
                def answer = []
                def getTests = {
                    def what = new File(it).list([accept:{d, f-> f ==~ testMarker }] as FilenameFilter)
                    (what == null)?[]:what.toList()
                }
                from.each{
                    getTests(it).each{item->
                        answer.add(new Tuple<String,String>(it, item))
                    }
                }
                answer
            }

            def testResults = [:]

            def getTestDetails = {
                //'it' is something like ........ or ......12.....
                [tests:5, failures:2, errors:1, skipped:2]
            }

/* first list looks like
 Basic tests : .......
 Functional tests : ....12.
 Integration tests : .......
 Smoke tests : .......
 this will translate into
 [
 'Basic tests':[tests:7, failures:0, errors:0, skipped:0],
 'Functional tests':[tests:7, failures:2, errors:0, skipped:0], etc.
 ]
*/
            def processTest = {line, into->
                def summaryPattern = ~/(\w[^:]+)\s*:\s*(.*)/
                def exclusions = ["Warning messages"] as Set
                Matcher m = (line =~ summaryPattern)
                if ( m.matches() ){
                    if ( !exclusions.contains(m[0][1]) ){
                        into.put( m[0][1].trim(), getTestDetails(m[0][2]) )
                    }
                }
            }

            def processTestResults = {file, into ->
                logger.warn "Processing test results from " + file.getCanonicalPath()
                def lines = file as String[]
                def detailPattern = ~/(.)\.\s*(.+)\(@.+\.r#\d*\):\s*(.+)/
                def exclusions = ["Warning messages"] as Set
                def tempBuffer = []
                def buffer = []
                lines.eachWithIndex{line, i->
                    if ( 0 == line.trim().length() ){
                        if ( tempBuffer.size() > 0 ){
                            buffer << tempBuffer.clone()
                            tempBuffer.clear()
                        }
                    }
                    else{
                        tempBuffer << line.trim()
                    }
//                    Matcher m1 = (line =~ summaryPattern)
//                    Matcher m2 = (line =~ detailPattern)
//                    if ( m1.matches() ){
//                        if ( !exclusions.contains(m1[0][1]) ){
//                            //println " * ${m1[0][1]} : ${m1[0][2]}"
//                        }
//                    }
//                    if ( m2.matches() )println "#" + line
                }
                if ( tempBuffer.size() > 0 ) buffer << tempBuffer
                buffer.eachWithIndex{list, index->
                    switch ( index ){
                        case 0:
                            /* first list looks like
                            Basic tests : .......
                            Functional tests : ....12.
                            Integration tests : .......
                            Smoke tests : .......
                            this will translate into
                            [
                            'Basic tests':[tests:7, failures:0, errors:0, skipped:0],
                            'Functional tests':[tests:7, failures:2, errors:0, skipped:0], etc.
                            ]
                             */
                            list.each{processTest(it, into)}
                            break;
                        default:
                            /*
                            1. Failure(@test-functional.r#14): equality holds ------------------------------
                            5 not equal to 6
                            Mean relative difference: 0.1666667
                            --
                            2. Failure(@test-functional.r#15): equality holds ------------------------------
                            10 is not identical to 11. Differences:
                            Mean relative difference: 0.09090909
                             */
                            break;
                    }
                }
            }

            pickResults(folders).each{tuple->
                //TODO: what's happening with testResults if we digest multiple files?
                processTestResults(
                    new File([tuple.get(0), tuple.get(1)].join(System.getProperty('file.separator'))),
                    testResults
                )
            }

            def links = ""
            def details = ""
            def q = { "\"" + it + "\""}
            def space2_ = { it.replace(" ", "_") }

            testResults.each{k,v->
                def testDetails = [:]
                def reportLocation =
                    new File(project.projectDir.canonicalPath + "/build/${space2_(k)}.html").getCanonicalPath()
                html4test(project, reportLocation, testDetails)
                v.put('resultsHtml', reportLocation)
            }

            testResults.each{k,v->
                def href = q(v['resultsHtml'])
                def title= q(k)
                links += "<a href=${href} title=${title}>$k</a>\n"
                details +=
                        "<pre>" +
                        "<b>${k}<b/><br />Tests: ${v['tests']}<br />" +
                        "Failures: ${v['failures']}<br />" +
                        "Errors: ${v['errors']}<br />" +
                        "Skipped: ${v['skipped']}" +
                        "</pre>\n"
            }
            //ant copy seems to choke on token substitution if binary files are included. need to go in two passes
            ant.copy(toDir: "build/reports/site", filtering: true, force: true, overwrite: true){
                fileset(dir: "src/test/resources/site"){
                    include(name: "**/*.html")
                }
                filterset(){
                    filter(token: "test_links", value: links)
                    filter(token: "test_results_details", value: details)
                }
            }   //copy with filtering has some issues copying some of the files, so two passes here.
            ant.copy(toDir: "build/reports/site", filtering: false, force: true, overwrite: false){
                fileset(dir: "src/test/resources/site"){
                    exclude(name: "**/*.html")
                }
            }
        }
    }
}

