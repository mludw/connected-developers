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

object Main extends IOApp {

  override def run(args: List[String]) = for {
    config <- Config.load
    api <- EmberClientBuilder.default[IO].build.use { case httpClient =>
      val githubClient  = new GithubClient(httpClient, config.githubToken)
      val twitterClient = new TwitterClient(httpClient)
      val connections   = DeveloperConnections(githubClient, twitterClient, ConnectionCheck)
      IO.delay(DeveloperConnectionsApi(connections))
    }
    res <- bind(api)
  } yield res

  def bind(api: DeveloperConnectionsApi) =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(api.routes.orNotFound)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
