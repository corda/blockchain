# Corda Transaction Builder Stack

This run just the corda-local-network & corda-transaction-builder services 
with UI enabled. This is NOT a full workbench integration stack, but it does 
enable access to the to base Corda integration services through a native UI 

## Running 

```bash
# to start
docker-compose pull & docker-compose up -d
docker ps 

# to stop
docker-compose down
```

## UI 

Assuming Docker host is your localhost (check how your Docker is configured),
then the UIs are at:
* [http://localhost:1115](corda-local-network)
* [http://localhost:1116](corda-transaction-builder)

## Using the API 

Assuming  Docker host is your localhost 

```bash
curl -X POST -H "Content-Type: application/json" http://localhost:1114/demo/nodes/create \
 --data '["Alice","Bob","Charlie"]'
```

You can list the nodes with:

```bash
curl http://localhost:1114/demo/nodes
```

Or get more info on a particular node with 

```bash
curl http://localhost:1114/demo/nodes/Alice/config
curl http://localhost:1114/demo/nodes/Alice/status
```

And deploy an app with 

```bash
curl -X POST  http://localhost:1114/demo/apps/<deployed-app-name>/deploy \
 --data-binary  @<path-to-some-app.jar> 
```

And finally, start with 

```bash
curl -X POST  http://corda-local-network:1114/demo/start 
```


## Problems 

### Downloading deps 
The corda-local-network service will download (and then cache) Corda 
dependencies dynamically. These are quite large & there may a delay. 

### Ports in use 
As the containers expose a large number of ports, there is a chance when 
running on a dev machine or laptop that another process has already taken one 
or more of these port, in which case Docker will complain. There isn't much 
you can do other than find a clean host or close down the conflicting processes. 
The unix commands below will help identify them.

```bash
sudo lsof -i -P -n | grep LISTEN 
sudo netstat -tulpn | grep LISTEN
sudo nmap -sTU -O IP-address-Here
lsof -i :<portnumber>
```

## Logspout 

An easy way of tracking docker logs from multiple containers. See also
https://github.com/gliderlabs/logspout

```bash
# to start 
docker run -d --name="logspout" \
	--volume=/var/run/docker.sock:/var/run/docker.sock \
	--publish=127.0.0.1:8000:80 \
	gliderlabs/logspout
	
# monitor logs
curl http://127.0.0.1:8000/logs
```
