import mill._
import scalalib._
import scalafmt._

object events extends ScalaModule with ScalafmtModule {

  def scalaVersion = "2.12.7"

  def scalacOptions = Seq("-feature", "-deprecation", "-unchecked", "-Ypartial-unification")

  def scalacPluginIvyDeps = Agg(ivy"org.spire-math::kind-projector:0.9.7")

  def ivyDeps = Agg(
    ivy"co.fs2::fs2-core:1.0.0",
    ivy"com.github.ben-manes.caffeine:caffeine:2.6.2")

}
