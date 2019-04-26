import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import Dependencies._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerCommands, dockerUsername}
import ReleaseTransformations._
import ReleasePlugin.autoImport._
import ReleaseKeys._
import scala.sys.process._

lazy val kafkaLagExporter =
  Project(id = "kafka-lag-exporter", base = file("."))
    .enablePlugins(AutomateHeaderPlugin)
    .enablePlugins(JavaAppPackaging)
    .enablePlugins(DockerPlugin)
    .settings(commonSettings)
    .settings(
      name := "kafka-lag-exporter",
      description := "Kafka lag exporter finds and reports Kafka consumer group lag metrics",
      libraryDependencies ++= Vector(
        LightbendConfig,
        Kafka,
        AkkaTyped,
        AkkaSlf4j,
        Fabric8Model,
        Fabric8Client,
        Prometheus,
        PrometheusHotSpot,
        PrometheusHttpServer,
        ScalaJava8Compat,
        Logback,
        ScalaTest,
        AkkaTypedTestKit,
        MockitoScala,
        AlpakkaKafkaTestKit,
        AkkaHttp
      ),
      dockerUsername := Some("lightbend"),
      // Based on best practices found in OpenShift Creating images guidelines
      // https://docs.openshift.com/container-platform/3.10/creating_images/guidelines.html
      dockerCommands := Seq(
        Cmd("FROM",           "centos:7"),
        Cmd("RUN",            "yum -y install java-1.8.0-openjdk-headless && yum clean all -y"),
        Cmd("RUN",            "useradd -r -m -u 1001 -g 0 kafkalagexporter"),
        Cmd("ADD",            "opt /opt"),
        Cmd("RUN",            "chgrp -R 0 /opt && chmod -R g=u /opt"),
        Cmd("WORKDIR",        "/opt/docker"),
        Cmd("USER",           "1001"),
        ExecCmd("CMD",        "/opt/docker/bin/kafka-lag-exporter", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap"),
      ),
      updateHelmChartVersions := {
        import scala.sys.process._
        s"./scripts/update_chart.sh ${version.value}" !
      },
      //compile in Compile := (compile in Compile).dependsOn(updateHelmChartVersions).value,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      skip in publish := true,
      releaseProcess := Seq[ReleaseStep](
        lintHelmChart,                          // Lint the Helm Chart for errors
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        updateHelmChart,                        // Update the Helm Chart with the release version
        publishDockerImage,                     // Publish the Docker images used by the chart to dockerhub
        commitReleaseVersion,
        tagRelease,
        githubReleaseDraft,                     // Create a GitHub release draft
        setNextVersion,
        updateHelmChart,                        // Update the Helm Chart with the next snapshot version
        commitNextVersion,
        pushChanges
      )
    )

lazy val commonSettings = Seq(
  organization := "com.lightbend.kafkalagexporter",
  scalaVersion := Version.Scala,
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-target:jvm-1.8",
    "-Xlog-reflective-calls",
    "-Xlint",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-deprecation",
    "-feature",
    "-language:_",
    "-unchecked"
  ),
  scalacOptions in (Compile, console) := (scalacOptions in (Global)).value.filter(_ == "-Ywarn-unused-import"),
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  organizationName := "Lightbend Inc. <http://www.lightbend.com>",
  startYear := Some(2019),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(
    HeaderLicense.Custom(
      """|Copyright (C) 2016 - 2019 Lightbend Inc. <http://www.lightbend.com>
         |""".stripMargin
    )
  ),
)

lazy val updateHelmChartVersions = taskKey[Unit]("Update Helm Chart versions")

def exec(cmd: String, errorMessage: String): Unit = {
  val e = cmd.!
  if (e != 0) sys.error(errorMessage)
}

lazy val lintHelmChart = ReleaseStep(action = st => {
  exec("./scripts/lint_chart.sh", "Error while linting Helm Chart")
  st
})

lazy val updateHelmChart = ReleaseStep(action = st => {
  val (releaseVersion, _) = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
  exec(s"./scripts/update_chart.sh $releaseVersion", "Error while updating Helm Chart versions")
  st
})

lazy val packageChart = ReleaseStep(action = st => {
  exec("./scripts/package_chart.sh", "Error while packaging Helm Chart")
  st
})

lazy val githubReleaseDraft = ReleaseStep(action = st => {
  val (releaseVersion, _) = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
  exec(
    s"./scripts/github_release.sh lightbend/kafka-lag-exporter v$releaseVersion -- kafka-lag-exporter-$releaseVersion.tgz",
    "Error while publishing GitHub release draft")
  st
})

lazy val publishDockerImage = ReleaseStep(
  action = { st: State =>
    val extracted = Project.extract(st)
    val ref = extracted.get(thisProjectRef)
    extracted.runAggregated(publish in Docker in ref, st)
  }
)