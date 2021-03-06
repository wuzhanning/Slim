package panes.slim.bundle.task

import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import groovy.xml.QName
import groovy.xml.XmlUtil
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.TaskAction
import panes.slim.bundle.utils.AndroidUtil

class ShrinkTask extends DefaultTask {
    @TaskAction
    void shrink() {
        String currentPath = project.getProjectDir()
        println "project.path = ${currentPath}"
        def android = AndroidUtil.android(project)
        def srcFiles = android.sourceSets.main.res.srcDirs
        def javaFiles = android.sourceSets.main.java.srcDirs
        def androidTestFiles = android.sourceSets.androidTest
        def testFiles = android.sourceSets.test
        println "src = ${srcFiles}; java = ${javaFiles}; test = ${androidTestFiles}; testFiles = ${testFiles}"
        srcFiles.each{
            it.deleteDir()
        }
        javaFiles.each{
            it.deleteDir()
        }
        androidTestFiles.java.srcDirs.each{File f->
            String path = f.parentFile.absolutePath
            if (path.endsWith('androidTest')){
                f.parentFile.deleteDir()
            }
        }
        testFiles.java.srcDirs.each{File f->
            String path = f.parentFile.absolutePath
            if (path.endsWith('test')){
                f.parentFile.deleteDir()
            }
        }

        String[] keepDirAll = ["${currentPath}\\build", "${currentPath}\\src"];
        def keepDirExt = project.extensions.Slim.keepDir.collect{
            println "kepp : ${it}"
            it = "${currentPath}\\${it}"
        }
        keepDirAll += keepDirExt
        def files = project.getProjectDir().listFiles()
        def dirToDelete = []
        files.each {
            if (it.isDirectory()){
                dirToDelete.add(it.getAbsolutePath())
            }
        }

        dirToDelete.removeAll(keepDirAll)
        dirToDelete.each {
            println "delete: ${it}"
            new File(it).deleteDir()
        }
        new File(AndroidUtil.manifestPath(project)).parentFile.listFiles().each {
            if (!it.name.equals("AndroidManifest.xml")){
                it.delete()
            }
        }
        DependencySet compilesDependencies = project.configurations.compile.dependencies
        DependencySet testDependencies = project.configurations.testCompile.dependencies
        def deleteCompile = compilesDependencies.findAll {
            it.name.contains('appcompat')
        }
//        compilesDependencies.removeAll(deleteCompile)
        println "delete compile: " <<deleteCompile
        def deleteTest = testDependencies.findAll {
            it.name.equals('junit')
        }
//        testDependencies.removeAll(deleteTest)
        println "delete compile: " << deleteTest
/*
        test: junit-4.12.jar
        test: hamcrest-core-1.3.jar
        compile: appcompat-v7-24.1.1.aar
        compile: animated-vector-drawable-24.1.1.aar
        compile: support-v4-24.1.1.aar
        compile: support-vector-drawable-24.1.1.aar
        compile: support-annotations-24.1.1.jar
*/

        String manifestPath = AndroidUtil.manifestPath(project)
        String packageValue
        def parser = new XmlParser().parse(manifestPath)

        packageValue = parser.attributes().find {
           'package'.equals(it.key)
        }.value
//        def xmlWriter = new StringWriter()
//        def xmlMarkup = new MarkupBuilder(xmlWriter)
//        xmlMarkup.'manifest'(
//                'xmlns:android':'http://schemas.android.com/apk/res/android',
//                'package':"${packageValue}"){
//            'application'()
//        }
//        def manifest =
//                new XmlSlurper()
//                        .parseText(xmlWriter.toString())
//                        .declareNamespace(android:'http://schemas.android.com/apk/res/android')

        parser.application.replaceNode{
            application(){}
        }
        def allNodes = parser.children().findAll {Node it->
            !it.name().equals('application')
        }
        allNodes.each {
            parser."${it.name()}".replaceNode{
            }
        }

        String serialize = XmlUtil.serialize(parser)
        new File(manifestPath).write(serialize)
    }

}