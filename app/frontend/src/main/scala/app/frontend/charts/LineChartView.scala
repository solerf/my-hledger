package app.frontend.charts

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** Cumulative line chart with two series: spend and earnings per day. */
object LineChartView:

  val canvasId = "chart-line"

  private val handle = new ChartHandle(canvasId)

  def view(
      spendSignal: Signal[List[(String, BigDecimal)]],
      earningsSignal: Signal[List[(String, BigDecimal)]]
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
        spendSignal.combineWith(earningsSignal) --> Observer[
          (List[(String, BigDecimal)], List[(String, BigDecimal)])
        ] { case (s, e) => render(s, e) }
      )
    )

  private def cumulative(series: List[(String, BigDecimal)]): List[BigDecimal] =
    series
      .scanLeft(BigDecimal(0)) { case (acc, (_, v)) => acc + v }
      .drop(1)

  private def render(
      spend: List[(String, BigDecimal)],
      earnings: List[(String, BigDecimal)]
  ): Unit = {
    // Build a unified, sorted date axis covering both series.
    val labels = (spend.map(_._1) ++ earnings.map(_._1)).distinct.sorted

    val spendMap    = spend.toMap
    val earningsMap = earnings.toMap
    val spendOnAxis    = labels.map(d => (d, spendMap.getOrElse(d, BigDecimal(0))))
    val earningsOnAxis = labels.map(d => (d, earningsMap.getOrElse(d, BigDecimal(0))))

    val spendCum    = cumulative(spendOnAxis).map(_.toDouble)
    val earningsCum = cumulative(earningsOnAxis).map(_.toDouble)

    val spendColor    = "#c10b0b"
    val earningsColor = "#1f7a1f"

    val data = js.Dynamic.literal(
      labels = js.Array(labels*),
      datasets = js.Array(
        js.Dynamic.literal(
          label = "Spend",
          data = js.Array(spendCum*),
          borderColor = spendColor,
          backgroundColor = spendColor,
          tension = 0.25,
          fill = false
        ),
        js.Dynamic.literal(
          label = "Earnings",
          data = js.Array(earningsCum*),
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
