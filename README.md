Scribt
======

_SBT script processor_


Install
-------

Clone this repository:

    git clone http://github.com/pvlugter/scribt.git
    cd scribt

Install the processor on your machine:

    sbt publish-local "*script is scribt scribt 0.1"


Scripts
-------

Scribt gives you sbt scripts like the sbt `<` action. That is, you can
put a list of sbt commands in a file and run them.

Scripts are expected to be in one of two places. Project-specific
scripts go in `project/scripts`. Global scripts go in
`~/.sbt/scripts`.

Run a script with `script <name>`. Project-specific scripts are
checked for first.

Scripts can also call other scripts just like other commands.


Properties
----------

Scribt allows property replacement in commands with
`#{property.name}`. Properties are looked up in a similar way to the
sbt `get` action.

For example, the following can be included in a script:

    sh git commit -am "release version #{project.version}"

To do a git commit where the message includes the project version.


Scala snippets
--------------

Scribt can also run Scala scripts, which run as if entered into the
`console-project` interpreter, including having the current project
bound to `current` and importing `sbt._` and `current._`. Scala
scripts are detected by `.scala` extension.

For example, the following Scala snippet can be put in a file called
`version-sans.scala`. It removes the extra part of the project version.

    projectVersion.get match {
      case Some(v: BasicVersion) => {
        val newVersion = v.withExtra(None)
        log.info("Changing version to " + newVersion)
        projectVersion() = newVersion
        saveEnvironment()
      }
      case _ => ()
    }

It can then be run with:

    script version-sans.scala


Example
-------

An example of scripting a release integrated with git can be found at
[Scribt Example][example].

[example]: http://github.com/pvlugter/scribt-example


Uninstall
---------

To uninstall the processor:

    cd path/to/scribt
    sbt "*remove script"
