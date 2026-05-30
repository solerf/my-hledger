package app.frontend.charts.yeartonow

import app.frontend.charts.{Colors, Instance}
import app.shared.dtos.MonthlyExpense

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** Year-to-now line chart: one line per (main account type, currency) — the
  * first account segment (e.g. expenses, revenues, assets, liabilities, equity)
  * split by commodity (EUR, USD, …) — plotting the running cumulative total by
  * month. Currencies are never summed together. Amounts keep hledger's sign
  * convention, so credit-normal types (revenues, liabilities) trend negative.
  */
object MonthlyCumulativeChartView:

  val canvasId = "chart-year-cumulative"

  private val handle = new Instance(canvasId)

  def view(dataSignal: Signal[List[MonthlyExpense]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "Cumulative by main account"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        dataSignal --> Observer[List[MonthlyExpense]](render)
      )
    )

  private def render(rows: List[MonthlyExpense]): Unit = {
    // Sorted month axis shared by every line.
    val months = rows.map(_.yearMonth).distinct.sorted

    // (accountType, currency, month) -> summed amount posted that month.
    // Currencies are kept separate: summing EUR and USD into one figure would
    // be meaningless, so each (type, currency) becomes its own line.
    val byTypeCurrencyMonth: Map[(String, String, String), BigDecimal] =
      rows
        .flatMap(me =>
          me.entries.map(e => (e.account.split(":").head, e.currency, me.yearMonth, e.amount))
        )
        .groupMapReduce { case (tpe, cur, month, _) => (tpe, cur, month) } {
          case (_, _, _, amt) => amt
        }(_ + _)

    // One line per (account type, currency), sorted for a stable legend/colours.
    val series = byTypeCurrencyMonth.keys.map { case (tpe, cur, _) =>
      (tpe, cur)
    }.toList.distinct.sorted
    val colors = Colors.pickColors(series.map { case (tpe, cur) => s"$tpe ($cur)" }).toList

    val datasets = series.zip(colors).map {
      case ((tpe, cur), color) =>
        // Per-month totals aligned to the axis, then a running cumulative sum.
        val monthly =
          months.map(m => byTypeCurrencyMonth.getOrElse((tpe, cur, m), BigDecimal(0)).toDouble)
        val cumulative = monthly.scanLeft(0d)(_ + _).drop(1)
        js.Dynamic.literal(
          label = s"$tpe ($cur)",
          data = js.Array(cumulative*),
          borderColor = color,
          backgroundColor = color,
          tension = 0,
          fill = false
        )
    }

    val data = js.Dynamic.literal(
      labels = js.Array(months*),
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
