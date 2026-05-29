package app.frontend.charts.monthly

import app.frontend.charts.Instance
import com.raquo.laminar.api.L.*

import scala.scalajs.js

object LineChartView:

  val canvasId = "chart-line"

  private val handle = new Instance(canvasId)

  def view(
    spendSignal: Signal[List[(String, Double)]],
    earningsSignal: Signal[List[(String, Double)]]
  ): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "Cumulative"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        spendSignal.combineWith(earningsSignal) -->
          Observer[
            (List[(String, Double)], List[(String, Double)])
          ] { case (s, e) => render(s, e) }
      )
    )

  private def cumulative(series: List[(String, Double)]): List[Double] =
    series
      .scanLeft(0d) { case (acc, (_, v)) => acc + v }
      .drop(1)

  private def render(
    spend: List[(String, Double)],
    earnings: List[(String, Double)]
  ): Unit = {
    // Build a unified, sorted date axis covering both series.
    val labels = (spend.map(_._1) ++ earnings.map(_._1)).distinct.sorted

    val spendMap                      = spend.toMap
    val earningsMap                   = earnings.toMap
    val (spendOnAxis, earningsOnAxis) =
      labels.map {
        d =>
          val s = (d, spendMap.getOrElse(d, 0d))
          val e = (d, earningsMap.getOrElse(d, 0d))
          (s, e)
      }.unzip

    val spendAcc    = cumulative(spendOnAxis)
    val earningsAcc = cumulative(earningsOnAxis)

    val spendColor    = "#c10b0b"
    val earningsColor = "#1f7a1f"

    val data = js.Dynamic.literal(
      labels = js.Array(labels*),
      datasets = js.Array(
        js.Dynamic.literal(
          label = "Spend",
          data = js.Array(spendAcc*),
          borderColor = spendColor,
          backgroundColor = spendColor,
          tension = 0.25,
          fill = false
        ),
        js.Dynamic.literal(
          label = "Earnings",
          data = js.Array(earningsAcc*),
          borderColor = earningsColor,
          backgroundColor = earningsColor,
          tension = 0.25,
          fill = false
        )
      )
    )

    val options = js.Dynamic.literal(
      responsive = true,
      maintainAspectRatio = false,
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(position = "bottom")
      )
    )

    handle.render(js.Dynamic.literal(`type` = "line", data = data, options = options))
  }
