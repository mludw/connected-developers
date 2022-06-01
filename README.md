## Connected Developers

### About

The application provides REST API for finding connected developers.

2 developers are considered to be connected if:
 - they follow each other on twitter
 - they have at least one Github organisation in common

### Apporach

#### Architecrture

The application is designed using hexagonal architecture.
The `devconnected.application` package defines the domain,
other `devconnected.*` packages implement adapters (or config).

The github and twitter call results are cached in memory.

#### Scala 3

The excercise has been done with Scala3.
If use of some language features look controversial - it can be caused by lack of experience with the new language version :).

#### Twitter API approach

Twitter get-following-users call is building list of all users and lets the application domain to find matching users.
Advantage of such approach is we can cache the followed users and the twitter port is something generic.
If we want to make the app more memory efficient (if people follow really huge amounts of users), 
we could allow twitter layer to check if user follows another user - iterate through users stream and do not build the list in the memory.

### Development 

#### Possible improvements / optimisations that could be implemented:

 - improving application configuration
 - adding logging (at least to errors)
 - configuring `logback.xml` (it logs everything now, but it's fine)
 - defining github / twitter api urls in the configuration (so we can e2e the app or start it locally with mocks)
 - the last github API call fetching the groups could be avoided if amount of returned elements is less than requested
 - http client retrying on failure
 - improving APIs response handling
 - tests for github/twitter cache wrappers and config loading (are missing);
   rest of the code has been done with TDD, here I did not want to use more time - I just tested it manually
 - e2e test starting application REST API, mocking external APIs (github and twitter)

#### Testing

To test the app execute:
```
sbt test
```

### Usage

The application requires providing twitter application token:
https://developer.twitter.com/en/docs/authentication/oauth-2-0/bearer-tokens.

Surprisingly there is no need to provide github token.

To run the app you need to:
- either set `TWITTER_TOKEN` environment variable
- or provide `TWITTER_TOKEN` system property when starting the app.

The application is started by `sbt run` command (providing the token in case when property is used).

#### Examples:

When having twitter token = `THE_TWITTER_TOKEN` go to the app directory in the terminal and

either do:
```
export TWITTER_TOKEN="THE_TWITTER_TOKEN"
sbt run
```

or:
```
sbt run -DTWITTER_TOKEN=THE_TWITTER_TOKEN
```
.

### API

Service starts on port 8080.
It accepts `GET developers/connected/{user1}/{user2}` requests.

Example calls :
- `curl localhost:8080/developers/connected/rossabaker/tpolecat` (devs are connected)
- `curl localhost:8080/developers/connected/rossabaker/alexandru` (devs are not connected)
- `curl localhost:8080/developers/connected/mludw/rossabaker` (not valid twitter user)
- `curl localhost:8080/developers/connected/mludw122/mludw123` (not valid twitter/github users)
