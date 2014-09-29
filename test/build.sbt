import JsEngineKeys._

lazy val root = (project in file(".")).enablePlugins(SbtWeb)

JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

pipelineStages in Assets := Seq(gulpInclude)