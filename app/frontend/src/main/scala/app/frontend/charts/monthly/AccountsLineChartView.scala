package app.frontend.charts.monthly

import app.frontend.Row
import app.frontend.charts.{Colors, Instance}

import scala.scalajs.js

import com.raquo.laminar.api.L.*

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
        tension = 0,
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
      elements = js.Dynamic.literal(
        point = js.Dynamic.literal(radius = 2, hoverRadius = 2)
      ),
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(position = "top")
      )
    )

    handle.render(js.Dynamic.literal(`type` = "line", data = data, options = options))
  }
