**Architecture and Interaction Diagrams** 
<br/>
* The Architecture Diagram gives you an overview at a High level on what's going on in the project. 
* The Interaction diagram gives you a little deeper insight into how each Peer, Super Node handles each operation.

**DOCKER COMPOSE EXECUTION**

* cd into downloaded repository.
* run "docker-compose -f docker-compose-project3.yml up --build -d"
* exec into each docker container in a seperate terminal 
  > "docker exec -it supernode2 bash" <br/>
  > "docker exec -it peer1 bash" <br/>
  > "docker exec -it client1 bash"
                                 
* run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.SuperNode < ID > " in supernode< ID > container
* run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.Peer < ID >" in peer< ID > container.
* run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.Client " in client containers
* Then try and play with the pub-sub system by giving appropriate answers to the CLI questions.


**DOCKER EXECUTION**
* cd into downloaded repository.
* run "docker build -t project3:rishabkatta -f Dockerfile-project3 ."   
* run "docker run --hostname supernode2 --ip="172.28.0.2" -it project3:rishabkatta /bin/bash"
* run "docker run --hostname supernode4 --ip="172.28.0.3" -it project3:rishabkatta /bin/bash"
* run "docker run --hostname peer1 -it project3:rishabkatta /bin/bash" in another terminal.
* run "docker run --hostname client1 -it project3:rishabkatta /bin/bash" in another.
* In supernode< ID > container, run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.SuperNode < ID >"
* In peer< ID > container run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.Peer < ID >"
* In client container run "java -cp target/project3-1.0-SNAPSHOT.jar edu.rit.cs.Client"

