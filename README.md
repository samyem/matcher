
# Product to Listing Matcher



To run, clone this repo and with JDK8 set to JAVA_HOME environment variable, execute:
 `./gradlew matcherRun`
 
 By default, the results are written to `results.txt` file in the working directory.
 

## How it works


*   The products are lexicographically indexes (full text index)
*   Text from listings are matched against the index to narrow the best match
*   Keywords exctacted from the products are matched against the listing for second pass 


