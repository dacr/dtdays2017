# Implicits

![](http://www.laboiteverte.fr/wp-content/uploads/2011/09/03-2001-A-Space-Odyssey.gif)

[David Crosson](https://github.com/dacr) -
  [@crodav](https://twitter.com/crodav)
 
Orange/IMT/OLS/IVA/I2P/XPERF


---

## Introduction

WE ARE ALL ALREADY using implicits !

* Consider this java code :  
  `System.out.println("The response : "+42)`
  - **42** is implicitly converted to a String
  - **"Response : "** is implicitly converted to a a mutable string such as a StringBuffer which provides an append like method

---

## So what are implicits ?

* ![](http://www.laboiteverte.fr/wp-content/uploads/2011/09/01-Dr.-Strangelove-3.gif)
  + [cambridge dictionary](http://dictionary.cambridge.org/dictionary/english/implicit) :
    - "*suggested but not communicated directly*"
    - So "implicits" in programming languages can be understood as something happening but not explicitly written ! kind of magic...

---

## But why ?
![](https://media.giphy.com/media/KSBH6QSi7gYEM/giphy.gif)

* to write less code,
* to enhance readability,
* to be more DRY (Don't Repeat Youself),
* to enhance legacy APIs,
* to define **new domain specific languages**,...

---

## Quick test environment

* Install the [SBT](http://www.scala-sbt.org/) build tool
* `git clone https://github.com/dacr/dtdays2017.git`
* `cd dtdays2017`
* `sbt console` *(on first time execution, it will download all the required dependencies)*
	+ `println("Hello world")`
* Then just copy/paste following code snippets into the scala console to play with Implicits

---

## Ninja turtle example
```scala
case class Point(x:Double, y:Double)
case class Turtle(id:String, pos:Point, angle:Double)

def distance(a:Point, b:Point):Double = sqrt(pow(b.x-a.x, 2)+pow(b.y-a.y, 2))
def angle(a:Point, b:Point):Double = atan2(b.y-a.y,b.x-a.x)

distance(Point(1d,1d),Point(2d,2d))

val leonardo = Turtle("leonardo",Point(1d,1d),0)
val donatello = Turtle("donatello",Point(2d,2d),0)

distance(leonardo.pos,donatello.pos)
```

---

## Adding implicit conversions
```
implicit def tuple2point(in:(Double,Double)):Point = in match {case (a,b)=>Point(a,b)}
implicit def turtle2point(in:Turtle):Point = in.pos

val leonardo = Turtle("leonardo",(1d,1d),0)
val donatello = Turtle("donatello",(2d,2d),0)

distance((1d,1d),(2d,2d))
distance(leonardo,donatello)
```
It simplifies API usage

---

## Adding operations

```scala
implicit class TurtlePlayRules(from:Turtle) {
  def forward(d:Double):Turtle = from.copy(pos=(from.x+cos(from.angle)*d, from.y+sin(from.angle)*d))
  def backward(d:Double):Turtle = forward(-d)
  def turnLeft(n:Int):Turtle = from.copy(angle=from.angle+PI/4*n)
  def turnRight(n:Int):Turtle = from.copy(angle=from.angle-PI/4*n)
}

leonardo forward 5 turnLeft 3 backward 1 turnRight 1 forward 2
```
The last line is automatically rewritten such as :
```
TurtlePlayRules(TurtlePlayRules(TurtlePlayRules(leonardo).forward(5)).turnLeft(3)).backward(1)
```

---

## What have we done ?

* We've just created an internal Domain Specific Language (DSL) !
	+ without defining any new dedicated parsers
	+ just using the chosen programming language
	    - that's why it is called an "internal" DSL.
    + SO all language features are available for advanced users ;
	    - `(1 to 10).foldLeft(leonardo){case (turtle,d) => turtle forward d turnLeft 1}`

---

## Implicit values & parameters

It's wonderful solution when you have to deal with contexts, configs, sessions, ... to stay DRY !

```
case class GlobalConfig(baseuri:String, timeout:Int)

def homepage()(implicit cfg:GlobalConfig) = {
  s"<html><body>baseuri is ${cfg.baseuri}</body></html>"
}

def route(request:String)(implicit cfg:GlobalConfig) = {
  request match {
    case ""  => homepage()
    case "/" => homepage()
  }
}

implicit val defaults:GlobalConfig = GlobalConfig("/base", 32)
route("/")
```

---

## Implicit values & parameters
* Usage example :
  ```scala
  List("a10","a1", "a2").sorted
  // List("a1", "a10", "a2")

  import fr.janalyse.tools.NaturalSort._

  List("a10","a1", "a2").sorted
  // List("a1", "a2", "a10")
  ```
  + The [sorted method](http://www.scala-lang.org/api/2.12.x/scala/collection/immutable/List.html) looks for an implicit parameter :
    ```scala
    def sorted[B >: A](implicit ord: math.Ordering[B]): List[A]
    ```
  + if no available implicit ordering is available in the current scope, the compiler will look into the [math.Ordering](http://www.scala-lang.org/api/2.12.x/scala/math/Ordering$.html) object for the default one.

---

## Implicit values & parameters
* let's illustrate how resolution works :
  ```
  trait Truc { def msg:String} ;  object Truc {
    implicit object DefaultTruc extends Truc {
      def msg = "muche !"
    }
  }
  ```
  + Note here the small tip to force the trait and the object to be compiled together within the console
    - _(I could have used `:paste` but it is not yet available for dotty console)_
* And now you can proceed with :
  ```
  def toto()(implicit truc:Truc) = "hello "+truc.msg

  println(toto())

  implicit val truc:Truc = new Truc { val msg="mucheII" }
  println(toto())
  ```

---

## The builder pattern

```
def ssh(host:String, user:String, password:String)(session: implicit SSHShell=>Unit) = {
  implicit val sh:SSHShell = SSH(host, user, password).newShell
  session
  sh.close
}
def ls(implicit sh:SSHShell) = {sh.ls()}
def cd(dir:String)(implicit sh:SSHShell) = {sh.cd(dir)}
def pwd(implicit sh:SSHShell) = {sh.pwd()}
def mkdir(name:String)(implicit sh:SSHShell) = {sh.mkdir(name)}

ssh("localhost", "test", "testtest") {
   println(ls)
   mkdir("toto")
   cd("toto")
   println(pwd)
}
```
This construct is possible thanks to **implicit function types** (*new scala feature coming with [dotty](http://dotty.epfl.ch/)*)

---

## Enhancing logging experience

* Some of the goals :
	+ Find solutions to have the source code less polluted by logging instructions
	+ Enhance log content coherency
	+ How to use logging with Futures, Actors, Streams, ...
	+ Conciliate logging achieved by the heterogeneous frameworks

---

## Enhancing logging experience

* Let's check the goal "less pollution".
  + Let's start with the following code :
    ```
    case class Car(brand:String, name:String, color:String, owner:Option[String])
    implicit def str2stropt(in:String):Option[String] = in.trim match {
      case "" => None
      case x => Some(x)
    }
    val cars = List(
      Car("volkswagen", "passat", "red", "david"),
      Car("ford", "T", "black", "")
    )
    val ownedCars = for {
      car <- cars
      owner <- car.owner
    } yield { car }
    ```
* Then we want to log unowned cars.

---

## Enhancing logging experience

* Typical classic solution
  ```
  val ownedCars = for {
    car <- cars
    } yield {
      if (car.owner.isDefined) Some(car)
      else {
        println(s"unowned car $car")
        None
      }
  }
  ```
  + **Awful** ! And worst, the result is now a `List[Option[Car]]` and not a `List[Car]`, we will have to `flatten` the result.  
  + **Here we have coded some logic just for logging purposes** !

---

## Enhancing logging experience

* A better approach would be to write something such as :
  ```
  val ownedCars = for {
    car <- cars
    owner <- car.owner     logIfUndefined  s"unowned car $car"
  } yield { car }
  ```
  + possible thanks to this implicit class :
    ```
    implicit class OptionToLoggable[T](on:Option[T]) {
      def logIfUndefined(msg: => String):Option[T] = {
    	  if (on.isEmpty) println(msg)
    	  on
      }
    }
    ```
  + The implementation is kept clean and preserved, and the log operation can be easily deactivated using a comment.

---

## Enhancing logging experience

* What happens behind the scene :
  + the following code :
    ```
    val ownedCars = for {
      car <- cars
      owner <- car.owner     logIfUndefined  s"unowned car $car"
    } yield { car }
  ```
  + is implicitly rewritten as follow :
    ```
    val ownedCars = for {
      car <- cars
      owner <- OptionToLoggable(car.owner).logIfUndefined(s"unowned car $car")
    } yield { car }
    ```

---

## References

* **Martin Odersky** :
	+ [What to leave implicits talk](https://www.youtube.com/watch?v=Oij5V7LQJsA)
	+ [what's different with dotty ? ](https://www.youtube.com/watch?v=9lWrt6H6UdE&list=PLLMLOC3WM2r5Ei2mnSHCD-ZD04AXovttL)
		- implicits is at the center of the scala language
		- implicits is the canonical way to represent contexts
* **Li Haoyi** : [Implicit Design Patterns](http://www.lihaoyi.com/post/ImplicitDesignPatternsinScala.html)
* **Aaron Levin** : [Mastering Typeclass Induction](https://www.youtube.com/watch?v=CstiIq4imWM)
* [Implicit function types](http://dotty.epfl.ch/docs/reference/implicit-function-types.html) with the "table/row/cell" build pattern example
* **Olivier Croisier** : Devoxx FR - [Log me tender](https://www.youtube.com/watch?v=x73Bq9uTsZo)

---

## Conclusion

* Implicits are THE FEATURE
  + but understand well what you're doing !
* **Any Questions** ?
![](https://media.giphy.com/media/MCZ39lz83o5lC/giphy.gif)
