package scribt

import scala.tools.nsc.{GenericRunnerCommand, Interpreter, InterpreterCommand, Settings}
import scala.tools.nsc.reporters.Reporter

import sbt._

/* Run code just like sbt console-project */
object ProjectConsoleInterpreter {
  def runCode(project: Project, code: String) =
    withInterpreter(project) { interpreter => interpreter.interpret(code) }

  def withInterpreter(project: Project)(f: Interpreter => Unit) = {
    val originalClassLoader = Thread.currentThread.getContextClassLoader

    val projectClassLoader = project.getClass.getClassLoader
    val classpath = getProjectClasspath(project)
    val fullClasspath = classpath.get ++ Path.fromFiles(project.info.app.scalaProvider.jars)

    val interpreterCommand = new InterpreterCommand(Nil, m => project.log.error(m))
    val compilerCommand = new GenericRunnerCommand(Nil, m => project.log.error(m))

    if (interpreterCommand.ok && compilerCommand.ok) {
      val interpreterSettings = interpreterCommand.settings
      val compilerSettings = compilerCommand.settings
      compilerSettings.classpath.value = Path.makeString(fullClasspath)

      val interpreter = new Interpreter(interpreterSettings) {
        override protected def parentClassLoader = projectClassLoader
        override protected def newCompiler(settings: Settings, reporter: Reporter) = super.newCompiler(compilerSettings, reporter)
      }

      interpreter.setContextClassLoader()
      interpreter.bind("current", project.getClass.getName, project)
      interpreter.interpret("import sbt._")
      interpreter.interpret("import Process._")
      interpreter.interpret("import current._")

      f(interpreter)

      interpreter.close
      Thread.currentThread.setContextClassLoader(originalClassLoader)
    }
  }

  def getProjectClasspath(project: Project): PathFinder =
    getProjectBuilder(project.info, project.log) match {
      case Some(builder) => builder.projectClasspath
      case _ if project.getClass == Project.DefaultBuilderClass => project.info.sbtClasspath
      case _ =>
	project.info.parent match {
	  case Some(p) => getProjectClasspath(p)
	  case None => project.info.sbtClasspath
	}
    }

  def getProjectBuilder(info: ProjectInfo, buildLog: Logger): Option[BuilderProject] =
    if (info.builderProjectPath.asFile.isDirectory) {
      val builderInfo = ProjectInfo(info.builderProjectPath.asFile, Nil, None)(buildLog, info.app, Some(info.definitionScalaVersion))
      val builderProject = new BuilderProject(builderInfo, info.pluginsPath, buildLog)
      Some(builderProject)
    } else {
      None
    }
}

