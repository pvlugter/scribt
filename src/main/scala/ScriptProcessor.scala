package scribt

import sbt._
import sbt.processor._

class ScriptProcessor extends Processor {
  val localScripts = Path.fromFile(".") / "project" / "scripts"
  val globalScripts = Path.userHome / ".sbt" / "scripts"

  val Execute = """--exec\s+(.*)""".r
  val Script = """(\S+)""".r

  val Property = """(.*)#\{(.+)\}(.*)""".r

  def apply(label: String, project: Project, onFailure: Option[String], args: String): ProcessorResult = {
    def succeed() = new processor.Success(project, onFailure)
    def insert(cmds: String*) = new processor.Success(project, onFailure, cmds: _*)

    def execute(command: String) = {
      val cmd = replaceProperties(command, project)
      project.log.info("> " + cmd)
      insert(cmd)
    }

    def scalaScript(name: String) = {
      val path = locateScript(name)
      project.log.info("Loading scala script from: " + path)
      val code = readScript(path, project.log)
      ProjectConsoleInterpreter.runCode(project, code)
      succeed
    }

    def script(name: String) = {
      val path = locateScript(name)
      project.log.info("Loading script from: " + path)
      val cmds = readLines(path, project.log)
      val execs = cmds map (label + " --exec " + _)
      insert(execs: _*)
    }

    args match {
      case Execute(command) => execute(command)
      case Script(name) if name.endsWith(".scala") => scalaScript(name)
      case Script(name) => script(name)
      case _ => fail("Usage: " + label + " <scriptname>")
    }
  }

  def replaceProperties(cmd: String, project: Project): String = {
    def replace(s: String): String = s match {
      case Property(before, property, after) => replace(before + getProperty(property, project) + after)
      case _ => s
    }
    replace(cmd)
  }

  /* Find properties in the same way as sbt get action */
  def getProperty(property: String, project: Project): String = {
    project.getPropertyNamed(property) match {
      case Some(p) =>
	p.resolve match {
	  case DefinedValue(value, isInherited, isDefault) => value.toString
          case u: UndefinedValue => fail("Property '" + property + "' is undefined.")
	  case ResolutionException(m, e) => fail(m)
	}
      case None =>
	val value = System.getProperty(property)
        if (value != null) value
        else fail("Property '" + property + "' is undefined.")
    }
  }

  def locateScript(name: String): Path = {
    val local = localScripts / name
    val global = globalScripts / name
    if (local.exists)
      local
    else if (global.exists)
      global
    else
      fail("Can't find script '%s' in %s or %s".format(name, localScripts, globalScripts))
  }

  def readLines(path: Path, log: Logger): List[String] =
    readScript(path, log).split(FileUtilities.Newline).toList

  def readScript(path: Path, log: Logger): String =
    FileUtilities.readString(path.asFile, log) match {
      case Right(text) => text
      case Left(error) => fail(error)
    }

  def fail(message: String) = throw new ProcessorException(message)
}
