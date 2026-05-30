package app.backend.service

import app.backend.hledger.model.Transaction
import app.backend.hledger.{HledgerApi, add as addPayload}
import app.shared.dtos.{ExpenseEntry, MonthlyExpense, NewTransaction}

import cats.Monad
import cats.syntax.all.*
import org.typelevel.log4cats.{Logger, LoggerFactory}

sealed trait ExpensesService[F[_]]:
  def monthly(month: Option[String]): F[List[MonthlyExpense]]
  def accounts(): F[List[String]]
  def add(transactions: List[NewTransaction]): F[Unit]
  def hledgerReachable(): F[Boolean]

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
                        comment =
                          Option(p.pcomment.trim()).filter(_.nonEmpty).getOrElse(t.tcomment.trim())
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

    override def add(transactions: List[NewTransaction]): F[Unit] = {
      // Entries are grouped into one transaction per date (see
      // add.fromNewTransactions), so the journal gets one multi-posting entry
      // per day rather than one transaction per drafted row.
      val grouped = addPayload.fromNewTransactions(transactions)
      Logger[F].info(
        s"add: posting ${grouped.size} transaction(s) from ${transactions.size} entries"
      ) *> grouped.traverse_(hledgerAPI.addTransaction)
    }

    override def hledgerReachable(): F[Boolean] =
      hledgerAPI.reachable().flatTap(ok => Logger[F].info(s"hledgerReachable: $ok"))
  }
