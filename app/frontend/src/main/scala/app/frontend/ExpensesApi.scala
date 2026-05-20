package app.frontend

import app.frontend.ExpensesApi.{DecodeExpensesError, ErrorOr}
import app.shared.dtos.MonthlyExpense
import io.circe
import io.circe.parser.decode
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.Thenable.Implicits.*

object ExpensesApi:
  type ErrorOr[T] = Either[ApiError, T]

  sealed abstract class ApiError(msg: String) {
    override def toString: String = msg
  }

  final case class DecodeExpensesError(body: String, err: Throwable)
      extends ApiError(s"decode failed: $err / body=$body")

case class ExpensesApi()(using ec: ExecutionContext):

  def fetchExpenses(): Future[ErrorOr[List[MonthlyExpense]]] =
    expenses("/api/expenses/monthly")

  def fetchExpensesBy(month: String): Future[ErrorOr[List[MonthlyExpense]]] =
    expenses(s"/api/expenses/monthly?month=$month")

  private def expenses(url: String)(using ec: ExecutionContext) =
    for {
      response <- dom.fetch(url)
      body     <- response.text()
      errOrExpenses =
        decode[List[MonthlyExpense]](body)
          .left
          .map(err => DecodeExpensesError(body, err.fillInStackTrace()))
    } yield errOrExpenses
