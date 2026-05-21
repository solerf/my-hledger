package app.frontend

import app.frontend.charts.{AccountsLineChartView, BarChartView, LineChartView, StackedBarChartView}
import app.shared.dtos.MonthlyExpense
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableRowElement

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
    if (state.isRowsEmpty) {
      div(
        navbar(),
        div(cls := "row", div(cls := "col-12", noJournal()))
      )
    } else {
      val rowsSignal = state.rowsSignal.map(flattenRowsByPrefix(_, "expenses"))
      val spendDaily = rowsSignal.map(dailyTotals)
      // Income postings are stored as negatives in hledger (credits)
      // invert them so they plot as positive
      val incomeRows    = state.rowsSignal.map(flattenRowsByPrefix(_, "revenues", negate = true))
      val earningsDaily = incomeRows.map(dailyTotals)
      // Table shows both: expenses as positive, revenues kept negative so the
      // sign distinguishes them and triggers the red styling.
      val rawRevenues = state.rowsSignal.map(flattenRowsByPrefix(_, "revenues"))
      val tableRows   = rowsSignal.combineWith(rawRevenues).map((e, r) => e ++ r)
      div(
        navbar(),
        monthSelector(state),
        div(cls := "row", div(cls := "col-12", BarChartView.view(rowsSignal))),
        div(cls := "row", div(cls := "col-12", StackedBarChartView.view(rowsSignal))),
        div(cls := "row", div(cls := "col-12", LineChartView.view(spendDaily, earningsDaily))),
        div(cls := "row", div(cls := "col-12", AccountsLineChartView.view(rowsSignal))),
        entriesTable(tableRows)
      )
    }

  private def noJournal() =
    div(
      cls  := "alert alert-warning shadow-sm mb-4",
      role := "alert",
      "Hledger Journal not found"
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
                val dateHeader = tr(cls := "table-secondary", td(colSpan := 3, strong(date)))
                val entries    = buildRows(group)
                dateHeader :: entries
              }
          }
        )
      )
    )

  private def buildRows(rows: List[Row]): List[ReactiveHtmlElement[HTMLTableRowElement]] =
    rows
      .groupBy(_.account.split(":").head)
      .toList
      .sortBy(_._1)
      .flatMap {
        case (parentAcc, subAccRows) =>
          val isExpense = parentAcc == "expenses"
          val accSum    = subAccRows.map(_.amount).sum
          val accHeader = tr(
            td(parentAcc, cls := "text-end fst-italic"),
            td(),
            td(
              cls                := "amount-num",
              cls("text-danger") := isExpense,
              formatAmount(accSum)
            )
          )
          val entries = subAccRows.map {
            r =>
              tr(
                td(),
                td(r.account.split(":").tail.mkString(":"), cls := "fst-italic"),
                td(
                  cls                := "amount-num",
                  cls("text-danger") := isExpense,
                  formatAmount(r.amount)
                )
              )
          }
          accHeader :: entries
      }

  private def formatAmount(a: BigDecimal): String =
    a.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString
