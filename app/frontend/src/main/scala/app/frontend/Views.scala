package app.frontend

import app.frontend.charts.{AccountsLineChartView, BarChartView, LineChartView, StackedBarChartView}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLDivElement, HTMLTableRowElement}

object Views:

  def render(
    state: AppState,
    monthChangeObserver: Observer[String]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      navbar(),
      child <-- state.signals.mounted.distinct.map { loaded =>
        if (!loaded) div(cls := "row", div(cls := "col-12", noJournal()))
        else loadedView(state, monthChangeObserver)
      }
    )

  private def loadedView(
    state: AppState,
    monthChangeObserver: Observer[String]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      monthSelector(state, monthChangeObserver),
      div(cls := "row", div(cls := "col-12", BarChartView.view(state.signals.expenses))),
      div(
        cls := "row",
        div(cls := "col-12", StackedBarChartView.view(state.signals.expenses))
      ),
      div(
        cls := "row",
        div(
          cls := "col-12",
          LineChartView.view(state.signals.dailySpend, state.signals.dailyIncome)
        )
      ),
      div(
        cls := "row",
        div(cls := "col-12", AccountsLineChartView.view(state.signals.expenses))
      ),
      entriesTable(state.signals.expensesAndRevenuesByDate)
    )

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

  private def monthSelector(state: AppState, monthChangeObserver: Observer[String]): HtmlElement =
    div(
      cls := "month-selector mb-3",
      label(cls := "form-label", forId := "month-select", "Month"),
      select(
        idAttr := "month-select",
        cls    := "form-select",
        controlled(
          value <-- state.signals.monthSelected,
          onChange.mapToValue --> state.selectedMonthWriter
        ),
        children <--
          state.signals.months
            .combineWith(state.signals.monthSelected).map {
              case (months, sel) =>
                months.map(m => option(value := m, selected := (m == sel), m))
            },
        state.signals.monthSelected.changes --> monthChangeObserver
      )
    )

  private def entriesTable(rowsSignal: Signal[List[(String, List[Row])]]): HtmlElement =
    div(
      cls := "table-responsive entries-scroll",
      table(
        cls := "table table-striped table-sm align-middle w-100",
        thead(cls := "table-light", tr(th("Date"), th("Account"), th("Amount"))),
        tbody(
          children <-- rowsSignal.map { rows =>
            rows
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
      .sortBy(_._1) // head account
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
