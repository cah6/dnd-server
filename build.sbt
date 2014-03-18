name := "websocket-test"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.2",
  anorm,
  cache
)     

play.Project.playScalaSettings
