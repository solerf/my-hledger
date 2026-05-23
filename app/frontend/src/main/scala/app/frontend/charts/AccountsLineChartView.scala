package app.frontend.charts

import app.frontend.Row
import com.raquo.laminar.api.L.*

import scala.scalajs.js

object AccountsLineChartView:

  val canvasId = "chart-accounts-line"

  private val handle = new Instance(canvasId)

  def view(dataRowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "By account, by date"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        dataRowsSignal --> Observer[List[Row]](render)
      )
    )

  private def render(rows: List[Row]): Unit = {
    val (labels, accounts) = rows.map(r => (r.date, r.account)).unzip match {
      case (d, a) => (d.distinct.sorted, a.distinct.sorted)
    }

    val colors = Colors.pickColors(accounts).toList

    val byAcctDate: Map[(String, String), BigDecimal] =
      rows
        .groupMapReduce(r => (r.account, r.date))(_.amount)(_ + _)

    val datasets = accounts.zip(colors).map { case (acct, color) =>
      val series = labels.map(d => byAcctDate.getOrElse((acct, d), BigDecimal(0)).toDouble)
      js.Dynamic.literal(
        label = acct,
        data = js.Array(series*),
        borderColor = color,
        backgroundColor = color,
        tension = 0.25,
        fill = false
      )
    }

    val data = js.Dynamic.literal(
      labels = js.Array(labels*),
      datasets = js.Array(datasets*)
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
