

final class mainTemplate$_ {
def args = mainTemplate_sc.args$
def scriptPath = """mainTemplate.sc"""
/*<script>*/


/* Borger, feel free to let your imagination shine but do not change this snippet. */
val url: String = args.length match {
  case 0 => "https://api.swissborg.io/webiste/v1/challenge/rates"
  case _ => args(0)
}

/* Add your stuff, be Awesome! */

println(url)

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.util.regex.Pattern

object ArbitrageApp {

  // ---------- Data types ----------

  final case class Edge(u: Int, v: Int, w: Double)
  final case class ArbitrageResult(cycle: List[Int], profitFactor: Double)

  // JSON d'exemple de l'énoncé (fallback si l'API live ne marche pas)
  // val sampleJson: String =
  //   """{
  //     |    "BTC-BTC": "1.0000000000",
  //     |    "BTC-BORG": "116352.2654440156",
  //     |    "BTC-DAI": "23524.1391553039",
  //     |    "BTC-EUR": "23258.8865583847",
  //     |    "BORG-BTC": "0.0000086866",
  //     |    "BORG-BORG": "1.0000000000",
  //     |    "BORG-DAI": "0.2053990550",
  //     |    "BORG-EUR": "0.2017539914",
  //     |    "DAI-BTC": "0.0000429088",
  //     |    "DAI-BORG": "4.9320433378",
  //     |    "DAI-DAI": "1.0000000000",
  //     |    "DAI-EUR": "0.9907652193",
  //     |    "EUR-BTC": "0.0000435564",
  //     |    "EUR-BORG": "5.0427577751",
  //     |    "EUR-DAI": "1.0211378960",
  //     |    "EUR-EUR": "1.0000000000"
  //     |}""".stripMargin

  // ---------- Fetch ----------

  def fetchRatesJson(): String = {
    // Corrige le bug "webiste" -> "website" sans toucher au snippet
    val fixedUrl =
      if (url.contains("webiste")) url.replace("webiste", "website")
      else url

    try {
      val client  = HttpClient.newHttpClient()
      val request = HttpRequest.newBuilder()
        .uri(URI.create(fixedUrl))
        .GET()
        .build()

      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() / 100 != 2) {
        Console.err.println(s"[warn] HTTP ${response.statusCode()} from $fixedUrl, using fallback example JSON.")
        ""
      } else {
        response.body()
      }
    } catch {
      case e: Throwable =>
        Console.err.println(s"[warn] Error fetching $fixedUrl: ${e.getMessage}, using fallback example JSON.")
        ""
    }
  }

  // ---------- Parsing (regex, sans libs externes) ----------

  def parseRates(body: String): (Vector[String], Map[(String, String), Double]) = {
    // On cherche des lignes du type: "EUR-DAI": "1.0211378960"
    val pattern = Pattern.compile(
      """"([A-Z]{3})-([A-Z]{3})"\s*:\s*"([0-9.]+)""""
    )
    val matcher = pattern.matcher(body)

    val pairsBuf       = scala.collection.mutable.ArrayBuffer[((String, String), Double)]()
    val currenciesSet  = scala.collection.mutable.Set[String]()

    while (matcher.find()) {
      val from    = matcher.group(1)
      val to      = matcher.group(2)
      val rateStr = matcher.group(3)
      val rate    = rateStr.toDouble

      pairsBuf += ((from -> to) -> rate)
      currenciesSet += from
      currenciesSet += to
    }

    val currencies = currenciesSet.toVector.sorted
    val rates      = pairsBuf.toMap

    (currencies, rates)
  }

  // ---------- Graph building ----------

  def buildGraph(
    currencies: Vector[String],
    rates: Map[(String, String), Double]
  ): (Vector[Edge], Array[Array[Double]]) = {

    val index: Map[String, Int] = currencies.zipWithIndex.toMap
    val n = currencies.length

    val rateMatrix = Array.fill[Double](n, n)(Double.NaN)
    val edgesBuf   = scala.collection.mutable.ArrayBuffer[Edge]()

    for (((from, to), r) <- rates) {
      val i = index(from)
      val j = index(to)
      rateMatrix(i)(j) = r
      val w = -math.log(r) // transform pour arbitrage
      edgesBuf += Edge(i, j, w)
    }

    (edgesBuf.toVector, rateMatrix)
  }

  // ---------- Debug: print graph ----------

  def printGraph(
    currencies: Vector[String],
    edges: Vector[Edge],
    rateMatrix: Array[Array[Double]]
  ): Unit = {
    println()
    println("=== Edges (graph) ===")
    edges.foreach { e =>
      val from = currencies(e.u)
      val to   = currencies(e.v)
      val rate = rateMatrix(e.u)(e.v)
      println(f"$from%4s -> $to%4s  rate = $rate%14.10f  w = ${e.w}%10.6f")
    }

    println()
    println("=== Rate matrix ===")

    if (currencies.isEmpty) {
      println("(empty)")
    } else {
      print("     ")
      currencies.foreach(c => print(f"$c%12s"))
      println()

      for (i <- currencies.indices) {
        print(f"${currencies(i)}%4s ")
        for (j <- currencies.indices) {
          val r = rateMatrix(i)(j)
          if (java.lang.Double.isNaN(r)) print(f"${"-"}%12s")
          else print(f"$r%12.6f")
        }
        println()
      }
    }

    println()
  }

