param(
    [Parameter(Mandatory=$True)]
    [String]
    $Repository
)
docker build -t $Repository/tachidesk-server:latest .
docker push $Repository/tachidesk-server:latest