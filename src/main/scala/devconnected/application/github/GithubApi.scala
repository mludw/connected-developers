package devconnected.application.github

import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle

trait GithubApi[F[_]]:

  /** If the user exists - will provide all github organisations that the user belongs to. Returns None if the user with
    * provided handle does not exist in github.
    */
  def getOrganisations(userHandle: UserHandle): F[Option[List[GithubOrganisation]]]
