package app.frontend.view

import app.frontend.charts.monthly.{AccountsLineChartView, LineChartView, PieChartView}
import app.frontend.{AppState, Loadable, Row}

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLDivElement, HTMLTableRowElement}

/** Monthly expenses view: the charts and the entries table for the selected
  * month. Owns the loading/error states for the monthly data feed.
  */
object Monthly:

  // Singletons so each pie keeps a persistent Chart.js instance across the
  // monthly-data re-renders (same pattern as the other chart views).
  private val expensesPie    = PieChartView("chart-pie-expenses", "Expenses", "doughnut")
  private val liabilitiesPie = PieChartView("chart-pie-liabilities", "Liabilities")

  def view(
    state: AppState,
    monthChangeObserver: Observer[String]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      child <-- state.signals.monthly.map {
        case Loadable.Loading     => Panel("Loading", "Loading journal…")
        case Loadable.Failed(msg) => Panel("Journal not found", msg)
        case Loadable.Loaded(_)   => loadedView(state, monthChangeObserver)
      }
    )

  private def loadedView(
    state: AppState,
    monthChangeObserver: Observer[String]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      monthSelector(state, monthChangeObserver),
      // Two pie charts side by side, taking the full-width slot the "By account"
      // bar chart used to occupy: expenses on the left, liabilities on the right.
      div(
        cls := "row",
        div(cls := "col-12", expensesPie.view(state.signals.expenses))
      ),
      div(
        cls := "row",
        div(cls := "col-12", liabilitiesPie.view(state.signals.liabilities))
      ),
      // Temporarily hidden — "By account" bar chart and "Account breakdown"
      // stacked bar chart:
      // div(cls := "row", div(cls := "col-12", BarChartView.view(state.signals.expenses))),
      // div(
      //   cls := "row",
      //   div(cls := "col-12", StackedBarChartView.view(state.signals.expenses))
      // ),

      div(
        cls := "row",
        div(cls := "col-12", AccountsLineChartView.view(state.signals.expenses))
      ),
      div(
        cls := "row",
        div(
          cls := "col-12",
          LineChartView.view(state.signals.dailySpend, state.signals.dailyIncome)
        )
      ),
      entriesTable(state.signals.entriesAsRows)
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
          val isExpense = parentAcc == "expenses" || parentAcc == "liabilities"
          val accSum    = subAccRows.map(_.amount).sum

          val headerDesc = subAccRows.headOption
            .map(r => Seq(r.description, r.comment).filter(_.nonEmpty).mkString(" | "))
            .collect { case s if s.nonEmpty => s"($s)" }
            .getOrElse("")

          val accHeader = tr(
            td(
              s"$parentAcc $headerDesc",
              cls := "text-end fst-italic"
            ),
            td(),
            td(
              cls                := "amount-num",
              cls("text-danger") := isExpense,
              formatAmount(accSum)
            )
          )
          val entries = subAccRows.map {
            r =>
              val desc =
                Option(r.description).collect { case s if s.nonEmpty => s"($s)" }.getOrElse("")
              tr(
                td(),
                td(
                  s"${r.account.split(":").tail.mkString(":")} $desc",
                  cls := "fst-italic"
                ),
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
