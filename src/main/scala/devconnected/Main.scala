package devconnected

import cats.effect.{IO, IOApp}
import devconnected.application.DeveloperConnections
import devconnected.application.connection.ConnectionCheck
import org.http4s.ember.client._
import org.http4s.client._
import devconnected.github.GithubClient
import devconnected.twitter.TwitterClient
import devconnected.config.Config
import devconnected.api.DeveloperConnectionsApi
import org.http4s.ember.server.EmberServerBuilder
import scala.concurrent.ExecutionContext.global
import cats.effect.ExitCode
import com.typesafe.scalalogging.LazyLogging

object Main extends IOApp with LazyLogging {

  override def run(args: List[String]) = for {
    config <- Config.load
    api <- EmberClientBuilder.default[IO].build.use { case httpClient =>
      val githubClient  = new GithubClient(httpClient)
      val twitterClient = new TwitterClient(httpClient, config.twitterToken)
      val connections   = DeveloperConnections(githubClient, twitterClient, ConnectionCheck)
      IO.delay(DeveloperConnectionsApi(connections))
    }
    _   <- logInfo("--- STARTING THE APPLICATION ON PORT 8080 ---")
    res <- bind(api)
  } yield res

  private def bind(api: DeveloperConnectionsApi) =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(api.routes.orNotFound)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)

  private def logInfo(msg: String) = IO.delay(logger.info(msg))
}
