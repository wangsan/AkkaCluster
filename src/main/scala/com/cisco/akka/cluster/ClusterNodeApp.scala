package com.cisco.akka.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.routing._
import akka.contrib.pattern._
import akka.routing._
import akka.cluster.ClusterEvent._
import akka.cluster._

object ClusterNodeApp extends App {
  args.length match {
    case 2 => start(args(0), args(1))
    case _ => println(s"Will not start node as port and node not provided...")
  }

  /**
   * starts a node on a cluster
   */
  def start(port: String, role: String) = {
    println(s"Bringing Node up on port: $port with role: $role")
    setSystemProperty(port, role)
    val system = ActorSystem("ClusterSystem")
    val router = system.actorOf(Props[ClusterWorker].withRouter(RoundRobinRouter(nrOfInstances = 4)), "workers")
    val clubbedWorkers = system.actorOf(Props(new ClubbedClusterRouter(router)), "clubbedWorkers")
    Cluster(system)
    //ClusterReceptionistExtension(system).registerService(clubbedWorkers)
  }

  /**
   * set system property of port and role of a node
   */
  def setSystemProperty(port: String, role: String) = {
    System.setProperty("akka.remote.netty.tcp.port", port)
    System.setProperty("akka.cluster.roles", role)
  }
}

/**
 * A worker that does not know it works for which company
 */
class ClusterWorker extends Actor {
  def receive = {
    case ClusterMessage(company) => println("The company is: " + company)
  }
}

/**
 *
 */
class ClubbedClusterRouter(clusterWorkerRouter: ActorRef) extends Actor {
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, MemberUp
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  // re-subscribe when restart
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case msg: ClusterMessage => clusterWorkerRouter ! msg
    case state: CurrentClusterState => state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) => register(m)
  }

  def register(member: Member) = if (member.hasRole("frontend")) context.actorSelection(RootActorPath(member.address) / "user" / "frontend") ! BackendRegistration
}

class FrontendClusterRouter extends Actor {
  val cluster = Cluster(context.system)
  // subscribe to cluster changes, MemberUp
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  // re-subscribe when restart
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case msg =>
  }
}

case object BackendRegistration
case class ClusterMessage(company: String)