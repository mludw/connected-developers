package devconnected.config

import devconnected.twitter.TwitterToken
import cats.effect.IO
import cats.syntax.flatMap

final case class Config(twitterToken: TwitterToken)

object Config {
  val load: IO[Config] = getSysPropOrEnvVar("TWITTER_TOKEN").map(t => Config(TwitterToken(t)))

  private def getSysPropOrEnvVar(name: String): IO[String] = IO
    .delay(sys.props.get(name))
    .flatMap(_.fold(IO.delay(sys.env(name)))(IO.delay))
    .handleErrorWith(_ => IO.raiseError(new Exception(s"Missing environment variable / property [$name]")))
}
