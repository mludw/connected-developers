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

### Possible improvements/optimisations that could be implemented:

 - add logging (at least to errors)
 - github/twitter url should be defined in the configuration (so I can e2e the app or start it locally with mocks)
 - last github groups API call could be avoided if amount of returned elements is less than requested
 - http client retries
 - e2e test starting application REST API, mocking external APIs (github and twitter)

### Usage

To run the app execute 
```
sbt run
```
### Testing

To test the app execute 
```
sbt test
```
