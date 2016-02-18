# requestinator

A Clojure library designed to generate and execute requests against a
web service based on a [Swagger](http://swagger.io) specification.

## Usage

### Via Leinginen

Generate three indepdendent, random sequences of requests based on the
[Petstore Sample Service](http://petstore.swagger.io/), and save it to
`/tmp/YYYY-MM-DD-HH-mm-ss`. Requests will be scheduled on average
twice a second for each of the three agents, and 60 seconds worth of
data will be generated.

```
lein run generate --spec-uri http://petstore.swagger.io/v2/swagger.json --destination file:///tmp/requestinator-test --agent-count 3 --interarrival-sec 0.5 --duration-sec 60
```

### Via Docker

```
git commit -am "Message for commit"
git tag v1.2.3
bin/build

```

## License

Copyright © 2016 Cognitect, Inc.

