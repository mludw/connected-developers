package devconnected.github

opaque type GithubToken = String

object GithubToken:
  def apply(s: String): GithubToken = s
