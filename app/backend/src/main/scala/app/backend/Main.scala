package app.backend

import app.backend.hledger.HledgerApi
import app.backend.service.ExpensesService
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.{HttpApp, Uri}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.File

object Main extends IOApp:

  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  // Dev wiring: serve straight out of an on-disk directory. In Docker we set APP_ASSETS_DIR to /opt/app/assets.
  private val DefaultAssetsDir = "frontend/src/main/resources"

  def run(args: List[String]): IO[ExitCode] =
    for {
      assetsDir     <- getAssetsDir
      hledgerWebUrl <- getHledgerWebUrl

      logger = LoggerFactory[IO].getLogger
      _ <- logger.info(
        s"starting app: bind=0.0.0.0:8081 assets=${assetsDir.getAbsolutePath} hledger-web=$hledgerWebUrl"
      )

      _ <-
        EmberClientBuilder.default[IO]
          .build
          .use { client =>
            val hlAPI = HledgerApi(hledgerWebUrl, client)
            val svc   = ExpensesService(hlAPI)

            buildServer(Routes.buildHttpApp(svc, assetsDir))
              .use(_ => logger.info("server ready on http://0.0.0.0:8081") *> IO.never)
          }

    } yield ExitCode.Success

  private def buildServer(httpApp: HttpApp[IO]): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8081")
      .withHttpApp(httpApp)
      .build

  private def getAssetsDir: IO[File] =
    for {
      assetsDirEnv <- IO(sys.env.getOrElse("APP_ASSETS_DIR", DefaultAssetsDir))
      assetsFile   <- IO.blocking(new File(assetsDirEnv))
    } yield assetsFile

  private def getHledgerWebUrl =
    for {
      urlEnv <- IO(sys.env.getOrElse("HLEDGER_WEB_URL", "http://localhost:5000"))
      url    <- IO.fromEither(Uri.fromString(urlEnv))
    } yield url
