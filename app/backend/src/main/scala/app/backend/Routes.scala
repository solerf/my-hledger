package app.backend

import app.backend.service.ExpensesService
import app.shared.dtos.*
import cats.Monad
import cats.effect.Async
import cats.syntax.all.*
import cats.syntax.semigroupk.*
import fs2.io.file.Files
import fs2.io.file.Path.fromNioPath
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger as RequestLogger
import org.http4s.{HttpApp, HttpRoutes, Request, Response, StaticFile, Status}
import org.typelevel.log4cats.{Logger, LoggerFactory}

import java.io.File

object Routes:

  def buildHttpApp[F[_]: {Async, Files, LoggerFactory}](
    expensesService: ExpensesService[F],
    assetsDir: File
  ): HttpApp[F] = {
    given Logger[F] = LoggerFactory[F].getLogger

    val api    = Router("/api" -> apiRoutes(expensesService))
    val static = staticRoutes(assetsDir)

    val httpApp = (api <+> static).orNotFound

    // Log every request/response. logBody=false avoids dumping JSON bodies.
    RequestLogger.httpApp(
      logHeaders = true,
      logBody = false,
      logAction = Some((msg: String) => Logger[F].info(msg))
    )(httpApp)
  }

  private def staticRoutes[F[_]: {Async, Files}](assetsDir: File): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ GET -> Root => serveFile(assetsDir, "index.html", req)
      case req @ GET -> path =>
        serveFile(assetsDir, path.segments.map(_.encoded).mkString("/"), req)
    }

  private def serveFile[F[_]: {Async, Files}](
    assetsDir: File,
    relPath: String,
    req: Request[F]
  ): F[Response[F]] =
    StaticFile
      .fromPath[F](fromNioPath(new File(assetsDir, relPath).toPath), Some(req))
      .getOrElse(Response.notFound[F])

  private object MonthParam extends OptionalQueryParamDecoderMatcher[String]("month")

  private def apiRoutes[F[_]: {Monad, Logger}](
    expensesService: ExpensesService[F]
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "expenses" / "monthly" :? MonthParam(month) =>
        for {
          _        <- Logger[F].info(s"GET /api/expenses/monthly month=${month.getOrElse("-")}")
          expenses <- expensesService.monthly(month)
        } yield Response[F](Status.Ok).withEntity(expenses.asJson)

      case GET -> Root / "accounts" =>
        for {
          _        <- Logger[F].info("GET /api/accounts")
          accounts <- expensesService.accounts()
        } yield Response[F](Status.Ok).withEntity(accounts.asJson)
    }
