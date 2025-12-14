

final class main$_ {
def args = main_sc.args$
def scriptPath = """main.sc"""
/*<script>*/
//> using scala "3.3.1"
//> using dep "com.lihaoyi::ujson:4.4.1"

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI

final case class Edge(u: Int, v: Int, w: Double)
final case class ArbitrageResult(cycleIndices: List[Int], profitFactor: Double)

// ---------------- HTTP + JSON ----------------
// // // //
def fetchRatesJson(): String = {
  val client  = HttpClient.newHttpClient()
  val request = HttpRequest.newBuilder()
    .uri(URI("https://api.swissborg.io/website/v1/challenge/rates"))
    .GET()
    .build()

  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  if (response.statusCode() != 200) {
    sys.error(s"HTTP error: ${response.statusCode()}")
  }
  response.body()
}

// def fetchRatesJson(): String =
//   """
//   {
//     "BTC-BTC": "1.0000000000",
//     "BTC-BORG": "116352.2654440156",
//     "BTC-DAI": "23524.1391553039",
//     "BTC-EUR": "23258.8865583847",
//     "BORG-BTC": "0.0000086866",
//     "BORG-BORG": "1.0000000000",
//     "BORG-DAI": "0.2053990550",
//     "BORG-EUR": "0.2017539914",
//     "DAI-BTC": "0.0000429088",
//     "DAI-BORG": "4.9320433378",
//     "DAI-DAI": "1.0000000000",
//     "DAI-EUR": "0.9907652193",
//     "EUR-BTC": "0.0000435564",
//     "EUR-BORG": "5.0427577751",
//     "EUR-DAI": "1.0211378960",
//     "EUR-EUR": "1.0000000000"
//   }
//   """

def parseRates(body: String): (Vector[String], Map[(String, String), Double]) = {
  val json = ujson.read(body).obj

  // Keep only entries that look like "FROM-TO"
  val rateEntries: Vector[(String, ujson.Value)] =
    json.iterator.collect { case (k, v) if k.contains("-") => (k, v) }.toVector

  // All distinct currency codes, derived only from rateEntries
  val currencySet: Set[String] =
    rateEntries.iterator.flatMap { case (pair, _) =>
      pair.split("-").toList
    }.toSet

  val currencies = currencySet.toVector.sorted

  // Map (FROM, TO) -> rate
  val rates: Map[(String, String), Double] =
    rateEntries.iterator.flatMap { case (pair, value) =>
      pair.split("-") match {
        case Array(from, to) =>
          Some((from -> to) -> value.str.toDouble)
        case _ =>
          None // ignore weird keys
      }
    }.toMap

  (currencies, rates)
}

// ---------------- Graph building ----------------

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
    val w = -math.log(r)  // classic arbitrage transform
    edgesBuf += Edge(i, j, w)
  }

  (edgesBuf.toVector, rateMatrix)
}

def printGraph(
  currencies: Vector[String],
  edges: Vector[Edge],
  rateMatrix: Array[Array[Double]]
): Unit = {

  println("=== Edges (graph) ===")
  edges.foreach { e =>
    val from = currencies(e.u)
    val to   = currencies(e.v)
    val rate = rateMatrix(e.u)(e.v)
    println(f"$from%5s -> $to%5s  rate = $rate%14.10f  w = ${e.w}%.6f")
  }

  println()
  println("=== Rate matrix ===")

  // header
  print("       ")
  currencies.foreach(c => print(f"$c%12s"))
  println()

  for (i <- currencies.indices) {
    print(f"${currencies(i)}%5s ")
    for (j <- currencies.indices) {
      val r = rateMatrix(i)(j)
      if (r.isNaN) print(f"${"---"}%12s")
      else print(f"$r%12.6f")
    }
    println()
  }
  println()
}

// ---------------- Bellman–Ford + negative cycle ----------------

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

  var x = -1
  var iter = 0
  while (iter < N) {
    x = -1
    var k = 0
    while (k < allEdges.length) {
      val e = allEdges(k)
      if (dist(e.u) + e.w < dist(e.v) - 1e-15) {
        dist(e.v) = dist(e.u) + e.w
        pred(e.v) = e.u
        x = e.v
      }
      k += 1
    }
    iter += 1
  }

  if (x == -1) {
    None  // no negative cycle
  } else {
    // x is on or reachable from a negative cycle
    var y = x
    var i = 0
    while (i < N) {
      y = pred(y)
      i += 1
    }

    // Reconstruct cycle: while-first pattern (no do/while)
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
    Some(realCycle)
  }
}

// ---------------- Arbitrage evaluation ----------------

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

def findBestArbitrage(
  currencies: Vector[String],
  edges: Vector[Edge],
  rateMatrix: Array[Array[Double]]
): Option[ArbitrageResult] = {
  findNegativeCycle(currencies.length, edges).map { cycle =>
    val profit = computeProfitFactor(cycle, rateMatrix)
    ArbitrageResult(cycle, profit)
  }
}

def printResult(
  res: ArbitrageResult,
  currencies: Vector[String],
  rateMatrix: Array[Array[Double]]
): Unit = {

  val names = res.cycleIndices.map(currencies)

  println("Arbitrage loop found:")
  println(names.mkString("  ->  "))

  val profitFactor = res.profitFactor
  println(f"Total profit factor (per loop): $profitFactor%.8f")

  val startAmount = 100.0
  var amount      = startAmount
  val m           = res.cycleIndices.length

  println()
  println(f"Simulating with $startAmount%.2f ${names.head}:")
  var i = 0
  while (i < m) {
    val aIdx = res.cycleIndices(i)
    val bIdx = res.cycleIndices((i + 1) % m)
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

// ---------------- "Main" for the script ----------------

def run(): Unit = {
  val jsonBody = fetchRatesJson()
  val (currencies, rates) = parseRates(jsonBody)
  val (edges, rateMatrix) = buildGraph(currencies, rates)

  printGraph(currencies, edges, rateMatrix)

  findBestArbitrage(currencies, edges, rateMatrix) match {
    case Some(res) =>
      printResult(res, currencies, rateMatrix)
    case None =>
      println("No arbitrage loop detected.")
  }
}

// Top-level call
run()


/*</script>*/ /*<generated>*//*</generated>*/
}

object main_sc {
  private var args$opt0 = Option.empty[Array[String]]
  def args$set(args: Array[String]): Unit = {
    args$opt0 = Some(args)
  }
  def args$opt: Option[Array[String]] = args$opt0
  def args$: Array[String] = args$opt.getOrElse {
    sys.error("No arguments passed to this script")
  }

  lazy val script = new main$_

  def main(args: Array[String]): Unit = {
    args$set(args)
    val _ = script.hashCode() // hashCode to clear scalac warning about pure expression in statement position
  }
}

export main_sc.script as `main`

