# BroadCast Messages

This app will run a database query to identify a list of subcribers to message and send the message to a RabbitMQ queue for processing

To build run `clojure -X:alljar` or `clojure -A:uberjar BroadCast.jar -C -m erl.broadcast.core`

Example run command `java -Ddenom.band=100 -jar BroadCast.jar `