package it.unibo.sd1920.akka_raft.client

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import it.unibo.sd1920.akka_raft.protocol.GuiControlMessage.{GuiMsgLossServer, GuiSendMessage}
import it.unibo.sd1920.akka_raft.server.ServerActor
import it.unibo.sd1920.akka_raft.utils.{CommandType, NetworkConstants}
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Random

//Un test visivo che automatizza l'invio di alcuni messaggi(con e senza perdita), per osservare se il comportamento del cluster è corretto.
//
class LongRunningTest
  extends TestKit(ActorSystem("ClientGuiActorTest"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  var clientGuiActor: ActorRef = _
  var serverActor0: ActorRef = _
  var serverActor1: ActorRef = _
  var serverActor2: ActorRef = _
  var serverActor3: ActorRef = _
  var serverActor4: ActorRef = _
  var serversName: List[String] = _

  var clientSystem0: ActorSystem = _
  var serverSystem0: ActorSystem = _
  var serverSystem1: ActorSystem = _
  var serverSystem2: ActorSystem = _
  var serverSystem3: ActorSystem = _
  var serverSystem4: ActorSystem = _

  //Spengo il sistema
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
    clientSystem0.terminate()
    serverSystem0.terminate()
    serverSystem1.terminate()
    serverSystem2.terminate()
    serverSystem3.terminate()
    serverSystem4.terminate()
    shutdown(clientSystem0)
    shutdown(serverSystem0)
    shutdown(serverSystem1)
    shutdown(serverSystem2)
    shutdown(serverSystem3)
    shutdown(serverSystem4)
    Thread.sleep(5000)
  }

  private def getConfig(config: String): com.typesafe.config.Config = {
    var port = config
    if (port.isEmpty) port = "0"
    ConfigFactory.parseString(s"""akka.remote.artery.canonical.port=$port""")
      .withFallback(ConfigFactory.load("server"))
  }

  private def getRandomServer: String = {
    serversName(Random.nextInt(serversName.size))
  }

  //Inizializzo il cluster
  override def beforeAll(): Unit = {
    serversName = List("S0", "S1", "S2", "S3", "S4")
    clientSystem0 = ActorSystem(NetworkConstants.clusterName, ConfigFactory.parseString("""akka.remote.artery.canonical.port=5000""")
      .withFallback(ConfigFactory.load("client")))
    clientGuiActor = clientSystem0 actorOf(ClientActor.props, "C0")
    serverSystem0 = ActorSystem(NetworkConstants.clusterName, getConfig(NetworkConstants.secondSeedPort.toString))
    serverActor0 = serverSystem0 actorOf(ServerActor.props, "S0")


    serverSystem1 = ActorSystem(NetworkConstants.clusterName, getConfig(""))
    serverActor1 = serverSystem1 actorOf(ServerActor.props, "S1")
    serverSystem2 = ActorSystem(NetworkConstants.clusterName, getConfig(""))
    serverActor2 = serverSystem2 actorOf(ServerActor.props, "S2")
    serverSystem3 = ActorSystem(NetworkConstants.clusterName, getConfig(""))
    serverActor3 = serverSystem3 actorOf(ServerActor.props, "S3")
    serverSystem4 = ActorSystem(NetworkConstants.clusterName, getConfig(""))
    serverActor4 = serverSystem4 actorOf(ServerActor.props, "S4")
  }

  "Testing Cluster long running" must {
    "" ignore {
      //Attendo che tutto sia online
      Thread.sleep(20000)
      //Sottometto al cluster una serie di richieste, senza perdità di messaggi

      Stream.range(0, 15).foreach(_ => {
        clientGuiActor.tell(GuiSendMessage(getRandomServer, CommandType(Random.nextInt(3)), Random.nextInt(100).toString, Random.nextInt(100).toString), clientGuiActor)
        Thread.sleep(500)
      })
      //Imposto la perdità di messaggi per ogni server al 25%
      clientGuiActor.tell(GuiMsgLossServer("S0", 0.75), clientGuiActor)
      clientGuiActor.tell(GuiMsgLossServer("S1", 0.75), clientGuiActor)
      clientGuiActor.tell(GuiMsgLossServer("S2", 0.75), clientGuiActor)
      clientGuiActor.tell(GuiMsgLossServer("S3", 0.75), clientGuiActor)
      clientGuiActor.tell(GuiMsgLossServer("S4", 0.75), clientGuiActor)
      Thread.sleep(5000)

      //Sottometto al cluster una serie di richieste, con una perdità del 25%
      Stream.range(0, 25).foreach(_ => {
        clientGuiActor.tell(GuiSendMessage(getRandomServer, CommandType(Random.nextInt(3)), Random.nextInt(100).toString, Random.nextInt(100).toString), clientGuiActor)
        Thread.sleep(500)
      })

      Thread.sleep(5000)
    }
  }
}


