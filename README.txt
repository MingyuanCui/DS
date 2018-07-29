1. The usage of client:
usage: java -jar ActivityStreamerClient.jar [-rh <arg>] [-rp <arg>] [-s <arg>] [-u <arg>]

 -rh <arg>   remote hostname, default localhost
 -rp <arg>   remote port number, default 4500
 -s <arg>    secret for username, default test10 when register
 -u <arg>    username, default anonymous
If -u is not anonymous and a secret is not provided, this situation will be regarded as registration.

2. The usage of server:
usage: java -jar ActivityStreamerServer.jar [-lp <arg>] [-rh <arg>] [-rp <arg>] [-s <arg>]

 -lp <arg>   local port to listen to, default 4500
 -rh <arg>   remote hostname,default localhost
 -rp <arg>   remote port number
 -s <arg>    secret for username
 
