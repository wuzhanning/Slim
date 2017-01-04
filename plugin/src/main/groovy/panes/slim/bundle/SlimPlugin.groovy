package panes.slim.bundle

import org.gradle.api.Plugin
import org.gradle.api.Project

public class SlimPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create("Slim", SlimExtension)
        project.logger.error("----------------------Slim  ------------------------------------")
        project.afterEvaluate {
            // prebuild
            PrebuildTask prebuildTask = project.task('prebuild', type:PrebuildTask, group:"Slim", description: "check environment")
            // shrink
            ShrinkTask shrinkTask = project.task('shrink', type: ShrinkTask, group: "Slim", description: "decrease file size of bundle.apk")
            shrinkTask.dependsOn prebuildTask
            // cut
            FillTask fillTask = project.task('fill', type:FillTask, group: "Slim", description: "delete and copy res")

            // genSlimBundle
            def assembleDebug = project.tasks.findByName('assembleDebug')
            GenBundleTask genBundleTask = project.task('genSlimBundle', type: GenBundleTask, group: "Slim", description: "generate bundle.apk")
            genBundleTask.dependsOn shrinkTask
//            genBundleTask.dependsOn assembleDebug
//            assembleDebug.mustRunAfter shrinkTask
        }
    }
}