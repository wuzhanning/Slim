package panes.slim.bundle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class MyCustomTask extends DefaultTask {
    @TaskAction
    void act() {
        println "project.path = ${project.path}"
    }

}