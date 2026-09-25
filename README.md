# Rate Limiter — LLD

A thread-safe, extensible Rate Limiter implementation supporting multiple rate-limiting algorithms.

Currently implemented:

* Sliding Window
* Token Bucket
* Per-client rate limiting
* Thread-safe state management

---

## 1. Problem Statement

Design a Rate Limiter that controls how many requests a client can make within a given period of time.

For example:

> Allow a client to make at most 5 requests in 10 seconds.

If the client exceeds the configured limit, the request should be rejected.

The design should:

* Support multiple rate-limiting algorithms.
* Maintain independent limits for different clients.
* Be thread-safe.
* Allow new rate-limiting algorithms to be added easily.

---

# 2. High-Level Design

The common abstraction is:

```text
                    RateLimiter
                         |
             +-----------+-----------+
             |                       |
       SlidingWindow            TokenBucket
             |                       |
             |                       |
      Client-specific          Client-specific
           State                    State
```

`RateLimiter` defines the common operation:

```java
boolean isRequestAllowed(Request request);
```

Each algorithm has its own implementation and maintains separate state for every client.

---

# 3. Class Structure

```text
Request
   |
   | clientId
   |
   v
RateLimiter
   |
   +-------------------------+
   |                         |
   v                         v
SlidingWindowImpl       TokenBucketImpl
   |                         |
   v                         v
SlidingWindow             TokenBucket
RateLimitingState         RateLimitingState
```

### Main classes

### `Request`

Represents an incoming request.

```java
public record Request(int id, int client) {
}
```

For this implementation, rate limiting is performed per client.

---

### `RateLimiter`

Common abstraction for all rate-limiting strategies.

```java
public interface RateLimiter {

    boolean isRequestAllowed(Request request);

}
```

This follows the **Strategy Pattern** idea.

New algorithms can be added without modifying existing implementations.

For example:

```text
RateLimiter
   |
   +-- SlidingWindowImpl
   +-- TokenBucketImpl
   +-- FixedWindowImpl       // future
   +-- LeakyBucketImpl       // future
```

---

# 4. Per-Client Rate Limiting

Each client gets an independent state.

```text
Client 1 ---> State 1
Client 2 ---> State 2
Client 3 ---> State 3
```

The implementations maintain:

```java
Map<Integer, RateLimitingState>
```

using:

```java
ConcurrentHashMap
```

Therefore, one client's requests do not consume another client's quota.

For example:

```text
Limit = 5 requests / 10 seconds

Client A -> 5 requests accepted
Client B -> 5 requests accepted
```

Client A reaching its limit does not affect Client B.

---

# 5. Sliding Window

## Idea

Maintain timestamps of accepted requests in a queue.

For:

```text
Limit = 3
Window = 10 seconds
```

suppose we have:

```text
t = 1
t = 3
t = 7
```

At `t = 8`:

```text
Requests in window = 3
```

Therefore:

```text
new request -> REJECT
```

At `t = 12`:

```text
request at t=1 has expired
```

The queue becomes:

```text
3 -> 7
```

Therefore a new request can be accepted.

---

## Algorithm

For every request:

```text
1. Get the state for the client.
2. Acquire the state lock.
3. Remove expired timestamps.
4. Check queue size against the limit.
5. If space is available:
       Add current timestamp.
       Accept request.
6. Otherwise:
       Reject request.
```

Conceptually:

```java
synchronized (state) {

    evictExpiredRequests();

    if (queue.size() < limit) {
        queue.add(currentTime);
        return true;
    }

    return false;
}
```

---

## Why a Queue?

Requests are processed chronologically.

Therefore the oldest request is always at the front.

When removing expired requests:

```text
oldest
  |
  v
[1] [3] [7] [9]
 ^
 |
peek()
```

We only need to inspect the front of the queue.

---

## Complexity

Let `N` be the number of requests currently inside the window.

### Time

Each request is added once and removed once.

Amortized:

```text
O(1)
```

### Space

At most `limit` timestamps are maintained per client:

```text
O(limit)
```

---

# 6. Token Bucket

## Idea

A bucket contains tokens.

Each accepted request consumes one token.

Tokens are periodically refilled based on elapsed time.

Example:

```text
Capacity = 5
Refill Rate = 2 tokens / second
```

Initially:

```text
[● ● ● ● ●]
  5 tokens
```

A request consumes one:

```text
[● ● ● ●]
  4 tokens
```

After enough time passes, tokens are added again.

The bucket can never contain more than its capacity.

```text
tokens = min(capacity, tokens + newlyGeneratedTokens)
```

---

# 7. Lazy Token Refill

The implementation does **not** require a background thread.

Instead, tokens are refilled when a request arrives.

State contains:

```text
tokens
lastRefillTime
```

When a request arrives:

```text
1. Calculate elapsed time.
2. Calculate how many tokens should have been generated.
3. Add tokens.
4. Cap tokens at bucket capacity.
5. Check whether a token is available.
6. Consume one token if available.
```

Conceptually:

```text
elapsedTime = currentTime - lastRefillTime

newTokens = elapsedTime * refillRate

tokens = min(capacity, tokens + newTokens)

if tokens > 0:
    tokens--
    ACCEPT
else:
    REJECT
```

---

# 8. Why Lazy Refill?

An alternative would be to create a background thread that continuously adds tokens.

That approach has additional complexity:

```text
background thread
       |
       v
periodically refill bucket
```

The lazy approach avoids this.

No thread is required.

The bucket state is updated only when a request arrives.

Benefits:

* No background thread.
* No thread lifecycle management.
* Less resource usage.
* Easier synchronization.
* Easier to reason about in an LLD interview.

---

# 9. Token Bucket Concurrency

The following operations must be treated as **one atomic state transition**:

```text
Refill
  ↓
Check token availability
  ↓
Consume token
```

Otherwise two threads could observe the same available token.

Therefore the state is synchronized around the complete operation.

Conceptually:

```java
synchronized (state) {

    refill();

    if (tokens > 0) {
        tokens--;
        return true;
    }

    return false;
}
```

The important principle is:

> Synchronize the complete state transition, not individual operations.

---

# 10. Why `ConcurrentHashMap` + `synchronized`?

There are two different concurrency problems.

### Problem 1 — Access to client states

Multiple threads may access:

```java
Map<ClientId, State>
```

Therefore:

```java
ConcurrentHashMap
```

is used.

### Problem 2 — Updating one client's state

Operations such as:

```text
check + update
```

must be atomic.

Therefore each client's state is synchronized independently.

This gives us:

```text
                 ConcurrentHashMap
                       |
        +--------------+--------------+
        |              |              |
      Client A       Client B       Client C
        |              |              |
      lock           lock           lock
```

This is better than synchronizing the entire RateLimiter because requests belonging to different clients can proceed concurrently.

---

# 11. Why Synchronize Per Client?

Suppose we have:

```text
Client A
Client B
Client C
```

If we synchronize the entire RateLimiter:

```java
synchronized(rateLimiter) {
    ...
}
```

then:

```text
Client A -> waiting
Client B -> waiting
Client C -> waiting
```

even though their state is independent.

Instead, synchronize each client's state:

```text
Client A -> State A -> Lock A
Client B -> State B -> Lock B
Client C -> State C -> Lock C
```

Now:

```text
Thread 1 -> Client A -> Lock A
Thread 2 -> Client B -> Lock B
```

can execute concurrently.

This provides better concurrency.

---

# 12. Strategy Pattern

The `RateLimiter` interface allows algorithms to be interchangeable.

```java
RateLimiter rateLimiter =
        new SlidingWindowImpl(...);
```

or:

```java
RateLimiter rateLimiter =
        new TokenBucketImpl(...);
```

The caller only knows:

```java
rateLimiter.isRequestAllowed(request);
```

It doesn't need to know how the algorithm works internally.

This follows the **Strategy Pattern**.

---

# 13. Sliding Window vs Token Bucket

| Feature          | Sliding Window        | Token Bucket                 |
| ---------------- | --------------------- | ---------------------------- |
| State            | Request timestamps    | Token count + refill time    |
| Burst handling   | More restrictive      | Allows bursts up to capacity |
| Memory           | O(limit) per client   | O(1) per client              |
| Refill mechanism | No refill required    | Lazy refill                  |
| Main operation   | Add/remove timestamps | Refill/consume tokens        |
| Good for         | Strict request count  | Controlled bursts            |

---

# 14. Example

Suppose:

```text
Client = 1
Limit = 5
Window = 10 seconds
```

### Sliding Window

```text
t=0   -> ACCEPT
t=1   -> ACCEPT
t=2   -> ACCEPT
t=3   -> ACCEPT
t=4   -> ACCEPT
t=5   -> REJECT
```

At `t=11`:

```text
request at t=0 expires
```

Therefore:

```text
t=11 -> ACCEPT
```

---

### Token Bucket

Suppose:

```text
Capacity = 5
Refill = 1 token/sec
```

Initially:

```text
5 tokens
```

Five requests can be accepted immediately:

```text
5 -> 4 -> 3 -> 2 -> 1 -> 0
```

The sixth request is rejected.

After one second:

```text
1 token
```

is available again.

---

# 15. Edge Cases

Important cases to consider:

### Sliding Window

* Client reaches exactly the limit.
* Client exceeds the limit.
* Old requests expire.
* Multiple clients.
* Multiple concurrent requests.
* Request exactly at the window boundary.

### Token Bucket

