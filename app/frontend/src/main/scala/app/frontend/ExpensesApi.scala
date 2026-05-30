package app.frontend

import app.frontend.ExpensesApi.{DecodeExpensesError, ErrorOr, RequestFailed}
import app.shared.dtos.{MonthlyExpense, NewTransaction}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

import io.circe.parser.decode
import io.circe.syntax.*
import org.scalajs.dom

object ExpensesApi:
  type ErrorOr[T] = Either[ApiError, T]

  sealed abstract class ApiError(msg: String) {
    override def toString: String = msg
  }

  final case class DecodeExpensesError(body: String, err: Throwable)
      extends ApiError(s"decode failed: $err / body=$body")

  final case class RequestFailed(status: Int, body: String)
      extends ApiError(s"request failed: HTTP $status / body=$body")

case class ExpensesApi()(using ec: ExecutionContext):

  def fetchExpenses(): Future[ErrorOr[List[MonthlyExpense]]] =
    expenses("/api/expenses/monthly")

  def fetchExpensesBy(month: String): Future[ErrorOr[List[MonthlyExpense]]] =
    expenses(s"/api/expenses/monthly?month=$month")

  /** Probe whether the backend can reach hledger-web. Returns false on a
    * non-2xx response or any network error (e.g. the backend itself is down).
    */
  def checkHealth(): Future[Boolean] =
    dom.fetch("/api/health").map(_.ok).recover { case _ => false }

  def fetchAccounts(): Future[ErrorOr[List[String]]] =
    for {
      response <- dom.fetch("/api/accounts")
      body     <- response.text()
    } yield decode[List[String]](body).left.map(err =>
      DecodeExpensesError(body, err.fillInStackTrace())
    )

  /** POST the drafted transactions to the backend, which forwards each to
    * hledger-web. Succeeds only on a 2xx response.
    */
  def addTransactions(transactions: List[NewTransaction]): Future[ErrorOr[Unit]] = {
    val init = new dom.RequestInit {}
    init.method = dom.HttpMethod.POST
    init.headers = js.Dictionary("Content-Type" -> "application/json")
    init.body = transactions.asJson.noSpaces

    for {
      response <- dom.fetch("/api/transactions", init)
      body     <- response.text()
    } yield
      if (response.ok) Right(())
      else Left(RequestFailed(response.status, body))
  }

  private def expenses(url: String): Future[ErrorOr[List[MonthlyExpense]]] =
    for {
      response <- dom.fetch(url)
      body     <- response.text()
      errOrExpenses =
        decode[List[MonthlyExpense]](body)
          .left
          .map(err => DecodeExpensesError(body, err.fillInStackTrace()))
    } yield errOrExpenses
