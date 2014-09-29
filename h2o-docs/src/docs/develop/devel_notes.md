# Development Notes 

## Coding convention
  * Every assertion in code should have error message
 
## Data entities

### Key

### Frame
The are two kinds of Frame with respect to visibility:
  * private Frame - has null Key
  * shared Frame - has Key and it is stored in DKV

 
## Locking

General rules:
  * every shared frame (i.e., frame which is accesible via DKV) should be locked by R or W lock
  * each lock should be identified by a locker - it should be a job key or locker identifier (TBD)
  * lock should be used only by high-level user API - i.e., jobs, UI controllers
  