  // ---------- Bellman–Ford + negative cycle ----------

  def findNegativeCycle(
    nCurrencies: Int,
    edges: Vector[Edge]
  ): Option[List[Int]] = {

    val n           = nCurrencies
    val superSource = n
    val N           = n + 1

    val allEdges =
      edges ++ (0 until n).map(i => Edge(superSource, i, 0.0))

    val dist = Array.fill[Double](N)(Double.PositiveInfinity)
    val pred = Array.fill[Int](N)(-1)

    dist(superSource) = 0.0

    var x    = -1
    var iter = 0
    while (iter < N) {
      x = -1
      var k = 0
      while (k < allEdges.length) {
        val e = allEdges(k)
        if (dist(e.u) + e.w < dist(e.v) - 1e-12) {
          dist(e.v) = dist(e.u) + e.w
          pred(e.v) = e.u
          x = e.v
        }
        k += 1
      }
      iter += 1
    }

    if (x == -1) {
      None // pas de cycle négatif
    } else {
      // remonte dans les prédécesseurs pour "tomber" dans le cycle
      var y = x
      var i = 0
      while (i < N) {
        y = pred(y)
        i += 1
      }

      // reconstruction du cycle (sans do/while)
      val buff  = scala.collection.mutable.ListBuffer[Int]()
      var cur   = y
      var first = true
      while (first || cur != y) {
        first = false
        buff += cur
        cur = pred(cur)
      }

      val cycle     = buff.toList.reverse
      val realCycle = cycle.filter(_ != superSource)
      if (realCycle.isEmpty) None else Some(realCycle)
    }
  }

  // ---------- Profit ----------

  def computeProfitFactor(
    cycle: List[Int],
    rateMatrix: Array[Array[Double]]
  ): Double = {
    if (cycle.isEmpty) return 1.0
    var prod = 1.0
    val m = cycle.length
    var i = 0
    while (i < m) {
      val a = cycle(i)
      val b = cycle((i + 1) % m)
      prod *= rateMatrix(a)(b)
      i += 1
    }
    prod
  }

  def printArbitrageResult(
    res: ArbitrageResult,
    currencies: Vector[String],
    rateMatrix: Array[Array[Double]]
  ): Unit = {
    val names = res.cycle.map(currencies)

    println("Arbitrage loop found:")
    println(names.mkString("  ->  "))
    println(f"Total profit factor (per loop): ${res.profitFactor}%.8f")

    val startAmount = 100.0
    var amount      = startAmount
    val m           = res.cycle.length

    println()
    println(f"Simulating with $startAmount%.2f ${names.head}:")
    var i = 0
    while (i < m) {
      val aIdx = res.cycle(i)
      val bIdx = res.cycle((i + 1) % m)
      val from = currencies(aIdx)
      val to   = currencies(bIdx)
      val rate = rateMatrix(aIdx)(bIdx)
      val newAmount = amount * rate
      println(f"  $amount%.8f $from  →  $newAmount%.8f $to  (rate = $rate%.10f)")
      amount = newAmount
      i += 1
    }

    println()
    println(f"End of loop: $amount%.8f ${names.head}  (net P&L = ${amount - startAmount}%.4f)")
  }

  // ---------- Orchestration ----------

  def run(): Unit = {
    val body      = fetchRatesJson()
    val parsed    = parseRates(body)
    val (currencies, rates) =
      if (parsed._1.nonEmpty && parsed._2.nonEmpty) parsed
      else {
        println("[info] Falling back to embedded example JSON from the statement.")
        // parseRates(sampleJson)
      }

    val (edges, rateMatrix) = buildGraph(currencies, rates)

    println(s"Found ${currencies.size} currencies and ${edges.size} edges.")

    // >>> ICI : print du graphe juste après le build <<<
    printGraph(currencies, edges, rateMatrix)

    if (currencies.isEmpty || edges.isEmpty) {
      println("No data -> cannot search for arbitrage loops.")
    } else {
      findNegativeCycle(currencies.length, edges) match {
        case Some(cycle) =>
          val profit = computeProfitFactor(cycle, rateMatrix)
          val res    = ArbitrageResult(cycle, profit)
          printArbitrageResult(res, currencies, rateMatrix)
        case None =>
          println("No arbitrage loop detected.")
      }
    }
  }
}

// Lancement de l'algo (après le template)
ArbitrageApp.run()

/*</script>*/ /*<generated>*//*</generated>*/
}

object mainTemplate_sc {
  private var args$opt0 = Option.empty[Array[String]]
  def args$set(args: Array[String]): Unit = {
    args$opt0 = Some(args)
  }
  def args$opt: Option[Array[String]] = args$opt0
  def args$: Array[String] = args$opt.getOrElse {
    sys.error("No arguments passed to this script")
  }

  lazy val script = new mainTemplate$_

  def main(args: Array[String]): Unit = {
    args$set(args)
    val _ = script.hashCode() // hashCode to clear scalac warning about pure expression in statement position
  }
}

export mainTemplate_sc.script as `mainTemplate`

