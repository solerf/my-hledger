package app.frontend

import app.shared.dtos.MonthlyExpense
import com.raquo.laminar.api.L.*

final case class AppState(api: ExpensesApi):

  private val rowsVar: Var[List[MonthlyExpense]] = Var(Nil)
  private val monthsVar: Var[List[String]]       = Var(Nil) // YYYY-MM

  private val selectedMonthVar: Var[String] = Var("") // YYYY-MM

  val rowsSignal: Signal[List[MonthlyExpense]] = rowsVar.signal
  val monthsSignal: Signal[List[String]]       = monthsVar.signal
  val selectedMonthSignal: Signal[String]      = selectedMonthVar.signal

  val selectedMonthWriter: Var[String] = selectedMonthVar

  def isRowsEmpty: Boolean = rowsVar.now().isEmpty

  def updateExpensesRows(rows: List[MonthlyExpense]): Unit = {
    if (monthsVar.now().isEmpty) setMonths(rows.map(_.yearMonth).distinct.sorted)
    rowsVar.set(rows)
  }

  private def setMonths(months: List[String]): Unit = {
    monthsVar.set(months)
    if (selectedMonthVar.now().isEmpty) months.lastOption.foreach(selectedMonthVar.set)
  }
