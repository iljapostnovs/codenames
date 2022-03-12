name := "FinalCase"

version := "1.0"

scalaVersion := "2.13.6"

val circeVersion     = "0.13.0"
val http4sVersion    = "0.21.22"
val scalaTestVersion = "3.2.10"
val slf4jVersion     = "1.7.5"

libraryDependencies ++= Seq(
  "io.circe"      %% "circe-generic"        % circeVersion,
  "io.circe"      %% "circe-generic-extras" % circeVersion,
  "io.circe"      %% "circe-parser"         % circeVersion,
  "org.http4s"    %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"    %% "http4s-blaze-client"  % http4sVersion,
  "org.http4s"    %% "http4s-circe"         % http4sVersion,
  "org.http4s"    %% "http4s-dsl"           % http4sVersion,
  "org.slf4j"      % "slf4j-api"            % slf4jVersion,
  "org.slf4j"      % "slf4j-simple"         % slf4jVersion,
  "org.scalatest" %% "scalatest"            % scalaTestVersion % Test,
)
