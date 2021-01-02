package org.github.vvlevchenko.gradle.make

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.process.ExecSpec
import java.io.*

open class MakePlugin:Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("make", MakeExtension::class.java, project)
    }
}

open class TaskDescription(val extension: MakeExtension, val phony:Boolean = false, val target:String, val prerequisites: List<String>)
open class TaskState(val description: TaskDescription, val configure:(TaskState.()->Unit)? = null) {
    val suffixes = mutableMapOf((".c" to ".o") to SuffixPattern("clang", "-c", "-o"))
    fun ref(name: String): String? = description.extension.environment.variables[name].also {
        description.extension.project.logger.info("ref($name: [$it])")
    }
    fun iref(name: String): Array<String> = when(name) {
        "target" -> arrayOf(description.target)
        "sources" -> arrayOf(*description.prerequisites.toTypedArray())
        else -> emptyArray()
    }.also {
        description.extension.project.logger.info("iref($name: [${it.joinToString{it}}])")
    }
}
class MakeEnvironment() {
    val variables = mutableMapOf<String, String>()

    operator fun String.invoke(value: String) {
        variables[this] = value
    }
}
open class MakeExtension(val project: Project) {
    val environment = MakeEnvironment()
    val name2tasks = mutableMapOf<String, Task>()
    var suffixConfiguration: (TaskState.()->Unit)? = null
    operator fun String.invoke(vararg args : String = emptyArray(), configure:(TaskState.()->Unit)? = null) = doStringInvoke(this, false, *args, configure = configure)
    operator fun String.invoke(phony: Boolean, vararg args : String = emptyArray(), configure:(TaskState.()->Unit)? = null) = doStringInvoke(this, phony, *args, configure = configure)

    private fun doStringInvoke(op:String, phony: Boolean, vararg args:String, configure:(TaskState.()->Unit)? = null):TaskState {
        project.logger.info("$op: ${args}")
        val state = TaskState(TaskDescription(this, phony, op, listOf(*args)), configure)
        explicitRule(state)
        return state

    }

    private fun explicitRule(state: TaskState) {
        project.logger.info("explicit-rule: ${state.description.target} <- ${state.description.prerequisites.joinToString{it}}")
        state.description.prerequisites.forEach { p ->
            if (name2tasks[p] != null)
                return@forEach
            project.logger.info("lookup suffix rule for ${p}")
            //TODO: filter
            val suffixTasks = state.suffixes.keys.filter { p.suffix() != null && p.suffix()!! == it.second }
            for(s in suffixTasks) {
                project.logger.info("suffix rule $s for $p")
                val name = p.removeSuffix(p.suffix()!!)
                project.logger.info("suffix rule $p name: $name")
                val taskName = "$name${s.second}"
                val suffixTask = TaskState(TaskDescription(this, false, taskName, listOf("$name${s.first}")))
                project.tasks.create(taskName) {
                    project.logger.info("$taskName: suffixConfiguration: ${suffixTask.description.extension.suffixConfiguration}")
                    suffixTask.description.extension.suffixConfiguration?.invoke(suffixTask)
                    dependsOn("$name${s.first}")
                    doLast{
                        project.exec {
                            project.logger.info("$taskName: suffixConfiguration: executable: ${state.suffixes[s]!!.tool}, flags: ${state.suffixes[s]!!.flags.joinToString{it}}")
                            executable(suffixTask.suffixes[s]!!.tool)
                            args(*suffixTask.suffixes[s]!!.flags)
                        }
                    }
                }
            }
        }
        name2tasks[state.description.target] = project.tasks.create(state.description.target) {
            state.description.prerequisites.forEach {
                dependsOn(it)
            }
            doLast {
                state.configure?.invoke(state)
            }
        }
    }

    //private fun suffixRule(target: String, vararg sources: String, configure:(TaskState.()->Unit)? = null): TaskState? {
    //    val src = sources.singleOrNull() ?: return null
    //    val suffixRuleName = "${src.suffix()}" to "${src.suffix()}"
    //    suffixes[suffixRuleName] ?: return null
    //    project.logger.info("suffix-rule: $target: $src ($suffixRuleName)")
    //    name2tasks[target] = project.tasks.create(target) {
    //        dependsOn(src)
    //        doLast {
    //            project.exec {
    //                executable(suffixes[suffixRuleName]!!.tool)
    //                args(*suffixes[suffixRuleName]!!.flags, target, src)
    //            }
    //        }
    //    }
    //    return TaskState(TaskDescription(this, false, target, listOf(src)))
    //}
}

class SuffixPattern(val tool:String, vararg val flags:String)

fun String.suffix():String? {
    val index = this.indexOfLast { it == '.' }
    if (index == -1)
        return null
    return this.substring(index)
}

fun MakeExtension.suffixes(configuration: TaskState.() -> Unit) {
    suffixConfiguration = configuration
}

fun MakeExtension.environment(configuration: MakeEnvironment.() -> Unit) {
    environment.configuration()
}

operator fun Pair<String, String>.invoke(state: TaskState, vararg flags:String?) {
    state.suffixes[this] = SuffixPattern(flags[0]!!, *flags.drop(1).filterNotNull().toTypedArray())
}


fun TaskState.shell(vararg p:String, configure: (ExecSpec.()->Unit)? = null){
    description.extension.project.tasks.create("shell-${description.target}") {
        this@shell.description.prerequisites.forEach { 
            dependsOn(it)
        }
        ByteArrayOutputStream().let { stdout ->
            project.exec {
                executable(p[0])
                args(p.drop(1))
                standardOutput = stdout
            }
            if (!this@shell.description.phony) {
                FileWriter(this@shell.description.extension.project.file(this@shell.description.target)).use {
                    it.write(stdout.toString())
                }
            }
        }
    }
}

fun TaskState.ld(vararg args:String) {
    description.extension.project.exec {
        executable("ld")
        args(*args)
    }
}

val TaskState.prerequisites
    get() = description.prerequisites.toTypedArray()

val TaskState.target
    get() = this.description.target
