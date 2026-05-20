package app.frontend

import app.frontend.charts.{AccountsLineChartView, BarChartView, LineChartView, PieChartView}
import app.shared.dtos.MonthlyExpense
import com.raquo.laminar.api.L.*

object Views:

  final case class Row(yearMonth: String, account: String, amount: BigDecimal, date: String)

  private def flattenRowsByPrefix(
    monthly: List[MonthlyExpense],
    prefix: String,
    negate: Boolean = false
  ): List[Row] =
    monthly.flatMap { m =>
      m.entries
        .collect {
          case e if e.account.startsWith(prefix) =>
            val amt = if negate then -e.amount else e.amount
            Row(m.yearMonth, e.account, amt, e.date)
        }
    }

  private def dailyTotals(rows: List[Row]): List[(String, BigDecimal)] =
    rows.groupMapReduce(_.date)(_.amount)(_ + _).toList.sortBy(_._1)

  def app(state: AppState): HtmlElement =
    val rowsSignal = state.rowsSignal.map(flattenRowsByPrefix(_, "expenses"))
    val spendDaily = rowsSignal.map(dailyTotals)
    // Income postings are stored as negatives in hledger (credits)
    // invert them so they plot as positive
    val incomeRows    = state.rowsSignal.map(flattenRowsByPrefix(_, "revenues", negate = true))
    val earningsDaily = incomeRows.map(dailyTotals)
    div(
      navbar(),
      monthSelector(state),
      div(cls := "row", div(cls := "col-12", BarChartView.view(rowsSignal))),
      div(cls := "row", div(cls := "col-12", PieChartView.view(rowsSignal))),
      div(cls := "row", div(cls := "col-12", LineChartView.view(spendDaily, earningsDaily))),
      div(cls := "row", div(cls := "col-12", AccountsLineChartView.view(rowsSignal))),
      entriesTable(rowsSignal)
    )

  private def navbar(): HtmlElement =
    navTag(
      cls := "navbar navbar-dark bg-dark mb-4 rounded",
      div(
        cls := "container-fluid",
        span(cls := "navbar-brand mb-0 h1", "my-hledger"),
        span(cls := "navbar-text text-light small", "monthly expenses")
      )
    )

  private def monthSelector(state: AppState): HtmlElement =
    div(
      cls := "month-selector mb-3",
      label(cls := "form-label", forId := "month-select", "Month"),
      select(
        idAttr := "month-select",
        cls    := "form-select",
        controlled(
          value <-- state.selectedMonthSignal,
          onChange.mapToValue --> state.selectedMonthWriter
        ),
        children <-- state.monthsSignal.map { months =>
          months.map(m => option(value := m, m))
        }
      )
    )

  private def entriesTable(rowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "table-responsive entries-scroll",
      table(
        cls := "table table-striped table-sm align-middle w-100",
        thead(cls := "table-light", tr(th("Date"), th("Account"), th("Amount"))),
        tbody(
          children <-- rowsSignal.map { rows =>
            rows
              .groupBy(_.date)
              .toList
              .sortBy(_._1)
              .flatMap { case (date, group) =>
                val header = tr(
                  cls := "table-secondary",
                  td(colSpan := 3, strong(date))
                )
                val entries = group.map { r =>
                  tr(
                    td(),
                    td(r.account),
                    td(
                      cls                := "amount-num",
                      cls("text-danger") := r.amount < 0,
                      formatAmount(r.amount)
                    )
                  )
                }
                header :: entries
              }
          }
        )
      )
    )

  private def formatAmount(a: BigDecimal): String =
    a.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString
