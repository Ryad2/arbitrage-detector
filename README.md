# Arbitrage Puzzle – Solution Overview

This project implements an arbitrage detector on the SwissBorg rate API using a graph-based approach and classic negative-cycle detection. It finds a profitable currency loop (if any) and simulates the resulting profit for a given starting amount.

---

## Algorithm and Complexity Analysis

### Model

Let:

- `C = {c0, …, c(n-1)}` be the set of currencies  
- For each ordered pair `(ci, cj)` we have a quoted rate `rij`, meaning:  
  *1 unit of `ci` buys `rij` units of `cj`.*

We build a **weighted directed graph**:

- **Vertices**: one per currency  
- **Edges**: for every quoted pair `ci -> cj` with rate `rij`  
- **Edge weight**:  
  `wij = -log(rij)`

An arbitrage loop is a cycle:

`ci0 -> ci1 -> … -> cik -> ci0`

such that:

`∏(ℓ = 0..k) r(iℓ, iℓ+1) > 1`

Taking logs:

`log( ∏(ℓ = 0..k) r(iℓ, iℓ+1) ) = ∑(ℓ = 0..k) log r(iℓ, iℓ+1) > 0`

With `wij = -log(rij)`, this is equivalent to:

`∑(ℓ = 0..k) w(iℓ, iℓ+1) < 0`

So:

> **An arbitrage loop exists if and only if the graph has a negative cycle.**

This is exactly what Bellman–Ford can detect.

### Algorithm

1. **Fetch and parse rates**
   - Download the JSON from the SwissBorg URL (using the provided template URL, with a minor fix for the `webiste` typo).
   - Extract all entries of the form `"XXX-YYY": "rate"` using a simple parser/regex.
   - Build:
     - the set of currencies,  
     - a map `(from, to) -> rate`.

2. **Build graph**
   - Index currencies as `0 .. n-1`.
   - Create:
     - `rateMatrix(i)(j) = rij` for later profit simulation,
     - a list of edges `Edge(u, v, w)` with `w = -log(rate)`.

3. **Add a super-source**
   - Add a virtual source vertex `s = n`.
   - Add edges `s -> i` of weight `0` for all currencies `i`.
   - This allows Bellman–Ford to reach every node, regardless of where the arbitrage cycle starts.

4. **Bellman–Ford + negative cycle detection**
   - Initialize:
     ```scala
     dist(s) = 0
     dist(other nodes) = +∞
     pred(·) = -1
     ```
   - Relax all edges `N = n + 1` times:
     ```scala
     if (dist(u) + w < dist(v) - 1e-12) {
       dist(v) = dist(u) + w
       pred(v) = u
       x = v
     }
     ```
   - The `1e-12` epsilon is used to avoid treating pure floating-point noise as a real improvement.
   - If on the `N`-th pass at least one distance is still improved (`x != -1`), a **negative cycle** exists.

5. **Cycle reconstruction**
   - From the last updated vertex `x`, walk predecessors `N` times to ensure you are inside the cycle:
     ```scala
     var y = x
     for (_ <- 0 until N) y = pred(y)
     ```
   - Then follow `pred` starting from `y` until you come back to `y` to reconstruct the cycle.
   - Filter out the super-source node to keep only real currencies.

6. **Profit computation and output**
   - Given the cycle (list of currency indices), compute:
     ```text
     profitFactor = ∏ over edges in cycle of rij
     ```
   - Simulate starting with 100 units of the first currency:
     ```scala
     amount = 100
     amount = amount * rateMatrix(a)(b) // for each edge in the cycle
     ```
   - Print:
     - the arbitrage loop (e.g. `EUR -> DAI -> BTC -> EUR`),
     - the profit factor,
     - the final amount and net P&L.

### Complexity

Let `n` be the number of currencies and `m` the number of edges.

In this problem the graph is essentially **dense** (there is a rate for almost every ordered pair), so:

- `m ≈ n²`

Costs:

1. **Parsing + graph construction**  
   - One pass over all pairs: `O(m) = O(n²)`

2. **Bellman–Ford with super-source**  
   - Vertices: `V = n + 1`  
   - Edges: `E = m + n ≈ n²`  
   - Complexity:
     `O(V * E) ≈ O(n * n²) = O(n³)`

3. **Cycle reconstruction + profit computation**  
   - Cycle length ≤ `n`: `O(n)`

Overall:

- **Time:** `O(n³)` on a dense graph  
- **Space:** `O(n²)` for the rate matrix and edge list

This is the standard optimal complexity class for detecting negative cycles in a general weighted graph and scales well for realistic numbers of currencies.

---

## Note About BORG and Its Key Features

**BORG** is SwissBorg’s native token (rebranded from CHSB in 2023) and is central to the SwissBorg ecosystem.

### Role and Utility

- **Utility + governance token** for the SwissBorg wealth-management platform.
- Holding and locking BORG in the SwissBorg app unlocks:
  - **Premium memberships / Loyalty Ranks**
  - Reduced trading fees
  - Higher yield on SwissBorg’s Earn products
  - Access to exclusive investment opportunities (e.g. launchpad / “BorgPad” deals)

In practice, BORG acts as the “membership and alignment” token: the more you hold and lock, the better the conditions you get on the platform.

### Fee Benefits and Rewards

- Users can receive **up to ~90% cashback in BORG** on trading fees, depending on their Loyalty Rank.
- SwissBorg uses a portion of platform fees and profits to **buy back BORG** on the market:
  - Some of these tokens are distributed as rewards (cashback, loyalty, governance),
  - Some can be **burned** (destroyed) based on governance votes.

This design links platform usage (fees) to **continuous buy pressure** on BORG.

### Staking and Governance

- Users can **stake/lock BORG** in the app to obtain **voting power** in SwissBorg’s governance (“Guardians” system).
- Voting power is roughly proportional to the amount of BORG locked (for example, 10 BORG = 1 vote).
- Active voters in governance rounds receive additional BORG rewards from a dedicated pool funded by buybacks.
- This ties:
  - **Platform benefits** (lower fees, better yields),
  - **Governance influence**,
  - **Rewards for participation**  
  to long-term BORG holding.

### Tokenomics and Technical Aspects

- **Fixed supply:**  
  Total supply is capped at **1 billion BORG** (minus tokens already burned). The CHSB→BORG migration was 1:1 and did not introduce inflation.
- **Deflationary mechanisms:**
  - Regular **buyback-and-burn** programs reduce circulating supply over time.
  - Significant amounts of BORG are **locked** in loyalty tiers, governance staking and launchpad programs, further limiting free float.
- **Blockchain:**
  - BORG is a standard **ERC-20 token on Ethereum**, with an audited smart contract (OpenZeppelin-based).
  - A bridged version exists on other networks (e.g. Solana) to benefit from cheaper and faster transactions.

### Relevance for the Arbitrage Puzzle

In the context of this technical test, BORG is treated exactly like any other currency (BTC, DAI, EUR, etc.):

- It is a node in the currency graph.
- Edges involving BORG (e.g. `BTC-BORG`, `BORG-EUR`) are included with their corresponding rates.
- The algorithm does not give it special treatment; an optimal arbitrage loop may or may not pass through BORG depending solely on the current exchange rates.

BORG’s “real-world” features (loyalty, governance, buybacks, burns) are what make it strategically important within SwissBorg’s ecosystem, but algorithmically it is one more currency in the arbitrage graph.
