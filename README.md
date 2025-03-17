# cpsc-559-distributed-voting-system

A distributed voting system developed in Java.

# Build Instructions

## Peer Nodes

`./gradlew run --console=plain`

See `./gradlew help` for more info

## Registry Server

Use either

`go build -C registry -o build/reg-server && registry/build/reg-server`

or

`cd registry && go run **/*.go`

Pass the `-h` flag for more info on the CLI interface
