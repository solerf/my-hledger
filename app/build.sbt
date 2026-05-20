ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "app"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Scalafix needs semanticdb; sbt-tpolecat enables it automatically in dev mode,
// but make it explicit so `sbt scalafix` works from a clean state too.
ThisBuild / semanticdbEnabled := true
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
// Required for scalafix's OrganizeImports `removeUnused = true` to detect
// unused imports on Scala 3 — without this flag the compiler does not mark
// them in semanticdb and scalafix leaves them in place.
ThisBuild / scalacOptions += "-Wunused:imports"

val Http4sVersion = "0.23.34"
val CirceVersion  = "0.14.15"

// sbt-tpolecat and other plugins sometimes inject Scala 2-only flags
// (e.g. `-Xfatal-warnings`, `-Ywarn-*`, `-Xlint:*`) that Scala 3 either
// renamed or doesn't recognise. Strip them so the build stays clean.
val dropScala2Options: Seq[String] => Seq[String] =
  _.filterNot { opt =>
    opt == "-Xfatal-warnings" ||
    opt.startsWith("-Ywarn") ||
    opt.startsWith("-Xlint") ||
    opt.startsWith("-Ypartial-unification") ||
    opt == "-Yrangepos"
  }

// Shared cross-project: DTOs with circe codecs for JVM + Scala.js.
lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(
    name := "app-shared",
    scalacOptions ~= dropScala2Options,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"    % CirceVersion,
      "io.circe" %%% "circe-generic" % CirceVersion,
      "io.circe" %%% "circe-parser"  % CirceVersion
    )
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js

// Backend: http4s ember server + client. Talks to hledger-web for data;
// serves the Scala.js frontend assets at /.
lazy val backend = (project in file("backend"))
  .dependsOn(sharedJVM)
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "app-backend",
    scalacOptions ~= dropScala2Options,
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"    %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % Http4sVersion,
      "org.http4s"    %% "http4s-circe"        % Http4sVersion,
      "io.circe"      %% "circe-core"          % CirceVersion,
      "io.circe"      %% "circe-generic"       % CirceVersion,
      "io.circe"      %% "circe-parser"        % CirceVersion,
      "org.typelevel" %% "cats-effect"         % "3.7.0",
      "org.typelevel" %% "log4cats-slf4j"      % "2.8.0",
      "org.slf4j"      % "slf4j-simple"        % "2.0.18",
    ),
    // sbt-revolver forks the backend with cwd = backend/baseDirectory, so the
    // default relative `frontend/src/main/resources` doesn't resolve. Pin it
    // to the absolute path of the frontend resource directory in dev.
    reStart / envVars := Map(
      "APP_ASSETS_DIR" ->
        ((LocalRootProject / baseDirectory).value / "frontend" / "src" / "main" / "resources").getAbsolutePath
    ),
    assembly / mainClass       := Some("app.backend.Main"),
    assembly / assemblyJarName := "app.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("META-INF", "MANIFEST.MF")                   => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.toLowerCase.endsWith(".sf")) =>
        MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.toLowerCase.endsWith(".dsa")) =>
        MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.toLowerCase.endsWith(".rsa")) =>
        MergeStrategy.discard
      case "module-info.class"                                   => MergeStrategy.discard
      case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
      case x =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )

// Frontend: Scala.js + Laminar. Mounts onto <div id="app">.
lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    name := "app-frontend",
    scalacOptions ~= dropScala2Options,
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "17.2.1"
    ),
    // Write the linker output (main.js, main.js.map, …) into a dedicated
    // subdirectory of the served resources. The linker wipes its output
    // directory on every link, so it must own this folder exclusively —
    // sharing it with `vendor/`, `app.css`, or `index.html` would delete
    // them. `index.html` references `js/main.js`.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      (Compile / resourceDirectory).value / "js",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      (Compile / resourceDirectory).value / "js"
  )

lazy val root = (project in file("."))
  .aggregate(sharedJVM, sharedJS, backend, frontend)
  .settings(
    name := "app",
    scalacOptions ~= dropScala2Options,
    publish / skip := true
  )

// --- Dev loop -----------------------------------------------------------------
// `dev` boots the backend with sbt-revolver (forked JVM that can be restarted
// without leaving sbt) and then enters watch mode. On any source change in
// backend OR frontend, sbt re-links the Scala.js bundle (writing main.js
// directly into the served resources directory) and reStarts the backend.
// Browser refresh picks up frontend changes immediately; backend changes
// require nothing more than the refresh once the new JVM is up.
//
// Implemented as a Command (not an alias) because `addCommandAlias` flattens
// top-level semicolons, which strips `~` of its argument and breaks the
// watch trigger. A Command injects each step into sbt's state intact.
commands ++= Seq(
  Command.command("dev") { state =>
    "backend/reStart" ::
      "~ ;frontend/fastLinkJS;backend/reStart" ::
      state
  },
  Command.command("devStop") { state =>
    "backend/reStop" :: state
  }
)
