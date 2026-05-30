package app.frontend

import app.frontend.ExpensesApi.ErrorOr
import app.shared.dtos.MonthlyExpense

import scala.collection.mutable

import com.raquo.laminar.api.L.*

enum NavView(val label: String):
  case Monthly     extends NavView("Monthly")
  case YearToNow   extends NavView("Year To Now")
  case ManualEntry extends NavView("Manual Entry")

final case class Row(
  yearMonth: String,
  account: String,
  comment: String,
  description: String,
  txComment: String,
  date: String,
  amount: BigDecimal
)

final case class AppState(api: ExpensesApi) {

  // ── Shared chrome ────────────────────────────────────────────────
  private val navViewVar: Var[NavView] = Var(NavView.Monthly)

  val navViewWriter: Var[NavView] = navViewVar
  val navView: Signal[NavView]    = navViewVar.signal

  // ── Backend/hledger-web connectivity ─────────────────────────────
  // Optimistically assume reachable until the bootstrap health check says
  // otherwise; when false the views are replaced by an error panel.
  private val hledgerReachableVar: Var[Boolean] = Var(true)
  val hledgerReachable: Signal[Boolean]         = hledgerReachableVar.signal

  def setHledgerReachable(reachable: Boolean): Unit = hledgerReachableVar.set(reachable)

  // ── Monthly view state ───────────────────────────────────────────
  // The raw API result drives the view directly: a loading panel until
  // the first response arrives, then either the rendered rows or the
  // surfaced error message.
  private val monthlyVar: Var[Loadable[List[MonthlyExpense]]] = Var(Loadable.Loading)
  private val monthsVar: Var[List[String]]                    = Var(Nil) // YYYY-MM
  private val selectedMonthVar: Var[String]                   = Var("")  // YYYY-MM

  val selectedMonthWriter: Var[String] = selectedMonthVar

  val signals: Signals =
    new Signals(monthlyVar.signal, monthsVar.signal, selectedMonthVar.signal)

  /** Forward a raw API result into the monthly view state. On success the
    * rows are filtered to the selected month and exposed for rendering; on
    * failure the error message is surfaced in place of the content.
    */
  def updateExpensesRows(result: ErrorOr[List[MonthlyExpense]]): Unit =
    result match {
      case Right(rows) =>
        if (monthsVar.now().isEmpty) setMonths(rows.map(_.yearMonth).distinct.sorted)
        val sel      = selectedMonthVar.now()
        val filtered =
          if (sel.isEmpty) rows
          else rows.filter(_.yearMonth == sel)
        monthlyVar.set(Loadable.Loaded(filtered))

      case Left(err) =>
        monthlyVar.set(Loadable.Failed(err.toString))
    }

  private def setMonths(months: List[String]): Unit = {
    monthsVar.set(months)
    if (selectedMonthVar.now().isEmpty) months.lastOption.foreach(selectedMonthVar.set)
  }

  final class Signals private[AppState] (
    val monthly: Signal[Loadable[List[MonthlyExpense]]],
    val months: Signal[List[String]],
    val monthSelected: Signal[String]
  ) {

    // Rows currently available for rendering — empty while loading or failed.
    private val monthlyExpenses: Signal[List[MonthlyExpense]] =
      monthly.map {
        case Loadable.Loaded(rows) => rows
        case _                     => Nil
      }

    final private case class RowsPartition(
      expenses: List[Row],
      liabilities: List[Row],
      revenues: List[Row],
      rawRevenues: List[Row]
    )

    private val partitions: Signal[RowsPartition] = monthlyExpenses.map(partitionMonthlyEntries)

    val expenses: Signal[List[Row]]    = partitions.map(_.expenses)
    val liabilities: Signal[List[Row]] = partitions.map(_.liabilities)
    val revenues: Signal[List[Row]]    = partitions.map(_.revenues)
    val rawRevenues: Signal[List[Row]] = partitions.map(_.rawRevenues)

    val entriesAsRows: Signal[List[(String, List[Row])]] =
      expenses
        .combineWith(revenues)
        .combineWith(liabilities)
        .map(_ ++ _ ++ _)
        .map(_.groupBy(_.date).toList.sortBy(_._1).reverse)

    val dailySpend: Signal[List[(String, Double)]]  = expenses.map(dailyTotals)
    val dailyIncome: Signal[List[(String, Double)]] = revenues.map(dailyTotals)

    private def partitionMonthlyEntries(monthlyEntries: List[MonthlyExpense]): RowsPartition = {

      def getAccountPrefix(account: String): Option[String] =
        Option.when {
          account.startsWith("expense") ||
          account.startsWith("revenues") ||
          account.startsWith("liabilities")
        }(account.split(":").head)

      val union = monthlyEntries.map { me =>
        val (comment, desc) = (me.comment, me.description)
        // 1st fold inner
        me.entries.foldLeft(mutable.Map.empty[String, List[Row]]) {
          case (acc, next) =>
            getAccountPrefix(next.account) match {
              case None          => acc
              case Some(accName) =>
                val row = Row(
                  yearMonth = me.yearMonth,
                  account = next.account,
                  comment = comment,
                  description = desc,
                  txComment = next.comment,
                  date = next.date,
                  amount = next.amount
                )
                val _ = acc.updateWith(accName) {
                  case Some(v) => Some(row :: v)
                  case _       => Some(List(row))
                }
                acc
            }
        }
      }
        // 2nd "fold" outer
        .reduce {
          case (m1, m2) =>
            m1 ++ m2.map { case (k, v) => k -> (v ++ m1.getOrElse(k, List.empty)) }
        }

      RowsPartition(
        expenses = union.getOrElse("expenses", List.empty),
        liabilities = union.getOrElse("liabilities", List.empty),
        // Income postings are stored as negatives in hledger (credits) invert them so they plot as positive
        revenues = union.getOrElse("revenues", List.empty).map(r => r.copy(amount = r.amount * -1)),
        rawRevenues = union.getOrElse("expenses", List.empty)
      )
    }

    private def dailyTotals(rows: List[Row]): List[(String, Double)] =
      rows.groupMapReduce(_.date)(_.amount.toDouble)(_ + _).toList.sortBy(_._1)

  }

}
