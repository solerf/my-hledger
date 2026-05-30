package app.backend.hledger

import app.backend.hledger.model.Transaction

import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

sealed trait HledgerApi[F[_]]:
  def transactions(): F[List[Transaction]]
  def accountNames(): F[List[String]]
  def addTransaction(tx: add.Transaction): F[Unit]

  /** True when hledger-web answers, false on any failure (down, refused, …). */
  def reachable(): F[Boolean]

object HledgerApi:

  def apply[F[_]: Concurrent](base: Uri, client: Client[F]): HledgerApi[F] =
    new HledgerWebClientImpl[F](base, client)

  private case class HledgerWebClientImpl[F[_]: Concurrent](base: Uri, client: Client[F])
      extends HledgerApi[F] {

    def transactions(): F[List[Transaction]] =
      client.expect[List[Transaction]](base / "transactions")

    def accountNames(): F[List[String]] =
      client.expect[List[String]](base / "accountnames")

    def addTransaction(tx: add.Transaction): F[Unit] =
      for {
        status <- client.status(Request[F](Method.PUT, base / "add").withEntity(tx))
        _      <-
          Concurrent[F]
            .raiseWhen(!status.isSuccess)(
              new RuntimeException(s"failed hledger-web add: $status")
            )
      } yield ()

    def reachable(): F[Boolean] =
      client.successful(Request[F](Method.GET, base / "accountnames")).handleError(_ => false)
  }
