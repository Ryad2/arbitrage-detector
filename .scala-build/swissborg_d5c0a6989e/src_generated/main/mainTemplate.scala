

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

