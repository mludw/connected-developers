## Connected Developers

### About

The application provided REST API for finding connected developers.
2 developers are considered to be connected if:
 - they follow each other on tweeter
 - they have at least one Github organization in common

### Design

The application is designed using of hexagonal architecture.
The 'devconnected.application' package defines the domain,
other 'devconnected.*' packages implement adapters.

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
