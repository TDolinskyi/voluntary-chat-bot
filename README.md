# telegram-chat-bot

# Build

in project root directory:

```
sbt docker:publishLocal
```

stop existing docker-compos:

```
docker-ompose stop
```

start and rebuild new docker images:

```
docker-compose up --build -d
```