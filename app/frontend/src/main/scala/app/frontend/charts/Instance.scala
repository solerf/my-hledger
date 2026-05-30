package app.frontend.charts

import scala.scalajs.js

import org.scalajs.dom

final class Instance(canvasId: String):

  private var instance: Option[js.Dynamic] = None

  def render(cfg: js.Dynamic): Unit =
    Option(dom.document.getElementById(canvasId))
      .foreach { canvas =>
        instance match {
          // Reuse the live chart only if it is still bound to the canvas
          // currently in the DOM. After navigating away and back, Laminar
          // recreates the <canvas>, so the old instance points at a detached
          // node — destroy it and build a fresh chart on the new canvas.
          case Some(chart) if chart.canvas.asInstanceOf[dom.Node] eq canvas =>
            chart.data = cfg.data
            chart.options = cfg.options
            chart.update("none")
          case other =>
            other.foreach(_.destroy())
            val Chart = js.Dynamic.global.Chart
            instance = Some(js.Dynamic.newInstance(Chart)(canvas, cfg))
        }
      }
