

object SshDsl {
  import fr.janalyse.ssh._
  import com.jcraft.jsch.ProxyHTTP

  sealed trait EndPoint {
    val host:String
    val port:Int
  }

  case class ProxyEndPoint(host:String, port:Int=ProxyEndPoint.defaultPort) extends EndPoint
  object ProxyEndPoint {
    val defaultPort=3128
  }
  implicit class ProxyEndPointHelper(val sc:StringContext) extends AnyVal {
    def proxy(args: Any*):ProxyEndPoint = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      var buf = new StringBuffer(strings.next)
      while(strings.hasNext) {
        buf append expressions.next
        buf append strings.next
      }
      val EpRE="""([^~]+)(?:~(\d+))?""".r
      buf.toString match {
        case EpRE(host,port) =>  // TODO : not safe
          ProxyEndPoint(
            host=host,
            port = Option(port).map(_.toInt).getOrElse(ProxyEndPoint.defaultPort)
          )
      }
    }
  }

  case class SshEndPoint(host:String, username:String=SshEndPoint.defaultUserName, port:Int=SshEndPoint.defaultPort) extends EndPoint
  object SshEndPoint {
    val defaultUserName=scala.util.Properties.userName
    val defaultPort=22
  }
  implicit class SshEndPointHelper(val sc:StringContext) extends AnyVal {
    def ssh(args: Any*):SshEndPoint = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      var buf = new StringBuffer(strings.next)
      while(strings.hasNext) {
        buf append expressions.next
        buf append strings.next
      }
      val EpRE="""([a-zA-Z_]@)?([^~]+)(?:~(\d+))?""".r
      buf.toString match {
        case EpRE(user,host,port) =>  // TODO : not safe
          SshEndPoint(
            host=host,
            username=Option(user).getOrElse(SshEndPoint.defaultUserName),
            port = Option(port).map(_.toInt).getOrElse(SshEndPoint.defaultPort)
          )
      }
    }
  }



  implicit class EndPointToAccessPath(from:EndPoint) {
    def <~>(to:EndPoint):AccessPath = AccessPath(from::to::Nil)
  }


  case class AccessPath(endpoints:List[EndPoint]) {
    def <~>(to:EndPoint):AccessPath = AccessPath(endpoints:+to)
  }

  class ServerContext(name:String) {
    var path = AccessPath(SshEndPoint("127.0.0.1")::Nil)
    def path_(newPath:AccessPath):Unit = {path=newPath}
    def pshell[T] (proc : SSHShell => T):T = {
      def intricate(endpoints:Iterable[EndPoint], lport:Option[Int]=None, through:Option[ProxyEndPoint]=None):T = {
        endpoints.headOption match {
          case Some(endpoint:ProxyEndPoint) =>
            intricate(endpoints.tail, lport, Some(endpoint))
          case Some(endpoint:SshEndPoint) if lport.isDefined => // intricate tunnel
            val proxy = through.map(p => new ProxyHTTP(p.host, p.port))
            val opts = SSHOptions("127.0.0.1", username=endpoint.username, port=lport.get, proxy=proxy)
            SSH.once(opts) { ssh =>
              val newPort = ssh.remote2Local(endpoint.host,endpoint.port)
              intricate(endpoints.tail, Some(newPort))
           }
          case Some(endpoint:SshEndPoint)  => // first tunnel
            val proxy = through.map(p => new ProxyHTTP(p.host, p.port))
            val opts=SSHOptions(endpoint.host, username=endpoint.username, port=endpoint.port, proxy=proxy)
            SSH.once(opts) { ssh =>
              val newPort = ssh.remote2Local("127.0.0.1",22)
              intricate(endpoints.tail, Some(newPort))
           }
          case None if lport.isDefined => SSH.shell("localhost",port=lport.get) { proc }
          case None =>
            throw new RuntimeException("Empty ssh path")
        }
      }
      intricate(path.endpoints)
    }
    def shell[T] (withSh : implicit SSHShell => T):T = {
      pshell { sh =>
        implicit val ish=sh
        withSh
      }
    }
  }
  var servers = Map.empty[String,ServerContext]

  def server(name:String)(specify: implicit ServerContext => Unit) = {
    implicit val context = new ServerContext(name)
    servers+=name->context
    specify
  }
  def access(path:AccessPath)(implicit context:ServerContext) = {
    context.path=path
  }
  def shell[T](serverName:String)(withSh : implicit SSHShell => T):T = {
    servers(serverName).pshell { sh =>
      implicit val ish = sh
      withSh
    }
  }

  def ls(implicit sh:SSHShell) = {sh.ls()}
  def cd(dir:String)(implicit sh:SSHShell) = {sh.cd(dir)}
  def pwd(implicit sh:SSHShell) = {sh.pwd()}
  def mkdir(name:String)(implicit sh:SSHShell) = {sh.mkdir(name)}
  def exec(cmd:String)(implicit sh:SSHShell) = {sh.execute(cmd)}
}



object test {
  def main(args: Array[String]): Unit = {
    import SshDsl._
    server("srv1") {
      access(proxy"127.0.0.1~3128" <~> ssh"127.0.0.1~22" <~> ssh"127.0.0.2~22" <~> ssh"127.0.0.3~22")
      //service("web1") on 80
    }

    shell("srv1") {
      ls.take(10).map{println}
      println(exec("netstat -anp | grep LISTEN | grep 127.0.0"))
    }
/*
    http("srv1" / "web1") {
      get("/server-status?auto")
    }
*/
  }
}
