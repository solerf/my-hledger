package app.frontend

package object charts {

  private def pickColors(n: Int): Vector[String] = Vector.fill(n)(randomColor())

  private def randomColor(): String = "#%06x".format(scala.util.Random.nextInt(1 << 24))
}
