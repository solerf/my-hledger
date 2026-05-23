package app.backend.service

import app.backend.hledger.HledgerApi
import app.backend.hledger.model.Transaction
import app.shared.dtos.{ExpenseEntry, MonthlyExpense}
import cats.Monad
import cats.syntax.all.*
import org.typelevel.log4cats.{Logger, LoggerFactory}

sealed trait ExpensesService[F[_]]:
  def monthly(month: Option[String]): F[List[MonthlyExpense]]
  def accounts(): F[List[String]]

object ExpensesService:

  def apply[F[_]: {Monad, LoggerFactory}](hledgerAPI: HledgerApi[F]): ExpensesService[F] = {
    given Logger[F] = LoggerFactory[F].getLogger
    ExpensesServiceImpl(hledgerAPI)
  }

  private case class ExpensesServiceImpl[F[_]: {Monad, Logger}](hledgerAPI: HledgerApi[F])
      extends ExpensesService[F] {

    override def monthly(month: Option[String]): F[List[MonthlyExpense]] =
      for {
        _                   <- Logger[F].info(s"monthly: month=${month.getOrElse("-")}")
        journalTransactions <- hledgerAPI.transactions()
        expenses =
          journalTransactions
            .foldLeft(Map.empty[String, Seq[Transaction]]) {
              case (acc, next) if month.isEmpty || month.contains(next.tyearmonth) =>
                acc.updatedWith(next.tyearmonth) {
                  case Some(v) => Some(v ++ Seq(next))
                  case _       => Some(Seq(next))
                }
              case (acc, _) =>
                acc
            }
            .flatMap {
              case (_, transactions) =>
                transactions.map { t =>
                  val entries =
                    t.tpostings.map { p =>
                      ExpenseEntry(
                        date = t.tdate,
                        account = p.paccount,
                        amount = BigDecimal(p.pamount.head.aquantity.floatingPoint),
                        currency = p.pamount.head.acommodity,
                        comment = Option(p.pcomment.trim()).filter(_.nonEmpty).getOrElse(t.tcomment.trim())
                      )
                    }
                  MonthlyExpense(
                    yearMonth = t.tyearmonth,
                    comment = t.tcomment.trim(),
                    description = t.tdescription.trim(),
                    entries = entries
                  )
                }
            }.toList
      } yield expenses

    override def accounts(): F[List[String]] =
      Logger[F].info("accounts: listing accountnames") *>
        hledgerAPI.accountNames().map(_.distinct.sorted)
  }
