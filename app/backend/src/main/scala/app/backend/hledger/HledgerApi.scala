package app.backend.hledger

import app.backend.hledger.model.Transaction
import cats.effect.Concurrent
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.Client

sealed trait HledgerApi[F[_]]:
  def transactions(): F[List[Transaction]]
  def accountNames(): F[List[String]]


object HledgerApi:

  def apply[F[_]: Concurrent](base: Uri, client: Client[F]): HledgerApi[F] =
    new HledgerWebClientImpl[F](base, client)

  private case class HledgerWebClientImpl[F[_]: Concurrent](base: Uri, client: Client[F])
      extends HledgerApi[F] {

    def transactions(): F[List[Transaction]] =
      client.expect[List[Transaction]](base / "transactions")

    def accountNames(): F[List[String]] =
      client.expect[List[String]](base / "accountnames")
  }
