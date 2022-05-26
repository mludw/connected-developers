package devconnected.application.connection

trait ConnectionChecker {
  def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck
}

object ConnectionChecker extends ConnectionChecker {
  override def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck =
    NotConnected
}
