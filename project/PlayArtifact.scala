package sbt.plugins

import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._
import com.typesafe.sbt.packager

object PlayArtifact extends Plugin {

  val playArtifact = TaskKey[File]("play-artifact", "Builds a deployable zip file for magenta")
  val playArtifactResources = TaskKey[Seq[(File, String)]](
    "play-artifact-resources", "Files that will be collected by the deployment-artifact task")
  val playArtifactFile = SettingKey[String](
    "play-artifact-file", "Filename of the artifact built by deployment-artifact")

  val magentaPackageName = SettingKey[String]("magenta-package-name", "Name of the magenta package")

  lazy val playArtifactDistSettings = assemblySettings ++ Seq(
    mainClass in assembly := Some("play.core.server.NettyServer"),
    jarName in assembly := "app.jar",

    // package config for Magenta and Upstart
    playArtifactResources <<= (assembly, baseDirectory, name, magentaPackageName) map {
      (assembly, base, name, packageName) => {
        val common = Seq(
          base / "conf" / "deploy.json" -> "deploy.json",
          base / "conf" / "start.sh" -> s"packages/$packageName/start.sh",
          base / "conf" / (name + ".conf") -> s"packages/$packageName/$name.conf",
          assembly -> s"packages/$packageName/${assembly.getName}"
        )

        val cropper = Seq(
          base / "cmyk.icc"      -> s"packages/$packageName/cmyk.icc",
          base / "grayscale.icc" -> s"packages/$packageName/grayscale.icc",
          base / "srgb.icc"      -> s"packages/$packageName/srgb.icc"
        )

       name match {
         case "cropper" => common ++ cropper
         case default => common
       }
      }
    },

    playArtifactFile := "artifacts.zip",
    playArtifact <<= buildDeployArtifact,
    packager.Keys.dist <<= buildDeployArtifact tag Tags.Disk,
    assembly <<= assembly.tag(Tags.Disk),

    mergeStrategy in assembly <<= (mergeStrategy in assembly) { current =>
    {
      // Take ours, i.e. MergeStrategy.first...
      case "logger.xml" => MergeStrategy.first
      case "version.txt" => MergeStrategy.first

      // Merge play.plugins because we need them all
      case "play.plugins" => MergeStrategy.filterDistinctLines

      // Try to be helpful...
      case "overview.html" => MergeStrategy.first
      case "NOTICE" => MergeStrategy.first
      case "LICENSE" => MergeStrategy.first
      case meta if meta.startsWith("META-INF/") => MergeStrategy.first

      case other => current(other)
    }
    },

    excludedFiles in assembly := { (bases: Seq[File]) =>
      bases flatMap { base => (base / "META-INF" * "*").get } collect {
        case f if f.getName.toLowerCase == "license" => f
        case f if f.getName.toLowerCase == "manifest.mf" => f
        case f if f.getName.endsWith(".SF") => f
        case f if f.getName.endsWith(".DSA") => f
        case f if f.getName.endsWith(".RSA") => f
      }
    }
  )

  private def buildDeployArtifact = (streams, target, playArtifactResources, playArtifactFile, magentaPackageName) map {
    (s, target, resources, artifactFileName, magentaPackageName) =>
      val distFile = target / artifactFileName
      s.log.info("Disting " + distFile)

      if (distFile.exists()) {
        distFile.delete()
      }
      IO.zip(resources, distFile)

      s.log.info("Done disting.")
      distFile
  }
}
