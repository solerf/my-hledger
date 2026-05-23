package app.frontend

import app.shared.dtos.MonthlyExpense
import com.raquo.laminar.api.L.*

final case class Row(
  yearMonth: String,
  account: String,
  date: String,
  amount: BigDecimal
)

final case class AppState(api: ExpensesApi) {

  private val rowsVar: Var[List[MonthlyExpense]] = Var(Nil)
  private val monthsVar: Var[List[String]]       = Var(Nil) // YYYY-MM
  private val mountedVar: Var[Boolean]            = Var(false)

  private val selectedMonthVar: Var[String] = Var("") // YYYY-MM

  val signals: Signals =
    new Signals(rowsVar.signal, monthsVar.signal, selectedMonthVar.signal, mountedVar.signal)

  val selectedMonthWriter: Var[String] = selectedMonthVar

  def updateExpensesRows(rows: List[MonthlyExpense]): Unit = {
    if (monthsVar.now().isEmpty) setMonths(rows.map(_.yearMonth).distinct.sorted)
    val sel = selectedMonthVar.now()
    val filtered =
      if (sel.isEmpty) rows
      else rows.filter(_.yearMonth == sel)
    rowsVar.set(filtered)
    if (!mountedVar.now()) mountedVar.set(true)
  }

  private def setMonths(months: List[String]): Unit = {
    monthsVar.set(months)
    if (selectedMonthVar.now().isEmpty) months.lastOption.foreach(selectedMonthVar.set)
  }

  final class Signals private[AppState] (
    val monthlyExpenses: Signal[List[MonthlyExpense]],
    val months: Signal[List[String]],
    val monthSelected: Signal[String],
    val mounted: Signal[Boolean]
  ) {

    final private case class RowsPartition(
      expenses: List[Row],
      revenues: List[Row],
      rawRevenues: List[Row]
    )

    private val partitions: Signal[RowsPartition] = monthlyExpenses.map(partitionMonthlyEntries)

    val expenses: Signal[List[Row]]    = partitions.map(_.expenses)
    val revenues: Signal[List[Row]]    = partitions.map(_.revenues)
    val rawRevenues: Signal[List[Row]] = partitions.map(_.rawRevenues)

    val expensesAndRevenuesByDate: Signal[List[(String, List[Row])]] =
      expenses.combineWith(revenues).map(_ ++ _).map(_.groupBy(_.date).toList.sortBy(_._1).reverse)

    val dailySpend: Signal[List[(String, Double)]]  = expenses.map(dailyTotals)
    val dailyIncome: Signal[List[(String, Double)]] = revenues.map(dailyTotals)

    private def partitionMonthlyEntries(monthlyEntries: List[MonthlyExpense]): RowsPartition = {
      val union = monthlyEntries.map { me =>
        // 1st fold inner
        me.entries.foldLeft((List.empty[Row], List.empty[Row], List.empty[Row])) {
          case ((exp, rev, rawRev), next) if next.account.startsWith("expense") =>
            (
              Row(
                yearMonth = me.yearMonth,
                account = next.account,
                date = next.date,
                amount = next.amount
              ) :: exp,
              rev,
              rawRev
            )

          case ((exp, rev, rawRev), next) if next.account.startsWith("revenues") =>
            // Income postings are stored as negatives in hledger (credits) invert them so they plot as positive
            val row = Row(
              yearMonth = me.yearMonth,
              account = next.account,
              date = next.date,
              amount = next.amount
            )
            (
              exp,
              row.copy(amount = next.amount * -1) :: rev,
              row :: rawRev
            )

          case ((exp, rev, rawRev), _) =>
            (exp, rev, rawRev)
        }
      }
        // 2nd fold outer
        .foldLeft((List.empty[Row], List.empty[Row], List.empty[Row])) {
          case ((expAcc, revAcc, rawRevAcc), (exp, rev, rawRev)) =>
            (expAcc ++ exp, revAcc ++ rev, rawRevAcc ++ rawRev)
        }

      RowsPartition(union._1.reverse, union._2.reverse, union._3.reverse)
    }

    private def dailyTotals(rows: List[Row]): List[(String, Double)] =
      rows.groupMapReduce(_.date)(_.amount.toDouble)(_ + _).toList.sortBy(_._1)

  }

}
