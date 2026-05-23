package app.frontend.charts

import org.scalajs.dom

import scala.scalajs.js

final class Instance(canvasId: String):

  private var instance: Option[js.Dynamic] = None

  def render(cfg: js.Dynamic): Unit =
    Option(dom.document.getElementById(canvasId))
      .foreach { canvas =>
        instance match {
          case Some(chart) =>
            chart.data = cfg.data
            chart.options = cfg.options
            chart.update("none")
          case None =>
            val Chart = js.Dynamic.global.Chart
            instance = Some(js.Dynamic.newInstance(Chart)(canvas, cfg))
        }
      }
