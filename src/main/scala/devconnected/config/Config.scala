package devconnected.config

import devconnected.github.GithubToken
import cats.effect.IO

final case class Config(githubToken: GithubToken)

object Config {
  val load: IO[Config] = getEnvVar("GITHUB_TOKEN").map(t => Config(GithubToken(t)))

  private def getEnvVar(name: String) = IO
    .delay(sys.env(name))
    .handleErrorWith(_ => IO.raiseError(new Exception(s"Missing environment variable [$name]")))
}
