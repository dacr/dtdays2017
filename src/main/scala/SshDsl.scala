

object SshDsl {
  import fr.janalyse.ssh._
  import com.jcraft.jsch.ProxyHTTP


  implicit class ProxyEndPointStringInterpolator(val sc:StringContext) extends AnyVal {
    def proxy(args: Any*):ProxyEndPoint = {
      val EpRE="""([^~]+)(?:~(\d+))?""".r
      sc.s(args: _*) match {
        case EpRE(host,port) =>  // TODO : not safe
          ProxyEndPoint(
            host=host,
            port = Option(port).map(_.toInt).getOrElse(ProxyEndPoint.defaultPort)
          )
      }
    }
  }

  implicit class SshEndPointStringInterpolator(val sc:StringContext) extends AnyVal {
    def ssh(args: Any*):SshEndPoint = {
      val EpRE="""(?:([a-zA-Z0-9_]+)@)?([^~]+)(?:~(\d+))?""".r
      sc.s(args: _*) match {
        case EpRE(user,host,port) =>  // TODO : not safe
          SshEndPoint(
            host=host,
            username=Option(user).getOrElse(SshEndPoint.defaultUserName),
            port = Option(port).map(_.toInt).getOrElse(SshEndPoint.defaultPort)
          )
      }
    }
  }

  implicit def tupleToAccessPath(tuple:Tuple2[String, EndPoint]): AccessPath = {
    tuple match {case (name,from) => AccessPath(name,from::Nil)}
  }
  implicit class EndPointToAccessPath(from:Tuple2[String, EndPoint]) {
    def ^(to:EndPoint):AccessPath = from match {
      case (name, from) => AccessPath(name, from::to::Nil)
    }
  }

  implicit class AccessPathHelper(accessPath:AccessPath) {
    def ^(next:EndPoint):AccessPath= accessPath.copy(endpoints = accessPath.endpoints:+next)
  }


  class SessionHelper() {
    var accesses:List[AccessPath]=Nil
    def addAccess(access:AccessPath):Unit = accesses = access::accesses
  }

  def session(accesses: implicit SessionHelper => Unit)(operations: implicit SSHConnectionManager => Unit) = {
    implicit val sessionHelper = new SessionHelper
    accesses
    implicit val connectionManager = SSHConnectionManager(sessionHelper.accesses)
    operations
    connectionManager.close()
  }

  def server(access:AccessPath)(implicit sessionHelper: SessionHelper):Unit = {
    sessionHelper.addAccess(access)
  }

  def shell(name:String)(operations: implicit SSHShell => Unit)(implicit manager: SSHConnectionManager):Unit = {
    manager.shell(name) { sh =>
      implicit val shi = sh
      operations
    }
  }

  def ls(implicit sh:SSHShell) = {sh.ls()}
  def cd(dir:String)(implicit sh:SSHShell) = {sh.cd(dir)}
  def pwd(implicit sh:SSHShell) = {sh.pwd()}
  def mkdir(name:String)(implicit sh:SSHShell) = {sh.mkdir(name)}
  def exec(cmd:String)(implicit sh:SSHShell) = {sh.execute(cmd)}
  def whoami(implicit sh:SSHShell) = {sh.whoami}
  def hostname(implicit sh:SSHShell) = {sh.hostname}
  def uptime(implicit sh:SSHShell) = {sh.uptime}
}



object test {
  def main(args: Array[String]): Unit = {

    import SshDsl._

    session {
      server("srv1" -> ssh"dcr@127.0.0.1~22")
      server("srv2" -> proxy"127.0.0.1" ^ ssh"dcr@127.0.0.1" ^ ssh"test@127.0.0.1")
    } {
      shell("srv1") { println(s"Hello from $hostname $whoami $pwd") }
      shell("srv2") { println(s"Hello from $hostname $whoami $pwd") }
    }
    
  }
}
