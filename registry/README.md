# Build Instructions

Use either

`go build -C registry -o build/reg-server && registry/build/reg-server`

or

`cd registry && go run **/*.go`

Pass the `-h` flag for more info on the CLI interface

# API Endpoints

Rest-like API handling expecting JSON input and returning JSON output. Note that
any example messages in the documentation are pretty-printed, and will not
contain any whitespace unless otherwise noted

## Sessions

Most endpoints deal directory with a **session** JSON object, representing a
single ongoing voting session.

**Example Session:**

```json
{
    "id": "ABC123", // 6 character upper alphanumeric session ID
    "host": "127.0.0.1", // IP address of the session's leader node
    "port": 8080, // Port address the leader is broadcasting on
    "options": [
        // Voting options for the session
        "cat",
        "dog"
    ]
}
```

### View Single Session

`GET /sessions/{SESSION_ID}`

Returns the complete information about a single session

**Example Response:**

```json
{
    "id": "ABC123",
    "host": "127.0.0.1",
    "port": 8080,
    "options": ["cat", "dog"]
}
```

### View All Sessions

`GET /sessions`

Returns the complete information on every ongoing session

**Example Response:**

```json
[
    {
        "id": "ABC123",
        "host": "127.0.0.1",
        "port": 8080,
        "options": ["cat", "dog"]
    },
    {
        "id": "ABC124",
        "host": "127.0.0.2",
        "port": 8081,
        "options": ["cat", "horse"]
    }
]
```

### View All Sessions (Truncated)

`GET /sessions/all`

Returns the **session IDs** of every ongoing session.

**Example Response:**

```json
{
    "sessions": ["ABC123", "ABC124"]
}
```

### Create New Session

`POST /sessions`

Submits the information for a new session, note that the session ID is assigned
server side. An ID will simply be ignored if present in the request. On a
successful submission, the response will contain only a JSON string containing
the assigned session ID

**Example Request:**

```json
{
    "host": "127.0.0.1",
    "port": 8080,
    "options": ["cat", "dog"]
}
```

**Example Response:**

```json
"ABC123"
```

### Update Ongoing Session

`GET /sessions/{SESSION_ID}`

Updates the leader information for a session. Only the host IP and port number
may be updated for a session. An update may contain either one of both of these
attributes

**Example Request:**

```json
{
    "host": "127.0.0.1",
}
```
