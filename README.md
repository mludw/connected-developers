## Connected Developers

### About

The application provided REST API for finding connected developers.
2 developers are considered to be connected if:
 - they follow each other on tweeter
 - they have at least one Github organization in common

### Apporach

#### Architecrture

The application is designed using of hexagonal architecture.
The 'devconnected.application' package defines the domain,
other 'devconnected.*' packages implement adapters.

#### Scala 3

The excercise has been done with Scala3.
If some language features used look controversial - it can be caused by lack of experience with the language update :).

#### Twitter

Twitter get-following-users call is building list of all users and lets the application domain to find matching users.
Advantage of such approach is we could cache the followed users and the twitter port is something generic.
If we want to make the app more memory effecrtive (if people follow really huge amounts of users), 
we could allow twitter layer to check if user follows another user - iterate through users stream and do not build the list in the memory.

### Development 

#### Possible improvements/optimisations that could be implemented:

 - add logging (at least to errors)
 - github/twitter url should be defined in the configuration (so I can e2e the app or start it locally with mocks)
 - last github groups API call could be avoided if amount of returned elements is less than requested
 - http client retries
 - there are no tests for github/twitter cache wrappers, the tests are important but I did not want to use more time - I just tested it manually
 - e2e test starting application REST API, mocking external APIs (github and twitter)

#### Testing

To test the app execute 
```
sbt test
```

### Usage

The application requires providing twitter application token:
https://developer.twitter.com/en/docs/authentication/oauth-2-0/bearer-tokens.

To run the app you need to:
- either set `TWITTER_TOKEN` environment variable
- or provide `TWITTER_TOKEN` system property when starting the app.
and then execute the `sbt run` command (providing the token in case when property is used).

#### Example:

When having twitter token `THE_TWITTER_TOKEN` go to the app directory in the terminal and

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
