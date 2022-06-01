package devconnected.config

import devconnected.twitter.TwitterToken
import cats.effect.IO
import cats.syntax.flatMap
import scala.concurrent.duration._

final case class Config(twitterToken: TwitterToken, cache: CacheConfig)

final case class CacheConfig(maxSize: Long, ttl: Option[Duration])

object Config {
  private val CacheMaxSize = 1000L // this should be coming from properties / app.conf
  private val CacheTTL     = Some(1.hour)

  val load: IO[Config] =
    getSysPropOrEnvVar("TWITTER_TOKEN")
      .map(t => Config(TwitterToken(t), cache = CacheConfig(CacheMaxSize, CacheTTL)))

  private def getSysPropOrEnvVar(name: String): IO[String] = IO
    .delay(sys.props.get(name))
    .flatMap(_.fold(IO.delay(sys.env(name)))(IO.delay))
    .handleErrorWith(_ => IO.raiseError(new Exception(s"Missing environment variable / property [$name]")))
}