* Bucket initially empty.
* Bucket initially full.
* Consume all tokens.
* Request when no tokens are available.
* Partial refill.
* Full refill.
* Bucket must never exceed capacity.
* Multiple clients.
* Concurrent requests.

---

# 16. Thread Safety Requirement

The most important concurrency rule in this implementation is:

### Sliding Window

```text
evict
  +
check
  +
add
```

must be atomic.

### Token Bucket

```text
refill
  +
check
  +
consume
```

must be atomic.

Making only individual methods thread-safe is not sufficient.

The **entire logical state transition** must be protected.

---

# 17. Complexity

## Sliding Window

Per client:

```text
Time:  O(1) amortized
Space: O(limit)
```

## Token Bucket

Per client:

```text
Time:  O(1)
Space: O(1)
```

The global client-state map requires:

```text
O(number of active clients)
```

space.

---

# 18. Important Interview Questions

### Q1. Why use an interface?

To allow multiple rate-limiting algorithms without changing the caller.

---

### Q2. Why maintain state per client?

Because rate limits are generally applied independently to clients.

---

### Q3. Why `ConcurrentHashMap`?

Multiple threads can access client states concurrently.

---

### Q4. Why synchronize the state?

Because operations such as:

```text
check + update
```

must be atomic.

---

### Q5. Why not synchronize the entire RateLimiter?

That would unnecessarily serialize requests from different clients.

---

### Q6. Why doesn't Token Bucket need a background thread?

Because tokens can be calculated lazily from elapsed time whenever a request arrives.

---

### Q7. What happens if two threads request simultaneously?

The client-specific state lock ensures that only one thread performs the state transition at a time.

---

### Q8. Which algorithm supports bursts better?

Token Bucket allows a client to consume accumulated tokens up to the bucket capacity.

---

### Q9. How would you add Fixed Window?

Create:

```java
class FixedWindowImpl implements RateLimiter
```

with client-specific window state.

No changes are required to the existing `RateLimiter` interface.

---

# 19. Future Improvements

For a real distributed system, the current implementation is local to a JVM.

If the application runs on multiple instances:

```text
             Load Balancer
             /     |     \
            /      |      \
         App 1   App 2   App 3
```

each application instance would maintain separate rate-limit state.

Therefore the global limit would not be accurate.

A distributed implementation could use:

```text
Redis
```

as shared state.

For example:

```text
Application
     |
     v
RateLimiter
     |
     v
Redis
     |
     +-- Client A state
     +-- Client B state
     +-- Client C state
```

Redis operations would need to be atomic, commonly using:

* Lua scripts
* Atomic Redis commands
* Redis sorted sets for sliding-window timestamps
* Redis counters for fixed-window approaches

---

# 20. Possible Future Extensions

The current design can be extended to support:

```text
Fixed Window
Sliding Window
Token Bucket
Leaky Bucket
```

It can also support different rate-limit keys:

```text
Client ID
User ID
API Key
IP Address
Tenant ID
Endpoint
```

A future `RateLimitKeyResolver` could abstract how the key is obtained.

Example:

```java
interface RateLimitKeyResolver {

    String resolve(Request request);

}
```

Then the algorithm doesn't need to know whether the limit is based on:

```text
user
client
IP
API key
```

---

# 21. Key Takeaways for LLD Interview

Remember these points:

```text
1. RateLimiter is the common abstraction.

2. Each algorithm implements RateLimiter.

3. Maintain independent state per client.

4. ConcurrentHashMap handles concurrent access to client states.

5. Synchronize individual client state rather than the entire limiter.

6. Sliding Window:
   Queue of timestamps
   -> evict expired
   -> check limit
   -> add timestamp

7. Token Bucket:
   tokens + lastRefillTime
   -> calculate elapsed time
   -> refill lazily
   -> check token
   -> consume token

8. The complete state transition must be atomic.

9. Token Bucket uses O(1) state per client.

10. Sliding Window uses O(limit) state per client.

11. For distributed systems, move state to a shared store such as Redis.
```

---

# 22. Design Summary

The core design is intentionally simple:

```text
                    +----------------+
                    |  RateLimiter   |
                    +----------------+
                            |
             +--------------+--------------+
             |                             |
             v                             v
    +------------------+          +------------------+
    | Sliding Window   |          |  Token Bucket    |
    +------------------+          +------------------+
             |                             |
             v                             v
    +------------------+          +------------------+
    | Map<Client,      |          | Map<Client,      |
    | SlidingState>    |          | TokenState>      |
    +------------------+          +------------------+
             |                             |
             v                             v
       +-----------+                +-------------+
       |  Queue    |                | Tokens      |
       | timestamps|                | Refill time |
       +-----------+                +-------------+
```

The most important design principle is:

> **The algorithm implementation owns the client-specific state, and the state transition is atomic.**

This keeps the design simple, extensible, and suitable for an LLD interview.
