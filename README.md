## Connected Developers

### About

The application provided REST API for finding connected developers.
2 developers are considered to be connected if:
 - they follow each other on tweeter
 - they have at least one Github organization in common

### Apporach

The application is designed using of hexagonal architecture.
The 'devconnected.application' package defines the domain,
other 'devconnected.*' packages implement adapters.

The excercise has been done with Scala3.
If some language features used look controversial - it can be caused by lack of experience with the language update :).

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
