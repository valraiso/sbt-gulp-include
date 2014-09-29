package net.valraiso.sbt.include

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.{incremental, SbtWeb, PathMapping}
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}
import com.typesafe.sbt.web.incremental._
import sbt.Task
import spray.json.{JsArray, JsBoolean, JsString, JsObject}

object Import {

  val gulpInclude = TaskKey[Pipeline.Stage]("gulpInclude", "gulp-include plugin")

  object gulpIncludeKeys {
    val buildDir = SettingKey[File]("gulpInclude-build-dir", "Where gulp-include will build files.")
    val extensions = SettingKey[String]("gulpInclude-extensions", "all inclusions that does not match the extension(s) will be ignored")
  }

}

object SbtgulpInclude extends AutoPlugin {

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import autoImport._
  import gulpIncludeKeys._

  override def projectSettings = Seq(
    buildDir := (resourceManaged in gulpInclude).value / "build",
    extensions := "",
    excludeFilter in gulpInclude := HiddenFileFilter,
    includeFilter in gulpInclude := GlobFilter("*.html"),
    resourceManaged in gulpInclude := webTarget.value / gulpInclude.key.label,
    gulpInclude := runGulpInclude.dependsOn(webModules in Assets).dependsOn(WebKeys.nodeModules in Assets).value
  )

  private def runGulpInclude: Def.Initialize[Task[Pipeline.Stage]] = Def.task {
    mappings =>

      val include = (includeFilter in gulpInclude).value
      val exclude = (excludeFilter in gulpInclude).value
      val gulpIncludeMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      SbtWeb.syncMappings(
        streams.value.cacheDirectory,
        gulpIncludeMappings,
        buildDir.value
      )

      val buildMappings = gulpIncludeMappings.map(o => buildDir.value / o._2)

      val cacheDirectory = streams.value.cacheDirectory / gulpInclude.key.label
      val runUpdate = FileFunction.cached(cacheDirectory, FilesInfo.hash) {
        inputFiles =>
          streams.value.log.info("gulpInclude")

          val sourceFileMappings = JsArray(inputFiles.filter(_.isFile).map { f =>
            val relativePath = IO.relativize(buildDir.value, f).get
            JsArray(JsString(f.getPath), JsString(relativePath))
          }.toList).toString()

          val targetPath = buildDir.value.getPath
          val jsOptions = JsObject(
              "extensions" -> JsString(extensions.value)
          ).toString()

          val shellFile = SbtWeb.copyResourceTo(
            (resourceManaged in gulpInclude).value,
            getClass.getClassLoader.getResource("gulp-include-shell.js"),
            streams.value.cacheDirectory
          )

          SbtJsTask.executeJs(
            state.value,
            (engineType in gulpInclude).value,
            (command in gulpInclude).value,
            (nodeModuleDirectories in Plugin).value.map(_.getPath),            
            shellFile,
            Seq(sourceFileMappings, targetPath, jsOptions),
            (timeoutPerSource in gulpInclude).value * gulpIncludeMappings.size
          )

          buildDir.value.***.get.filter(!_.isDirectory).toSet
      }

      val gulpIncludedMappings = runUpdate(buildMappings.toSet).pair(relativeTo(buildDir.value))
      (mappings.toSet -- gulpIncludeMappings ++ gulpIncludedMappings).toSeq
  }

}